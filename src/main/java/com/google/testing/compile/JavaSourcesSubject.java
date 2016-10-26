/*
 * Copyright (C) 2013 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.testing.compile;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.compilations;
import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.testing.compile.CompilationSubject.DiagnosticAtColumn;
import com.google.testing.compile.CompilationSubject.DiagnosticInFile;
import com.google.testing.compile.CompilationSubject.DiagnosticOnLine;
import com.google.testing.compile.Parser.ParseResult;
import com.sun.source.tree.CompilationUnitTree;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

/**
 * A <a href="https://github.com/truth0/truth">Truth</a> {@link Subject} that evaluates the result
 * of a {@code javac} compilation.  See {@link com.google.testing.compile} for usage examples
 *
 * @author Gregory Kick
 */
@SuppressWarnings("restriction") // Sun APIs usage intended
public final class JavaSourcesSubject
    extends Subject<JavaSourcesSubject, Iterable<? extends JavaFileObject>>
    implements CompileTester, ProcessedCompileTesterFactory {
  private final List<String> options = new ArrayList<String>(Arrays.asList("-Xlint"));

  JavaSourcesSubject(FailureStrategy failureStrategy, Iterable<? extends JavaFileObject> subject) {
    super(failureStrategy, subject);
  }

  @Override
  public JavaSourcesSubject withCompilerOptions(Iterable<String> options) {
    Iterables.addAll(this.options, options);
    return this;
  }

  @Override
  public JavaSourcesSubject withCompilerOptions(String... options) {
    this.options.addAll(Arrays.asList(options));
    return this;
  }

  @Override
  public CompileTester processedWith(Processor first, Processor... rest) {
    return processedWith(Lists.asList(first, rest));
  }

  @Override
  public CompileTester processedWith(Iterable<? extends Processor> processors) {
    return new CompilationClause(processors);
  }

  @Override
  public void parsesAs(JavaFileObject first, JavaFileObject... rest) {
    new CompilationClause().parsesAs(first, rest);
  }

  @CanIgnoreReturnValue
  @Override
  public SuccessfulCompilationClause compilesWithoutError() {
    return new CompilationClause().compilesWithoutError();
  }

  @CanIgnoreReturnValue
  @Override
  public CleanCompilationClause compilesWithoutWarnings() {
    return new CompilationClause().compilesWithoutWarnings();
  }

  @CanIgnoreReturnValue
  @Override
  public UnsuccessfulCompilationClause failsToCompile() {
    return new CompilationClause().failsToCompile();
  }

  /** The clause in the fluent API for testing compilations. */
  private final class CompilationClause implements CompileTester {
    private final ImmutableSet<Processor> processors;

    private CompilationClause() {
      this(ImmutableSet.<Processor>of());
    }

    private CompilationClause(Iterable<? extends Processor> processors) {
      this.processors = ImmutableSet.copyOf(processors);
    }

    @Override
    public void parsesAs(JavaFileObject first, JavaFileObject... rest) {
      ParseResult actualResult = Parser.parse(actual());
      ImmutableList<Diagnostic<? extends JavaFileObject>> errors =
          actualResult.diagnosticsByKind().get(Kind.ERROR);
      if (!errors.isEmpty()) {
        StringBuilder message = new StringBuilder("Parsing produced the following errors:\n");
        for (Diagnostic<? extends JavaFileObject> error : errors) {
          message.append('\n');
          message.append(error);
        }
        failureStrategy.fail(message.toString());
      }
      final ParseResult expectedResult = Parser.parse(Lists.asList(first, rest));
      final FluentIterable<? extends CompilationUnitTree> actualTrees = FluentIterable.from(
          actualResult.compilationUnits());
      final FluentIterable<? extends CompilationUnitTree> expectedTrees = FluentIterable.from(
          expectedResult.compilationUnits());

      Function<? super CompilationUnitTree, ImmutableSet<String>> getTypesFunction =
          new Function<CompilationUnitTree, ImmutableSet<String>>() {
        @Override public ImmutableSet<String> apply(CompilationUnitTree compilationUnit) {
          return TypeEnumerator.getTopLevelTypes(compilationUnit);
        }
      };

      final ImmutableMap<? extends CompilationUnitTree, ImmutableSet<String>> expectedTreeTypes =
          Maps.toMap(expectedTrees, getTypesFunction);
      final ImmutableMap<? extends CompilationUnitTree, ImmutableSet<String>> actualTreeTypes =
          Maps.toMap(actualTrees, getTypesFunction);
      final ImmutableMap<? extends CompilationUnitTree, Optional<? extends CompilationUnitTree>>
      matchedTrees = Maps.toMap(expectedTrees,
          new Function<CompilationUnitTree, Optional<? extends CompilationUnitTree>>() {
        @Override public Optional<? extends CompilationUnitTree> apply(
            final CompilationUnitTree expectedTree) {
          return Iterables.tryFind(actualTrees,
              new Predicate<CompilationUnitTree>() {
            @Override public boolean apply(CompilationUnitTree actualTree) {
              return expectedTreeTypes.get(expectedTree).equals(
                  actualTreeTypes.get(actualTree));
            }
          });
        }
      });

      for (Map.Entry<? extends CompilationUnitTree, Optional<? extends CompilationUnitTree>>
      matchedTreePair : matchedTrees.entrySet()) {
        final CompilationUnitTree expectedTree = matchedTreePair.getKey();
        if (!matchedTreePair.getValue().isPresent()) {
          failNoCandidates(expectedTreeTypes.get(expectedTree), expectedTree,
              actualTreeTypes, actualTrees);
        } else {
          CompilationUnitTree actualTree = matchedTreePair.getValue().get();
          TreeDifference treeDifference = TreeDiffer.diffCompilationUnits(expectedTree, actualTree);
          if (!treeDifference.isEmpty()) {
            String diffReport = treeDifference.getDiffReport(
                new TreeContext(expectedTree, expectedResult.trees()),
                new TreeContext(actualTree, actualResult.trees()));
            failWithCandidate(expectedTree.getSourceFile(), actualTree.getSourceFile(), diffReport);
          }
        }
      }
    }

    /** Called when the {@code generatesSources()} verb fails with no diff candidates. */
    private void failNoCandidates(ImmutableSet<String> expectedTypes,
        CompilationUnitTree expectedTree,
        final ImmutableMap<? extends CompilationUnitTree, ImmutableSet<String>> actualTypes,
        FluentIterable<? extends CompilationUnitTree> actualTrees) {
      String generatedTypesReport = Joiner.on('\n').join(
          actualTrees.transform(new Function<CompilationUnitTree, String>() {
                @Override public String apply(CompilationUnitTree generated) {
                  return String.format("- %s in <%s>",
                      actualTypes.get(generated),
                      generated.getSourceFile().toUri().getPath());
                }
              })
          .toList());
      failureStrategy.fail(Joiner.on('\n').join(
          "",
          "An expected source declared one or more top-level types that were not present.",
          "",
          String.format("Expected top-level types: <%s>", expectedTypes),
          String.format("Declared by expected file: <%s>",
              expectedTree.getSourceFile().toUri().getPath()),
          "",
          "The top-level types that were present are as follows: ",
          "",
          generatedTypesReport,
          ""));
    }

    /** Called when the {@code generatesSources()} verb fails with a diff candidate. */
    private void failWithCandidate(JavaFileObject expectedSource,
        JavaFileObject actualSource, String diffReport) {
      try {
        failureStrategy.fail(Joiner.on('\n').join(
            "",
            "Source declared the same top-level types of an expected source, but",
            "didn't match exactly.",
            "",
            String.format("Expected file: <%s>", expectedSource.toUri().getPath()),
            String.format("Actual file: <%s>", actualSource.toUri().getPath()),
            "",
            "Diffs:",
            "======",
            "",
            diffReport,
            "",
            "Expected Source: ",
            "================",
            "",
            expectedSource.getCharContent(false).toString(),
            "",
            "Actual Source:",
            "=================",
            "",
            actualSource.getCharContent(false).toString()));
      } catch (IOException e) {
        throw new IllegalStateException("Couldn't read from JavaFileObject when it was already "
            + "in memory.", e);
      }
    }

    @CanIgnoreReturnValue
    @Override
    public SuccessfulCompilationClause compilesWithoutError() {
      Compilation compilation = compilation();
      check().about(compilations()).that(compilation).succeeded();
      return new SuccessfulCompilationBuilder(compilation);
    }

    @CanIgnoreReturnValue
    @Override
    public CleanCompilationClause compilesWithoutWarnings() {
      Compilation compilation = compilation();
      check().about(compilations()).that(compilation).succeededWithoutWarnings();
      return new CleanCompilationBuilder(compilation);
    }

    @CanIgnoreReturnValue
    @Override
    public UnsuccessfulCompilationClause failsToCompile() {
      Compilation compilation = compilation();
      check().about(compilations()).that(compilation).failed();
      return new UnsuccessfulCompilationBuilder(compilation);
    }

    private Compilation compilation() {
      return javac().withProcessors(processors).withOptions(options).compile(actual());
    }
  }

  /**
   * A helper method for {@link SingleSourceAdapter} to ensure that the inner class is created
   * correctly.
   */
  private CompilationClause newCompilationClause(Iterable<? extends Processor> processors) {
    return new CompilationClause(processors);
  }

  /**
   * Base implementation of {@link CompilationWithWarningsClause}.
   *
   * @param T the type parameter for {@link CompilationWithWarningsClause}. {@code this} must be an
   *     instance of {@code T}; otherwise some calls will throw {@link ClassCastException}.
   */
  abstract class CompilationWithWarningsBuilder<T> implements CompilationWithWarningsClause<T> {
    protected final Compilation compilation;

    protected CompilationWithWarningsBuilder(Compilation compilation) {
      this.compilation = compilation;
    }

    @CanIgnoreReturnValue
    @Override
    public T withNoteCount(int noteCount) {
      check().about(compilations()).that(compilation).hadNoteCount(noteCount);
      return thisObject();
    }

    @CanIgnoreReturnValue
    @Override
    public FileClause<T> withNoteContaining(String messageFragment) {
      return new FileBuilder(
          check().about(compilations()).that(compilation).hadNoteContaining(messageFragment));
    }

    @CanIgnoreReturnValue
    @Override
    public T withWarningCount(int warningCount) {
      check().about(compilations()).that(compilation).hadWarningCount(warningCount);
      return thisObject();
    }

    @CanIgnoreReturnValue
    @Override
    public FileClause<T> withWarningContaining(String messageFragment) {
      return new FileBuilder(
          check().about(compilations()).that(compilation).hadWarningContaining(messageFragment));
    }

    @CanIgnoreReturnValue
    public T withErrorCount(int errorCount) {
      check().about(compilations()).that(compilation).hadErrorCount(errorCount);
      return thisObject();
    }

    @CanIgnoreReturnValue
    public FileClause<T> withErrorContaining(String messageFragment) {
      return new FileBuilder(
          check().about(compilations()).that(compilation).hadErrorContaining(messageFragment));
    }

    /**
     * Returns this object, cast to {@code T}.
     */
    @SuppressWarnings("unchecked")
    protected final T thisObject() {
      return (T) this;
    }

    private final class FileBuilder implements FileClause<T> {
      private final DiagnosticInFile diagnosticInFile;

      private FileBuilder(DiagnosticInFile diagnosticInFile) {
        this.diagnosticInFile = diagnosticInFile;
      }

      @Override
      public T and() {
        return thisObject();
      }

      @Override
      public LineClause<T> in(JavaFileObject file) {
        final DiagnosticOnLine diagnosticOnLine = diagnosticInFile.inFile(file);

        return new LineClause<T>() {
          @Override
          public T and() {
            return thisObject();
          }

          @Override
          public ColumnClause<T> onLine(long lineNumber) {
            final DiagnosticAtColumn diagnosticAtColumn = diagnosticOnLine.onLine(lineNumber);

            return new ColumnClause<T>() {
              @Override
              public T and() {
                return thisObject();
              }

              @Override
              public ChainingClause<T> atColumn(long columnNumber) {
                diagnosticAtColumn.atColumn(columnNumber);
                return this;
              }
            };
          }
        };
      }
    }
  }

  /**
   * Base implementation of {@link GeneratedPredicateClause GeneratedPredicateClause<T>} and
   * {@link ChainingClause ChainingClause<GeneratedPredicateClause<T>>}.
   *
   * @param T the type parameter to {@link GeneratedPredicateClause}. {@code this} must be an
   *     instance of {@code T}.
   */
  private abstract class GeneratedCompilationBuilder<T> extends CompilationWithWarningsBuilder<T>
      implements GeneratedPredicateClause<T>, ChainingClause<GeneratedPredicateClause<T>> {

    protected GeneratedCompilationBuilder(Compilation compilation) {
      super(compilation);
    }

    @CanIgnoreReturnValue
    @Override
    public T generatesSources(JavaFileObject first, JavaFileObject... rest) {
      new JavaSourcesSubject(failureStrategy, compilation.generatedSourceFiles())
          .parsesAs(first, rest);
      return thisObject();
    }

    @CanIgnoreReturnValue
    @Override
    public T generatesFiles(JavaFileObject first, JavaFileObject... rest) {
      for (JavaFileObject expected : Lists.asList(first, rest)) {
        if (!wasGenerated(expected)) {
          failureStrategy.fail("Did not find a generated file corresponding to "
              + expected.getName());
        }
      }
      return thisObject();
    }

    boolean wasGenerated(JavaFileObject expected) {
      ByteSource expectedByteSource = JavaFileObjects.asByteSource(expected);
      for (JavaFileObject generated : compilation.generatedFiles()) {
        try {
          if (generated.getKind().equals(expected.getKind())
              && expectedByteSource.contentEquals(JavaFileObjects.asByteSource(generated))) {
            return true;
          }
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      return false;
    }

    @CanIgnoreReturnValue
    @Override
    public SuccessfulFileClause<T> generatesFileNamed(
        JavaFileManager.Location location, String packageName, String relativeName) {
      final JavaFileObjectSubject javaFileObjectSubject =
          check()
              .about(compilations())
              .that(compilation)
              .generatedFile(location, packageName, relativeName);
      return new SuccessfulFileClause<T>() {
        @Override
        public GeneratedPredicateClause<T> and() {
          return GeneratedCompilationBuilder.this;
        }

        @Override
        public SuccessfulFileClause<T> withContents(ByteSource expectedByteSource) {
          javaFileObjectSubject.hasContents(expectedByteSource);
          return this;
        }

        @Override
        public SuccessfulFileClause<T> withStringContents(Charset charset, String expectedString) {
          javaFileObjectSubject.contentsAsString(charset).isEqualTo(expectedString);
          return this;
        }
      };
    }

    @Override
    public GeneratedPredicateClause<T> and() {
      return this;
    }
  }

  final class CompilationBuilder extends GeneratedCompilationBuilder<CompilationBuilder> {
    CompilationBuilder(Compilation compilation) {
      super(compilation);
    }
  }

  private final class UnsuccessfulCompilationBuilder
      extends CompilationWithWarningsBuilder<UnsuccessfulCompilationClause>
      implements UnsuccessfulCompilationClause {

    UnsuccessfulCompilationBuilder(Compilation compilation) {
      super(compilation);
    }
  }

  private final class SuccessfulCompilationBuilder
      extends GeneratedCompilationBuilder<SuccessfulCompilationClause>
      implements SuccessfulCompilationClause {

    SuccessfulCompilationBuilder(Compilation compilation) {
      super(compilation);
    }
  }

  private final class CleanCompilationBuilder
      extends GeneratedCompilationBuilder<CleanCompilationClause>
      implements CleanCompilationClause {

    CleanCompilationBuilder(Compilation compilation) {
      super(compilation);
    }
  }

  public static JavaSourcesSubject assertThat(JavaFileObject javaFileObject) {
    return assertAbout(javaSources()).that(ImmutableList.of(javaFileObject));
  }

  public static JavaSourcesSubject assertThat(
      JavaFileObject javaFileObject, JavaFileObject... javaFileObjects) {
    return assertAbout(javaSources())
        .that(ImmutableList.<JavaFileObject>builder()
            .add(javaFileObject)
            .add(javaFileObjects)
            .build());
  }

  public static final class SingleSourceAdapter
      extends Subject<SingleSourceAdapter, JavaFileObject>
      implements CompileTester, ProcessedCompileTesterFactory {
    private final JavaSourcesSubject delegate;

    SingleSourceAdapter(FailureStrategy failureStrategy, JavaFileObject subject) {
      super(failureStrategy, subject);
      this.delegate =
          new JavaSourcesSubject(failureStrategy, ImmutableList.of(subject));
    }

    @Override
    public JavaSourcesSubject withCompilerOptions(Iterable<String> options) {
      return delegate.withCompilerOptions(options);
    }

    @Override
    public JavaSourcesSubject withCompilerOptions(String... options) {
      return delegate.withCompilerOptions(options);
    }

    @Override
    public CompileTester processedWith(Processor first, Processor... rest) {
      return delegate.newCompilationClause(Lists.asList(first, rest));
    }

    @Override
    public CompileTester processedWith(Iterable<? extends Processor> processors) {
      return delegate.newCompilationClause(processors);
    }

    @CanIgnoreReturnValue
    @Override
    public SuccessfulCompilationClause compilesWithoutError() {
      return delegate.compilesWithoutError();
    }

    @CanIgnoreReturnValue
    @Override
    public CleanCompilationClause compilesWithoutWarnings() {
      return delegate.compilesWithoutWarnings();
    }

    @CanIgnoreReturnValue
    @Override
    public UnsuccessfulCompilationClause failsToCompile() {
      return delegate.failsToCompile();
    }

    @Override
    public void parsesAs(JavaFileObject first, JavaFileObject... rest) {
      delegate.parsesAs(first, rest);
    }
  }
}
