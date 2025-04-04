/*
 * Copyright (C) 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.testing.compile;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.Diagnostic.Kind.ERROR;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.parser.JavacParser;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

/** Methods to parse Java source files. */
final class Parser {

  /**
   * Parses {@code sources} into {@linkplain CompilationUnitTree compilation units}. This method
   * <b>does not</b> compile the sources.
   *
   * @param sourcesDescription describes the sources. Parsing exceptions will contain this string.
   * @throws IllegalStateException if any parsing errors occur.
   */
  static ParseResult parse(Iterable<? extends JavaFileObject> sources, String sourcesDescription) {
    DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
    Context context = new Context();
    context.put(DiagnosticListener.class, diagnosticCollector);
    Log log = Log.instance(context);
    // The constructor registers the instance in the Context
    JavacFileManager unused = new JavacFileManager(context, true, UTF_8);
    ParserFactory parserFactory = ParserFactory.instance(context);
    try {
      List<CompilationUnitTree> parsedCompilationUnits = new ArrayList<>();
      for (JavaFileObject source : sources) {
        log.useSource(source);
        JavacParser parser =
            parserFactory.newParser(
                source.getCharContent(false),
                /* keepDocComments= */ true,
                /* keepEndPos= */ true,
                /* keepLineMap= */ true);
        JCCompilationUnit unit = parser.parseCompilationUnit();
        unit.sourcefile = source;
        parsedCompilationUnits.add(unit);
      }
      List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
      if (foundParseErrors(diagnostics)) {
        String msgPrefix = String.format("Error while parsing %s:\n", sourcesDescription);
        throw new IllegalStateException(msgPrefix + Joiner.on('\n').join(diagnostics));
      }
      return new ParseResult(
          sortDiagnosticsByKind(diagnostics), parsedCompilationUnits, JavacTrees.instance(context));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Returns {@code true} if errors were found while parsing source files. */
  private static boolean foundParseErrors(List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    return diagnostics.stream().anyMatch(d -> d.getKind().equals(ERROR));
  }

  private static ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
      sortDiagnosticsByKind(Iterable<Diagnostic<? extends JavaFileObject>> diagnostics) {
    return Multimaps.index(diagnostics, input -> input.getKind());
  }

  /**
   * The diagnostic, parse trees, and {@link Trees} instance for a parse task.
   *
   * <p>Note: It is possible for the {@link Trees} instance contained within a {@code ParseResult}
   * to be invalidated by a call to {@link com.sun.tools.javac.api.JavacTaskImpl#cleanup()}. Though
   * we do not currently expose the {@link JavacTask} used to create a {@code ParseResult} to {@code
   * cleanup()} calls on its underlying implementation, this should be acknowledged as an
   * implementation detail that could cause unexpected behavior when making calls to methods in
   * {@link Trees}.
   */
  static final class ParseResult {
    private final ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
        diagnostics;
    private final ImmutableList<? extends CompilationUnitTree> compilationUnits;
    private final Trees trees;

    ParseResult(
        ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>> diagnostics,
        Iterable<? extends CompilationUnitTree> compilationUnits,
        Trees trees) {
      this.trees = trees;
      this.compilationUnits = ImmutableList.copyOf(compilationUnits);
      this.diagnostics = diagnostics;
    }

    ImmutableListMultimap<Diagnostic.Kind, Diagnostic<? extends JavaFileObject>>
        diagnosticsByKind() {
      return diagnostics;
    }

    ImmutableList<? extends CompilationUnitTree> compilationUnits() {
      return compilationUnits;
    }

    Trees trees() {
      return trees;
    }
  }

  private Parser() {}
}
