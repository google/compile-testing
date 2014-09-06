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
import com.google.testing.compile.JavacCompilation.Result;
import com.google.testing.compile.CompileTester.SuccessfulCompilationClause;

import com.sun.source.tree.CompilationUnitTree;

import java.io.IOException;
import java.util.Map;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
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
  JavaSourcesSubject(FailureStrategy failureStrategy, Iterable<? extends JavaFileObject> subject) {
    super(failureStrategy, subject);
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
  public SuccessfulCompilationClause compilesWithoutError() {
    return new CompilationClause().compilesWithoutError();
  }

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
     * {@link JavacCompilation.Result}
     */
    private String reportFilesGenerated(JavacCompilation.Result result) {
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
    public SuccessfulCompilationClause compilesWithoutError() {
      JavacCompilation.Result result = JavacCompilation.compile(processors, getSubject());
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
      return new SuccessfulCompilationBuilder(result);
    }

    @Override
    public UnsuccessfulCompilationClause failsToCompile() {
      Result result = JavacCompilation.compile(processors, getSubject());
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

  private final class UnsuccessfulCompilationBuilder implements UnsuccessfulCompilationClause {
    private final JavacCompilation.Result result;

    UnsuccessfulCompilationBuilder(JavacCompilation.Result result) {
      checkArgument(!result.successful());
      this.result = result;
    }

    @Override
    public FileClause withErrorContaining(final String messageFragment) {
      FluentIterable<Diagnostic<? extends JavaFileObject>> diagnostics =
          FluentIterable.from(result.diagnosticsByKind().get(Kind.ERROR));
      final FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsWithMessage =
          diagnostics.filter(new Predicate<Diagnostic<?>>() {
            @Override
            public boolean apply(Diagnostic<?> input) {
              return input.getMessage(null).contains(messageFragment);
            }
          });
      if (diagnosticsWithMessage.isEmpty()) {
        failureStrategy.fail(String.format(
            "Expected an error containing \"%s\", but only found %s", messageFragment,
            diagnostics.transform(
              new Function<Diagnostic<?>, String>() {
                @Override public String apply(Diagnostic<?> input) {
                  return "\"" + input.getMessage(null) + "\"";
                }
              })));
      }
      return new FileClause() {
        @Override
        public UnsuccessfulCompilationClause and() {
          return UnsuccessfulCompilationBuilder.this;
        }

        @Override
        public LineClause in(final JavaFileObject file) {
          final FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsInFile =
              diagnosticsWithMessage.filter(new Predicate<Diagnostic<? extends FileObject>>() {
                @Override
                public boolean apply(Diagnostic<? extends FileObject> input) {
                  return ((input.getSource() != null)
                      && file.toUri().getPath().equals(input.getSource().toUri().getPath()));
                }
              });
          if (diagnosticsInFile.isEmpty()) {
            failureStrategy.fail(String.format(
                "Expected an error in %s, but only found errors in %s", file.getName(),
                diagnosticsWithMessage.transform(
                    new Function<Diagnostic<? extends FileObject>, String>() {
                      @Override public String apply(Diagnostic<? extends FileObject> input) {
                        return (input.getSource() != null) ? input.getSource().getName()
                            : "(no associated file)";
                      }
                    })
                .toSet()));
          }
          return new LineClause() {
            @Override public UnsuccessfulCompilationClause and() {
              return UnsuccessfulCompilationBuilder.this;
            }

            @Override public ColumnClause onLine(final long lineNumber) {
              final FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsOnLine =
                  diagnosticsWithMessage.filter(new Predicate<Diagnostic<?>>() {
                    @Override
                    public boolean apply(Diagnostic<?> input) {
                      return lineNumber == input.getLineNumber();
                    }
                  });
              if (diagnosticsOnLine.isEmpty()) {
                failureStrategy.fail(String.format(
                    "Expected an error on line %d of %s, but only found errors on line(s) %s",
                    lineNumber, file.getName(), diagnosticsInFile.transform(
                        new Function<Diagnostic<?>, String>() {
                          @Override public String apply(Diagnostic<?> input) {
                            long errLine = input.getLineNumber();
                            return (errLine != Diagnostic.NOPOS) ? errLine + ""
                                : "(no associated position)";
                          }
                        })
                    .toSet()));
              }
              return new ColumnClause() {
                @Override
                public UnsuccessfulCompilationClause and() {
                  return UnsuccessfulCompilationBuilder.this;
                }

                @Override
                public ChainingClause<UnsuccessfulCompilationClause> atColumn(
                    final long columnNumber) {
                  FluentIterable<Diagnostic<? extends JavaFileObject>> diagnosticsAtColumn =
                      diagnosticsOnLine.filter(new Predicate<Diagnostic<?>>() {
                        @Override
                        public boolean apply(Diagnostic<?> input) {
                          return columnNumber == input.getColumnNumber();
                        }
                      });
                  if (diagnosticsAtColumn.isEmpty()) {
                    failureStrategy.fail(String.format(
                        "Expected an error at %d:%d of %s, but only found errors at column(s) %s",
                        lineNumber, columnNumber, file.getName(), diagnosticsOnLine.transform(
                            new Function<Diagnostic<?>, String>() {
                              @Override public String apply(Diagnostic<?> input) {
                                long errCol = input.getColumnNumber();
                                return (errCol != Diagnostic.NOPOS) ? errCol + ""
                                    : "(no associated position)";
                              }
                            })
                        .toSet()));
                  }
                  return new ChainingClause<UnsuccessfulCompilationClause>() {
                    @Override public UnsuccessfulCompilationClause and() {
                      return UnsuccessfulCompilationBuilder.this;
                    }
                  };
                }
              };
            }
          };
        }
      };
    }
  }

  private final class SuccessfulCompilationBuilder implements SuccessfulCompilationClause,
      GeneratedPredicateClause {
    private final JavacCompilation.Result result;

    SuccessfulCompilationBuilder(JavacCompilation.Result result) {
      checkArgument(result.successful());
      this.result = result;
    }

    @Override
    public GeneratedPredicateClause and() {
      return this;
    }

    @Override
    public SuccessfulCompilationClause compilesAs(JavaFileObject first, JavaFileObject... rest) {
      return expectParseResults(
          result,
          JavacCompilation.parse(Lists.asList(first, rest)));
    }

    @Override
    public SuccessfulCompilationClause generatesSources(JavaFileObject first,
        JavaFileObject... rest) {
      return expectParseResults(
          JavacCompilation.parse(result.generatedSources()), 
          JavacCompilation.parse(Lists.asList(first, rest)));
    }
    
    private SuccessfulCompilationClause expectParseResults(
        final JavacCompilation.ParseResult actualResult, final JavacCompilation.ParseResult expectedResult) {
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
      return this;
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
          "An expected source declared one or more top-level types that were not generated.",
          "",
          String.format("Expected top-level types: <%s>", expectedTypes),
          String.format("Declared by expected file: <%s>",
              expectedTree.getSourceFile().toUri().getPath()),
          "",
          "The top-level types that were generated are as follows: ",
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
            "A generated source declared the same top-level types of an expected source, but",
            "didn't match exactly.",
            "",
            String.format("Expected file: <%s>", expectedSource.toUri().getPath()),
            String.format("Generated file: <%s>", actualSource.toUri().getPath()),
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


    @Override
    public SuccessfulCompilationClause generatesFiles(JavaFileObject first,
        JavaFileObject... rest) {
      for (JavaFileObject expected : Lists.asList(first, rest)) {
        if (!wasGenerated(result, expected)) {
          failureStrategy.fail("Did not find a generated file corresponding to "
              + expected.getName());
        }
      }
      return this;
    }

    boolean wasGenerated(JavacCompilation.Result result, JavaFileObject expected) {
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
    public CompileTester processedWith(Processor first, Processor... rest) {
      return delegate.newCompilationClause(Lists.asList(first, rest));
    }

    @Override
    public CompileTester processedWith(Iterable<? extends Processor> processors) {
      return delegate.newCompilationClause(processors);
    }

    @Override
    public SuccessfulCompilationClause compilesWithoutError() {
      return delegate.compilesWithoutError();
    }

    @Override
    public UnsuccessfulCompilationClause failsToCompile() {
      return delegate.failsToCompile();
    }
  }
}
