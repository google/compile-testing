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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.testing.compile.Compilation.Result;

import com.sun.source.tree.CompilationUnitTree;

import org.truth0.FailureStrategy;
import org.truth0.subjects.Subject;

import java.io.IOException;
import java.util.Arrays;

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

    @Override
    public SuccessfulCompilationClause compilesWithoutError() {
      Compilation.Result result = Compilation.compile(processors, getSubject());
      if (!result.successful()) {
        ImmutableList<Diagnostic<? extends JavaFileObject>> errors =
            result.diagnosticsByKind().get(Kind.ERROR);
        StringBuilder message = new StringBuilder("Compilation produced the following errors:\n");
        Joiner.on('\n').appendTo(message, errors);
        failureStrategy.fail(message.toString());
      }
      return new SuccessfulCompilationBuilder(result);
    }

    @Override
    public UnsuccessfulCompilationClause failsToCompile() {
      Result result = Compilation.compile(processors, getSubject());
      if (result.successful()) {
        failureStrategy.fail("Compilation was expected to fail, but contained no errors");
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
    private final Compilation.Result result;

    UnsuccessfulCompilationBuilder(Compilation.Result result) {
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
                  return file.toUri().getPath().equals(input.getSource().toUri().getPath());
                }
              });
          if (diagnosticsInFile.isEmpty()) {
            failureStrategy.fail(String.format(
                "Expected an error in %s, but only found errors in %s", file.getName(),
                diagnosticsWithMessage.transform(
                    new Function<Diagnostic<? extends FileObject>, String>() {
                      @Override public String apply(Diagnostic<? extends FileObject> input) {
                        return input.getSource().getName();
                      }
                    })));
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
                        new Function<Diagnostic<?>, Long>() {
                          @Override public Long apply(Diagnostic<?> input) {
                            return input.getLineNumber();
                          }
                        })));
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
                            new Function<Diagnostic<?>, Long>() {
                              @Override public Long apply(Diagnostic<?> input) {
                                return input.getColumnNumber();
                              }
                            })));
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
    private final Compilation.Result result;

    SuccessfulCompilationBuilder(Compilation.Result result) {
      checkArgument(result.successful());
      this.result = result;
    }

    @Override
    public GeneratedPredicateClause and() {
      return this;
    }

    @Override
    public SuccessfulCompilationClause generatesSources(JavaFileObject first,
        JavaFileObject... rest) {
      ImmutableList<JavaFileObject> generatedSources = result.generatedSources();
      Iterable<? extends CompilationUnitTree> actualCompilationUnits =
          Compilation.parse(generatedSources);
      final EqualityScanner scanner = new EqualityScanner();
      for (final CompilationUnitTree expectedTree : Compilation.parse(Lists.asList(first, rest))) {
        Optional<? extends CompilationUnitTree> found =
            Iterables.tryFind(actualCompilationUnits, new Predicate<CompilationUnitTree>() {
              @Override
              public boolean apply(CompilationUnitTree actualTree) {
                return scanner.visitCompilationUnit(expectedTree, actualTree);
              }
            });
        if (!found.isPresent()) {
          final JavaFileObject expected = expectedTree.getSourceFile();
          Optional<JavaFileObject> actual =
              FluentIterable.from(generatedSources).firstMatch(new Predicate<JavaFileObject>() {
                @Override public boolean apply(JavaFileObject generatedFile) {
                  return generatedFile.toUri().getPath().endsWith(expected.toUri().getPath());
                }
              });
          if (actual.isPresent()) {
            CharSequence actualSource = null;
            try {
              actualSource = actual.get().getCharContent(false);
            } catch (IOException e) {
              throw new RuntimeException("Exception reading source content.", e);
            }
            failureStrategy.fail("Generated file " + expected.getName()
                + " did not match expectation. Found:\n"
                + (actualSource == null ? "no source found" : actualSource));
          } else {
            failureStrategy.fail("Did not find a source file named " + expected.getName());
          }
        }
      }
      return this;
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

    boolean wasGenerated(Compilation.Result result, JavaFileObject expected) {
      for (JavaFileObject generated : result.generatedFilesByKind().get(expected.getKind())) {
        try {
          if (Arrays.equals(
              ByteStreams.toByteArray(expected.openInputStream()),
              ByteStreams.toByteArray(generated.openInputStream()))) {
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
