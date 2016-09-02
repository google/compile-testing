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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;
import static javax.tools.JavaFileObject.Kind.CLASS;

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
import com.google.testing.compile.Compilation.Result;
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
import javax.tools.FileObject;
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

    /** Returns a {@code String} report describing the contents of a given generated file. */
    private String reportFileGenerated(JavaFileObject generatedFile) {
      try {
        StringBuilder entry =
            new StringBuilder().append(String.format("\n%s:\n", generatedFile.toUri().getPath()));
        if (generatedFile.getKind().equals(CLASS)) {
          entry.append(String.format("[generated class file (%s bytes)]",
                  JavaFileObjects.asByteSource(generatedFile).size()));
        } else {
          entry.append(generatedFile.getCharContent(true));
        }
        return entry.append("\n").toString();
      } catch (IOException e) {
        throw new IllegalStateException("Couldn't read from JavaFileObject when it was "
            + "already in memory.", e);
      }
    }

    /**
     * Returns a {@code String} report describing what files were generated in the given
     * {@link Compilation.Result}
     */
    private String reportFilesGenerated(Compilation.Result result) {
      FluentIterable<JavaFileObject> generatedFiles =
          FluentIterable.from(result.generatedSources());
      StringBuilder message = new StringBuilder("\n\n");
      if (generatedFiles.isEmpty()) {
        return message.append("(No files were generated.)\n").toString();
      } else {
        message.append("Generated Files\n")
            .append("===============\n");
        for (JavaFileObject generatedFile : generatedFiles) {
          message.append(reportFileGenerated(generatedFile));
        }
        return message.toString();
      }
    }

    @Override
    public void parsesAs(JavaFileObject first, JavaFileObject... rest) {
      Compilation.ParseResult actualResult = Compilation.parse(getSubject());
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
      final Compilation.ParseResult expectedResult = Compilation.parse(Lists.asList(first, rest));
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
      return new SuccessfulCompilationBuilder(successfulCompilationResult());
    }

    @CanIgnoreReturnValue
    @Override
    public CleanCompilationClause compilesWithoutWarnings() {
      return new CleanCompilationBuilder(successfulCompilationResult()).withWarningCount(0);
    }

    private Compilation.Result successfulCompilationResult() {
      Compilation.Result result =
          Compilation.compile(processors, options, getSubject());
      if (!result.successful()) {
        ImmutableList<Diagnostic<? extends JavaFileObject>> errors =
            result.diagnosticsByKind().get(Kind.ERROR);
        StringBuilder message = new StringBuilder("Compilation produced the following errors:\n");
        for (Diagnostic<? extends JavaFileObject> error : errors) {
          message.append('\n');
          message.append(error);
        }
        message.append('\n');
        message.append(reportFilesGenerated(result));
        failureStrategy.fail(message.toString());
      }
      return result;
    }

    @CanIgnoreReturnValue
    @Override
    public UnsuccessfulCompilationClause failsToCompile() {
      Result result = Compilation.compile(processors, options, getSubject());
      if (result.successful()) {
        String message = Joiner.on('\n').join(
            "Compilation was expected to fail, but contained no errors.",
            "",
            reportFilesGenerated(result));
        failureStrategy.fail(message);
      }
      return new UnsuccessfulCompilationBuilder(result);
    }
  }

  /**
   * A helper method for {@link SingleSourceAdapter} to ensure that the inner class is created
   * correctly.
   */
  private CompilationClause newCompilationClause(Iterable<? extends Processor> processors) {
    return new CompilationClause(processors);
  }

  private static String messageListing(Iterable<? extends Diagnostic<?>> diagnostics,
      String headingFormat, Object... formatArgs) {
    StringBuilder listing = new StringBuilder(String.format(headingFormat, formatArgs))
        .append('\n');
    for (Diagnostic<?> diagnostic : diagnostics) {
      listing.append(diagnostic.getMessage(null)).append('\n');
    }
    return listing.toString();
  }

  /**
   * Returns a string representation of a diagnostic kind.
   *
   * @param expectingSpecificCount {@code true} if being used after a count, as in "Expected 5
   *     errors"; {@code false} if being used to describe one message, as in "Expected a warning
   *     containing…".
   */
  private static String kindToString(Kind kind, boolean expectingSpecificCount) {
    switch (kind) {
      case ERROR:
        return expectingSpecificCount ? "errors" : "an error";

      case MANDATORY_WARNING:
      case WARNING:
        return expectingSpecificCount ? "warnings" : "a warning";

      case NOTE:
        return expectingSpecificCount ? "notes" : "a note";

      case OTHER:
        return expectingSpecificCount ? "diagnostic messages" : "a diagnostic message";

      default:
        throw new AssertionError(kind);
    }
  }

  /**
   * Base implementation of {@link CompilationWithWarningsClause}.
   *
   * @param T the type parameter for {@link CompilationWithWarningsClause}. {@code this} must be an
   *     instance of {@code T}; otherwise some calls will throw {@link ClassCastException}.
   */
  private abstract class CompilationWithWarningsBuilder<T>
      implements CompilationWithWarningsClause<T> {
    protected final Compilation.Result result;

    protected CompilationWithWarningsBuilder(Compilation.Result result) {
      this.result = result;
    }

    @CanIgnoreReturnValue
    @Override
    public T withNoteCount(int noteCount) {
      return withDiagnosticCount(Kind.NOTE, noteCount);
    }

    @CanIgnoreReturnValue
    @Override
    public FileClause<T> withNoteContaining(String messageFragment) {
      return withDiagnosticContaining(Kind.NOTE, messageFragment);
    }

    @CanIgnoreReturnValue
    @Override
    public T withWarningCount(int warningCount) {
      return withDiagnosticCount(Kind.WARNING, warningCount);
    }

    @CanIgnoreReturnValue
    @Override
    public FileClause<T> withWarningContaining(String messageFragment) {
      return withDiagnosticContaining(Kind.WARNING, messageFragment);
    }

    /**
     * Fails if the number of diagnostic messages of a given kind is not {@code expectedCount}.
     */
    @CanIgnoreReturnValue
    protected T withDiagnosticCount(Kind kind, int expectedCount) {
      List<Diagnostic<? extends JavaFileObject>> diagnostics = result.diagnosticsByKind().get(kind);
      if (diagnostics.size() != expectedCount) {
        failureStrategy.fail(
            messageListing(
                diagnostics,
                "Expected %d %s, but found the following %d %s:",
                expectedCount,
                kindToString(kind, true),
                diagnostics.size(),
                kindToString(kind, true)));
      }
      return thisObject();
    }

    /**
     * Fails if there is no diagnostic message of a given kind that contains
     * {@code messageFragment}.
     */
    @CanIgnoreReturnValue
    protected FileClause<T> withDiagnosticContaining(
        final Kind kind, final String messageFragment) {
      FluentIterable<Diagnostic<? extends JavaFileObject>> diagnostics =
          FluentIterable.from(result.diagnosticsByKind().get(kind));
      final FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsWithMessage =
          diagnostics.filter(
              new Predicate<Diagnostic<?>>() {
                @Override
                public boolean apply(Diagnostic<?> input) {
                  return input.getMessage(null).contains(messageFragment);
                }
              });
      if (diagnosticsWithMessage.isEmpty()) {
        failureStrategy.fail(
            messageListing(
                diagnostics,
                "Expected %s containing \"%s\", but only found:",
                kindToString(kind, false),
                messageFragment));
      }
      return new FileClause<T>() {

        @Override
        public T and() {
          return thisObject();
        }

        @CanIgnoreReturnValue
        @Override
        public LineClause<T> in(final JavaFileObject file) {
          final FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsInFile =
              diagnosticsWithMessage.filter(
                  new Predicate<Diagnostic<? extends FileObject>>() {
                    @Override
                    public boolean apply(Diagnostic<? extends FileObject> input) {
                      return ((input.getSource() != null)
                          && file.toUri().getPath().equals(input.getSource().toUri().getPath()));
                    }
                  });
          if (diagnosticsInFile.isEmpty()) {
            failureStrategy.fail(
                String.format(
                    "Expected %s in %s, but only found them in %s",
                    kindToString(kind, false),
                    file.getName(),
                    diagnosticsWithMessage
                        .transform(
                            new Function<Diagnostic<? extends FileObject>, String>() {
                              @Override
                              public String apply(Diagnostic<? extends FileObject> input) {
                                return (input.getSource() != null)
                                    ? input.getSource().getName()
                                    : "(no associated file)";
                              }
                            })
                        .toSet()));
          }
          return new LineClause<T>() {
            @Override
            public T and() {
              return thisObject();
            }

            @CanIgnoreReturnValue
            @Override
            public ColumnClause<T> onLine(final long lineNumber) {
              final FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsOnLine =
                  diagnosticsWithMessage.filter(
                      new Predicate<Diagnostic<?>>() {
                        @Override
                        public boolean apply(Diagnostic<?> input) {
                          return lineNumber == input.getLineNumber();
                        }
                      });
              if (diagnosticsOnLine.isEmpty()) {
                failureStrategy.fail(
                    String.format(
                        "Expected %s on line %d of %s, but only found them on line(s) %s",
                        kindToString(kind, false),
                        lineNumber,
                        file.getName(),
                        diagnosticsInFile
                            .transform(
                                new Function<Diagnostic<?>, String>() {
                                  @Override
                                  public String apply(Diagnostic<?> input) {
                                    long errLine = input.getLineNumber();
                                    return (errLine != Diagnostic.NOPOS)
                                        ? errLine + ""
                                        : "(no associated position)";
                                  }
                                })
                            .toSet()));
              }
              return new ColumnClause<T>() {
                @Override
                public T and() {
                  return thisObject();
                }

                @CanIgnoreReturnValue
                @Override
                public ChainingClause<T> atColumn(final long columnNumber) {
                  FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsAtColumn =
                      diagnosticsOnLine.filter(
                          new Predicate<Diagnostic<?>>() {
                            @Override
                            public boolean apply(Diagnostic<?> input) {
                              return columnNumber == input.getColumnNumber();
                            }
                          });
                  if (diagnosticsAtColumn.isEmpty()) {
                    failureStrategy.fail(
                        String.format(
                            "Expected %s at %d:%d of %s, but only found them at column(s) %s",
                            kindToString(kind, false),
                            lineNumber,
                            columnNumber,
                            file.getName(),
                            diagnosticsOnLine
                                .transform(
                                    new Function<Diagnostic<?>, String>() {
                                      @Override
                                      public String apply(Diagnostic<?> input) {
                                        long errCol = input.getColumnNumber();
                                        return (errCol != Diagnostic.NOPOS)
                                            ? errCol + ""
                                            : "(no associated position)";
                                      }
                                    })
                                .toSet()));
                  }
                  return this;
                }
              };
            }
          };
        }
      };
    }

    /**
     * Returns this object, cast to {@code T}.
     */
    @SuppressWarnings("unchecked")
    protected final T thisObject() {
      return (T) this;
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

    protected GeneratedCompilationBuilder(Result result) {
      super(result);
    }

    @CanIgnoreReturnValue
    @Override
    public T generatesSources(JavaFileObject first, JavaFileObject... rest) {
      new JavaSourcesSubject(failureStrategy, result.generatedSources())
          .parsesAs(first, rest);
      return thisObject();
    }

    @CanIgnoreReturnValue
    @Override
    public T generatesFiles(JavaFileObject first, JavaFileObject... rest) {
      for (JavaFileObject expected : Lists.asList(first, rest)) {
        if (!wasGenerated(result, expected)) {
          failureStrategy.fail("Did not find a generated file corresponding to "
              + expected.getName());
        }
      }
      return thisObject();
    }

    boolean wasGenerated(Compilation.Result result, JavaFileObject expected) {
      ByteSource expectedByteSource = JavaFileObjects.asByteSource(expected);
      for (JavaFileObject generated : result.generatedFilesByKind().get(expected.getKind())) {
        try {
          ByteSource generatedByteSource = JavaFileObjects.asByteSource(generated);
          if (expectedByteSource.contentEquals(generatedByteSource)) {
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
      // TODO(gak): Validate that these inputs aren't null, location is an output location, and
      // packageName is a valid package name.
      // We're relying on the implementation of location.getName() to be equivalent to the path of
      // the location.
      StringBuilder fileNameBuilder = new StringBuilder(location.getName()).append('/');
      if(packageName != null && !packageName.isEmpty()) {
        fileNameBuilder.append(packageName.replace('.', '/')).append('/');
      }
      String expectedFilename = fileNameBuilder.append(relativeName).toString();

      for (JavaFileObject generated : result.generatedFilesByKind().values()) {
        if (generated.toUri().getPath().endsWith(expectedFilename)) {
          return new SuccessfulFileBuilder<T>(
              this, generated.toUri().getPath(), JavaFileObjects.asByteSource(generated));
        }
      }
      StringBuilder encounteredFiles = new StringBuilder();
      for (JavaFileObject generated : result.generatedFilesByKind().values()) {
        if (generated.toUri().getPath().contains(location.getName())) {
          encounteredFiles.append("  ").append(generated.toUri().getPath()).append('\n');
        }
      }
      failureStrategy.fail("Did not find a generated file corresponding to " + relativeName
          + " in package " + packageName + "; Found: " + encounteredFiles.toString());
      return new SuccessfulFileBuilder<T>(this, null, null);
    }

    @Override
    public GeneratedPredicateClause<T> and() {
      return this;
    }
  }

  private final class UnsuccessfulCompilationBuilder
      extends CompilationWithWarningsBuilder<UnsuccessfulCompilationClause>
      implements UnsuccessfulCompilationClause {

    UnsuccessfulCompilationBuilder(Compilation.Result result) {
      super(result);
      checkArgument(!result.successful());
    }

    @CanIgnoreReturnValue
    @Override
    public UnsuccessfulCompilationClause withErrorCount(int errorCount) {
      return withDiagnosticCount(Kind.ERROR, errorCount);
    }

    @CanIgnoreReturnValue
    @Override
    public FileClause<UnsuccessfulCompilationClause> withErrorContaining(String messageFragment) {
      return withDiagnosticContaining(Kind.ERROR, messageFragment);
    }
  }

  private final class SuccessfulCompilationBuilder
      extends GeneratedCompilationBuilder<SuccessfulCompilationClause>
      implements SuccessfulCompilationClause {

    SuccessfulCompilationBuilder(Compilation.Result result) {
      super(result);
      checkArgument(result.successful());
    }
  }

  private final class CleanCompilationBuilder
      extends GeneratedCompilationBuilder<CleanCompilationClause>
      implements CleanCompilationClause {

    CleanCompilationBuilder(Compilation.Result result) {
      super(result);
      checkArgument(result.successful());
    }
  }

  private final class SuccessfulFileBuilder<T> implements SuccessfulFileClause<T> {
    private final GeneratedPredicateClause<T> chainedClause;
    private final String generatedFilePath;
    private final ByteSource generatedByteSource;

    SuccessfulFileBuilder(
        GeneratedPredicateClause<T> chainedClause,
        String generatedFilePath,
        ByteSource generatedByteSource) {
      this.chainedClause = chainedClause;
      this.generatedFilePath = generatedFilePath;
      this.generatedByteSource = generatedByteSource;
    }

    @Override
    public GeneratedPredicateClause<T> and() {
      return chainedClause;
    }

    @CanIgnoreReturnValue
    @Override
    public SuccessfulFileClause<T> withContents(ByteSource expectedByteSource) {
      try {
        if (!expectedByteSource.contentEquals(generatedByteSource)) {
          failureStrategy.fail("The contents in " + generatedFilePath
              + " did not match the expected contents");
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
    }

    @CanIgnoreReturnValue
    @Override
    public SuccessfulFileClause<T> withStringContents(Charset charset, String expectedString) {
      try {
        String generatedString = generatedByteSource.asCharSource(charset).read();
        if (!generatedString.equals(expectedString)) {
          failureStrategy.failComparing(
              "The contents in " + generatedFilePath + " did not match the expected string",
              expectedString,
              generatedString);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      return this;
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
