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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Fact.simpleFact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.CompilationSubject.compilations;
import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static com.google.testing.compile.TypeEnumerator.getTopLevelTypes;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteSource;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.testing.compile.CompilationSubject.DiagnosticAtColumn;
import com.google.testing.compile.CompilationSubject.DiagnosticInFile;
import com.google.testing.compile.CompilationSubject.DiagnosticOnLine;
import com.google.testing.compile.Parser.ParseResult;
import com.sun.source.tree.CompilationUnitTree;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collector;
import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import org.jspecify.annotations.Nullable;

/**
 * A <a href="https://github.com/truth0/truth">Truth</a> {@link Subject} that evaluates the result
 * of a {@code javac} compilation. See {@link com.google.testing.compile} for usage examples
 *
 * @author Gregory Kick
 */
@SuppressWarnings("restriction") // Sun APIs usage intended
public final class JavaSourcesSubject extends Subject
    implements CompileTester, ProcessedCompileTesterFactory {
  private final @Nullable Iterable<? extends JavaFileObject> actual;
  private final List<String> options = new ArrayList<>(Arrays.asList("-Xlint"));
  @Nullable private ClassLoader classLoader;
  @Nullable private ImmutableList<File> classPath;

  JavaSourcesSubject(
      FailureMetadata failureMetadata, @Nullable Iterable<? extends JavaFileObject> actual) {
    super(failureMetadata, actual);
    this.actual = actual;
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

  /**
   * @deprecated prefer {@link #withClasspath(Iterable)}. This method only supports {@link
   *     java.net.URLClassLoader} and the default system classloader, and {@link File}s are usually
   *     a more natural way to expression compilation classpaths than class loaders.
   */
  @Deprecated
  @Override
  public JavaSourcesSubject withClasspathFrom(ClassLoader classLoader) {
    this.classLoader = classLoader;
    return this;
  }

  @Override
  public JavaSourcesSubject withClasspath(Iterable<File> classPath) {
    this.classPath = ImmutableList.copyOf(classPath);
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
      this(ImmutableSet.of());
    }

    private CompilationClause(Iterable<? extends Processor> processors) {
      this.processors = ImmutableSet.copyOf(processors);
    }

    @Override
    public void parsesAs(JavaFileObject first, JavaFileObject... rest) {
      if (Iterables.isEmpty(actualNotNull())) {
        failWithoutActual(
            simpleFact(
                "Compilation generated no additional source files, though some were expected."));
        return;
      }
      ParseResult actualResult = Parser.parse(actualNotNull(), "*actual* source");
      ImmutableList<Diagnostic<? extends JavaFileObject>> errors =
          actualResult.diagnosticsByKind().get(Kind.ERROR);
      if (!errors.isEmpty()) {
        StringBuilder message = new StringBuilder("Parsing produced the following errors:\n");
        for (Diagnostic<? extends JavaFileObject> error : errors) {
          message.append('\n');
          message.append(error);
        }
        failWithoutActual(simpleFact(message.toString()));
        return;
      }
      ParseResult expectedResult = Parser.parse(Lists.asList(first, rest), "*expected* source");
      ImmutableList<TypedCompilationUnit> actualTrees =
          actualResult.compilationUnits().stream()
              .map(TypedCompilationUnit::create)
              .collect(toImmutableList());
      ImmutableList<TypedCompilationUnit> expectedTrees =
          expectedResult.compilationUnits().stream()
              .map(TypedCompilationUnit::create)
              .collect(toImmutableList());

      ImmutableMap<TypedCompilationUnit, Optional<TypedCompilationUnit>> matchedTrees =
          Maps.toMap(
              expectedTrees,
              expectedTree ->
                  actualTrees.stream()
                      .filter(actualTree -> expectedTree.types().equals(actualTree.types()))
                      .findFirst());

      matchedTrees.forEach(
          (expectedTree, maybeActualTree) -> {
            if (!maybeActualTree.isPresent()) {
              failNoCandidates(expectedTree.types(), expectedTree.tree(), actualTrees);
              return;
            }
            TypedCompilationUnit actualTree = maybeActualTree.get();
            TreeDifference treeDifference =
                TreeDiffer.diffCompilationUnits(expectedTree.tree(), actualTree.tree());
            if (!treeDifference.isEmpty()) {
              String diffReport =
                  treeDifference.getDiffReport(
                      new TreeContext(expectedTree.tree(), expectedResult.trees()),
                      new TreeContext(actualTree.tree(), actualResult.trees()));
              failWithCandidate(
                  expectedTree.tree().getSourceFile(),
                  actualTree.tree().getSourceFile(),
                  diffReport);
            }
          });
    }

    /** Called when the {@code generatesSources()} verb fails with no diff candidates. */
    private void failNoCandidates(
        ImmutableSet<String> expectedTypes,
        CompilationUnitTree expectedTree,
        ImmutableList<TypedCompilationUnit> actualTrees) {
      String generatedTypesReport =
          Joiner.on('\n')
              .join(
                  actualTrees.stream()
                      .map(
                          generated ->
                              String.format(
                                  "- %s in <%s>",
                                  generated.types(),
                                  generated.tree().getSourceFile().toUri().getPath()))
                      .collect(toList()));
      failWithoutActual(
          simpleFact(
              Joiner.on('\n')
                  .join(
                      "",
                      "An expected source declared one or more top-level types that were not "
                          + "present.",
                      "",
                      String.format("Expected top-level types: <%s>", expectedTypes),
                      String.format(
                          "Declared by expected file: <%s>",
                          expectedTree.getSourceFile().toUri().getPath()),
                      "",
                      "The top-level types that were present are as follows: ",
                      "",
                      generatedTypesReport,
                      "")));
    }

    /** Called when the {@code generatesSources()} verb fails with a diff candidate. */
    private void failWithCandidate(
        JavaFileObject expectedSource, JavaFileObject actualSource, String diffReport) {
      try {
        failWithoutActual(
            simpleFact(
                Joiner.on('\n')
                    .join(
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
                        actualSource.getCharContent(false).toString())));
      } catch (IOException e) {
        throw new IllegalStateException(
            "Couldn't read from JavaFileObject when it was already " + "in memory.", e);
      }
    }

    @CanIgnoreReturnValue
    @Override
    public SuccessfulCompilationClause compilesWithoutError() {
      Compilation compilation = compilation();
      check("compilation()").about(compilations()).that(compilation).succeeded();
      return new SuccessfulCompilationBuilder(compilation);
    }

    @CanIgnoreReturnValue
    @Override
    public CleanCompilationClause compilesWithoutWarnings() {
      Compilation compilation = compilation();
      check("compilation()").about(compilations()).that(compilation).succeededWithoutWarnings();
      return new CleanCompilationBuilder(compilation);
    }

    @CanIgnoreReturnValue
    @Override
    public UnsuccessfulCompilationClause failsToCompile() {
      Compilation compilation = compilation();
      check("compilation()").about(compilations()).that(compilation).failed();
      return new UnsuccessfulCompilationBuilder(compilation);
    }

    private Compilation compilation() {
      Compiler compiler = javac().withProcessors(processors).withOptions(options);
      if (classLoader != null) {
        compiler = compiler.withClasspathFrom(classLoader);
      }
      if (classPath != null) {
        compiler = compiler.withClasspath(classPath);
      }
      return compiler.compile(actualNotNull());
    }
  }

  @AutoValue
  abstract static class TypedCompilationUnit {
    abstract CompilationUnitTree tree();

    abstract ImmutableSet<String> types();

    static TypedCompilationUnit create(CompilationUnitTree tree) {
      return new AutoValue_JavaSourcesSubject_TypedCompilationUnit(tree, getTopLevelTypes(tree));
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
   * @param <T> the type parameter for {@link CompilationWithWarningsClause}. {@code this} must be
   *     an instance of {@code T}; otherwise some calls will throw {@link ClassCastException}.
   */
  abstract class CompilationWithWarningsBuilder<T> implements CompilationWithWarningsClause<T> {
    protected final Compilation compilation;

    protected CompilationWithWarningsBuilder(Compilation compilation) {
      this.compilation = compilation;
    }

    @CanIgnoreReturnValue
    @Override
    public T withNoteCount(int noteCount) {
      check("compilation()").about(compilations()).that(compilation).hadNoteCount(noteCount);
      return thisObject();
    }

    @CanIgnoreReturnValue
    @Override
    public FileClause<T> withNoteContaining(String messageFragment) {
      return new FileBuilder(
          check("compilation()")
              .about(compilations())
              .that(compilation)
              .hadNoteContaining(messageFragment));
    }

    @CanIgnoreReturnValue
    @Override
    public T withWarningCount(int warningCount) {
      check("compilation()").about(compilations()).that(compilation).hadWarningCount(warningCount);
      return thisObject();
    }

    @CanIgnoreReturnValue
    @Override
    public FileClause<T> withWarningContaining(String messageFragment) {
      return new FileBuilder(
          check("compilation()")
              .about(compilations())
              .that(compilation)
              .hadWarningContaining(messageFragment));
    }

    @CanIgnoreReturnValue
    public T withErrorCount(int errorCount) {
      check("compilation()").about(compilations()).that(compilation).hadErrorCount(errorCount);
      return thisObject();
    }

    @CanIgnoreReturnValue
    public FileClause<T> withErrorContaining(String messageFragment) {
      return new FileBuilder(
          check("compilation()")
              .about(compilations())
              .that(compilation)
              .hadErrorContaining(messageFragment));
    }

    /** Returns this object, cast to {@code T}. */
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
   * Base implementation of {@link GeneratedPredicateClause GeneratedPredicateClause<T>} and {@link
   * ChainingClause ChainingClause<GeneratedPredicateClause<T>>}.
   *
   * @param <T> the type parameter to {@link GeneratedPredicateClause}. {@code this} must be an
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
      check("generatedSourceFiles()")
          .about(javaSources())
          .that(compilation.generatedSourceFiles())
          .parsesAs(first, rest);
      return thisObject();
    }

    @CanIgnoreReturnValue
    @Override
    public T generatesFiles(JavaFileObject first, JavaFileObject... rest) {
      for (JavaFileObject expected : Lists.asList(first, rest)) {
        if (!wasGenerated(expected)) {
          failWithoutActual(
              simpleFact("Did not find a generated file corresponding to " + expected.getName()));
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
          check("compilation()")
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
        .that(
            ImmutableList.<JavaFileObject>builder()
                .add(javaFileObject)
                .add(javaFileObjects)
                .build());
  }

  private Iterable<? extends JavaFileObject> actualNotNull() {
    isNotNull();
    return checkNotNull(actual);
  }

  private static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return collectingAndThen(toList(), ImmutableList::copyOf);
  }

  public static final class SingleSourceAdapter extends Subject
      implements CompileTester, ProcessedCompileTesterFactory {
    private final JavaSourcesSubject delegate;

    SingleSourceAdapter(FailureMetadata failureMetadata, @Nullable JavaFileObject subject) {
      super(failureMetadata, subject);
      /*
       * TODO(b/131918061): It would make more sense to eliminate SingleSourceAdapter entirely.
       * Users can already use assertThat(JavaFileObject, JavaFileObject...) above for a single
       * file. Anyone who needs a Subject.Factory could fall back to
       * `about(javaSources()).that(ImmutableSet.of(source))`.
       *
       * We could take that on, or we could wait for JavaSourcesSubject to go away entirely in favor
       * of CompilationSubject.
       */
      this.delegate =
          check("delegate()").about(javaSources()).that(ImmutableList.of(checkNotNull(subject)));
    }

    @Override
    public JavaSourcesSubject withCompilerOptions(Iterable<String> options) {
      return delegate.withCompilerOptions(options);
    }

    @Override
    public JavaSourcesSubject withCompilerOptions(String... options) {
      return delegate.withCompilerOptions(options);
    }

    /**
     * @deprecated prefer {@link #withClasspath(Iterable)}. This method only supports {@link
     *     java.net.URLClassLoader} and the default system classloader, and {@link File}s are
     *     usually a more natural way to expression compilation classpaths than class loaders.
     */
    @Deprecated
    @Override
    public JavaSourcesSubject withClasspathFrom(ClassLoader classLoader) {
      return delegate.withClasspathFrom(classLoader);
    }

    @Override
    public JavaSourcesSubject withClasspath(Iterable<File> classPath) {
      return delegate.withClasspath(classPath);
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
