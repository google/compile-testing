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
import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.testing.compile.Compilation.Result;

import com.sun.source.tree.CompilationUnitTree;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

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
  private final Set<String> options = Sets.newHashSet();
  
  JavaSourcesSubject(FailureStrategy failureStrategy, Iterable<? extends JavaFileObject> subject) {
    super(failureStrategy, subject);
  }
  
  @Override
  public ProcessedCompileTesterFactory withCompilerOptions(Iterable<String> options) {
    Iterables.addAll(this.options, options);
    return this;
  }
  
  @Override
  public ProcessedCompileTesterFactory withCompilerOptions(String... options) {
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

    @Override
    public SuccessfulCompilationClause compilesWithoutError() {
      Compilation.Result result =
          Compilation.compile(processors, ImmutableSet.copyOf(options), getSubject());
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
      Result result = Compilation.compile(processors, ImmutableSet.copyOf(options), getSubject());
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
      new JavaSourcesSubject(failureStrategy, result.generatedSources())
          .parsesAs(first, rest);
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

    @Override
    public SuccessfulFileClause generatesFileNamed(
        JavaFileManager.Location location, String packageName, String relativeName) {
      // TODO(gak): Validate that these inputs aren't null, location is an output location, and
      // packageName is a valid package name.
      // We're relying on the implementation of location.getName() to be equivalent to the path of
      // the location.
      String expectedFilename = new StringBuilder(location.getName()).append('/')
          .append(packageName.replace('.', '/')).append('/').append(relativeName).toString();

      for (JavaFileObject generated : result.generatedFilesByKind().values()) {
        if (generated.toUri().getPath().endsWith(expectedFilename)) {
          return new SuccessfulFileBuilder(this, generated.toUri().getPath(),
              JavaFileObjects.asByteSource(generated));
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
      return new SuccessfulFileBuilder(this, null, null);
    }
  }

  private final class SuccessfulFileBuilder implements SuccessfulFileClause {
    private final SuccessfulCompilationBuilder compilationClause;
    private final String generatedFilePath;
    private final ByteSource generatedByteSource;

    SuccessfulFileBuilder(SuccessfulCompilationBuilder compilationClause, String generatedFilePath,
        ByteSource generatedByteSource) {
      this.compilationClause = compilationClause;
      this.generatedFilePath = generatedFilePath;
      this.generatedByteSource = generatedByteSource;
    }

    @Override
    public GeneratedPredicateClause and() {
      return compilationClause;
    }

    @Override
    public SuccessfulFileClause withContents(ByteSource expectedByteSource) {
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
    public ProcessedCompileTesterFactory withCompilerOptions(Iterable<String> options) {
      return delegate.withCompilerOptions(options);
    }
    
    @Override
    public ProcessedCompileTesterFactory withCompilerOptions(String... options) {
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

    @Override
    public SuccessfulCompilationClause compilesWithoutError() {
      return delegate.compilesWithoutError();
    }

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
