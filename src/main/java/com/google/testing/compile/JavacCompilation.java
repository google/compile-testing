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

import static com.google.common.base.Charsets.UTF_8;
import static javax.tools.JavaFileObject.Kind.SOURCE;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimaps;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTool;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/**
 * Utilities for performing compilation with {@code javac}.
 *
 * @author Gregory Kick
 */
final class JavacCompilation {
  private JavacCompilation() {}

  /**
   * Compile {@code sources} using {@code processors}.
   *
   * @throws RuntimeException if compilation fails.
   */
  static Result compile(Iterable<? extends Processor> processors,
      Iterable<? extends JavaFileObject> sources) {
    JavacTool compiler = (JavacTool) ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
    JavacTask task = compiler.getTask(
        null, // explicitly use the default because old versions of javac log some output on stderr
        fileManager,
        diagnosticCollector,
        ImmutableSet.<String>of(),
        ImmutableSet.<String>of(),
        sources);
    task.setProcessors(processors);
    try {
      Iterable<? extends CompilationUnitTree> parsedCompilationUnits = task.parse();
      Trees trees = Trees.instance(task);
      task.generate();
      List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
      
      boolean successful = true;
      for (Diagnostic<?> diagnostic : diagnostics) {
        if (Diagnostic.Kind.ERROR == diagnostic.getKind()) {
          successful = false;
        }
      }
      return new Result(successful, sortDiagnosticsByKind(diagnostics), 
          parsedCompilationUnits, trees, fileManager.getOutputFiles());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Parse {@code sources} into {@linkplain CompilationUnitTree compilation units}.  This method
   * <b>does not</b> compile the sources.
   */
  static ParseResult parse(Iterable<? extends JavaFileObject> sources) {
    JavacTool compiler = (JavacTool) ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnosticCollector =
        new DiagnosticCollector<JavaFileObject>();
    InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(
        compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
    JavacTask task = compiler.getTask(
        null, // explicitly use the default because old versions of javac log some output on stderr
        fileManager,
        diagnosticCollector,
        ImmutableSet.<String>of(),
        ImmutableSet.<String>of(),
        sources);
    try {
      Iterable<? extends CompilationUnitTree> parsedCompilationUnits = task.parse();
      List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
      for (Diagnostic<?> diagnostic : diagnostics) {
        if (Diagnostic.Kind.ERROR == diagnostic.getKind()) {
          throw new IllegalStateException("error while parsing:\n"
              + Diagnostics.toString(diagnostics));
        }
      }
      return new ParseResult(sortDiagnosticsByKind(diagnostics), parsedCompilationUnits,
          Trees.instance(task));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
      sortDiagnosticsByKind(Iterable<Diagnostic<? extends JavaFileObject>> diagnostics) {
    return Multimaps.index(diagnostics,
        new Function<Diagnostic<?>, Diagnostic.Kind>() {
          @Override public Diagnostic.Kind apply(Diagnostic<?> input) {
            return input.getKind();
          }
        });
  }

  /**
   * The diagnostic, parse trees, and {@link Trees} instance for a parse task.
   *
   * <p>Note: It is possible for the {@link Trees} instance contained within a {@code ParseResult}
   * to be invalidated by a call to {@link com.sun.tools.javac.api.JavacTaskImpl#cleanup()}. Though
   * we do not currently expose the {@link JavacTask} used to create a {@code ParseResult} to
   * {@code cleanup()} calls on its underlying implementation, this should be acknowledged as an
   * implementation detail that could cause unexpected behavior when making calls to methods in
   * {@link Trees}.
   */
  static class ParseResult {
    private final ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
        diagnostics;
    private final ImmutableList<? extends CompilationUnitTree> compilationUnits;
    private final Trees trees;

    ParseResult(
        ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>> diagnostics,
        Iterable<? extends CompilationUnitTree> compilationUnits, Trees trees) {
      this.trees = trees;
      this.compilationUnits = ImmutableList.copyOf(compilationUnits);
      this.diagnostics = diagnostics;
    }

    ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
        diagnosticsByKind() {
      return diagnostics;
    }

    Iterable<? extends CompilationUnitTree> compilationUnits() {
      return compilationUnits;
    }

    Trees trees() {
      return trees;
    }
  }

  /** The diagnostic and file output of a compilation. */
  static final class Result extends ParseResult {
    private final boolean successful;
    private final ImmutableListMultimap<JavaFileObject.Kind, JavaFileObject> generatedFilesByKind;

    Result(boolean successful,
        ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>> diagnostics,
        Iterable<? extends CompilationUnitTree> compilationUnits, Trees trees,
        Iterable<? extends JavaFileObject> generatedFiles) {
      super(diagnostics, compilationUnits, trees);
      this.successful = successful;
      this.generatedFilesByKind = Multimaps.index(
          ImmutableList.copyOf(generatedFiles),
          new Function<JavaFileObject, JavaFileObject.Kind>() {
            @Override public JavaFileObject.Kind apply(JavaFileObject input) {
              return input.getKind();
            }
          });
      if (!successful && diagnostics.get(Diagnostic.Kind.ERROR).isEmpty()) {
        throw new CompilationFailureException();
      }
    }

    boolean successful() {
      return successful;
    }

    ImmutableListMultimap<JavaFileObject.Kind, JavaFileObject> generatedFilesByKind() {
      return generatedFilesByKind;
    }

    ImmutableList<JavaFileObject> generatedSources() {
      return generatedFilesByKind.get(SOURCE);
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(this)
          .add("successful", successful)
          .add("diagnostics", diagnosticsByKind())
          .toString();
    }
  }
}
