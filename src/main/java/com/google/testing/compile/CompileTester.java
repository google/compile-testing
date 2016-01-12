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

import com.google.common.io.ByteSource;

import java.nio.charset.Charset;

import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

/**
 * The root of the fluent API for testing the result of compilation.
 *
 * <p>This interface exists only to facilitate a fluent API and is subject to change. Implementing
 * this interface is not recommended.
 *
 * @author Gregory Kick
 */
public interface CompileTester {
  /** 
   * The clause in the fluent API that tests that the code parses equivalently to the specified 
   * code.
   */
  void parsesAs(JavaFileObject first, JavaFileObject... rest);
  
  /** The clause in the fluent API that tests for successful compilation without errors. */
  SuccessfulCompilationClause compilesWithoutError();

  /**
   * The clause in the fluent API that tests for successful compilation without warnings or
   * errors.
   */
  CleanCompilationClause compilesWithoutWarnings();

  /** The clause in the fluent API that tests for unsuccessful compilation. */
  UnsuccessfulCompilationClause failsToCompile();

  /**
   * The clause in the fluent API that allows for chaining test conditions.
   *
   * @param T the clause type returned by {@link #and()}
   */
  public interface ChainingClause<T> {
    T and();
  }

  /**
   * The clause in the fluent API that checks notes in a compilation.
   *
   * @param T the non-generic clause type implementing this interface
   */
  public interface CompilationWithNotesClause<T> {
    /**
     * Checks that a note exists that contains the given fragment in the
     * {@linkplain Diagnostic#getMessage(java.util.Locale) diagnostic message}.
     */
    FileClause<T> withNoteContaining(String messageFragment);

    /**
     * Checks that the total note count in all files matches the given amount. This only counts
     * diagnostics of the kind {@link Diagnostic.Kind#NOTE}.
     */
    T withNoteCount(int noteCount);
  }

  /**
   * The clause in the fluent API that checks notes and warnings in a compilation.
   *
   * @param T the non-generic clause type implementing this interface
   */
  public interface CompilationWithWarningsClause<T> extends CompilationWithNotesClause<T> {

    /**
     * Checks that a warning exists that contains the given fragment in the
     * {@linkplain Diagnostic#getMessage(java.util.Locale) diagnostic message}.
     */
    FileClause<T> withWarningContaining(String messageFragment);

    /**
     * Checks that the total warning count in all files matches the given amount. This only counts
     * diagnostics of the kind {@link Diagnostic.Kind#WARNING}.
     */
    T withWarningCount(int warningCount);
  }

  /**
   * The clause in the fluent API that checks that a diagnostic is associated with a particular
   * {@link JavaFileObject}.
   *
   * @param T the clause type returned by {@link ChainingClause#and()}
   */
  public interface FileClause<T> extends ChainingClause<T> {
    LineClause<T> in(JavaFileObject file);
  }

  /**
   * The clause in the fluent API that checks that a diagnostic is on a particular
   * {@linkplain Diagnostic#getLineNumber() line}.
   *
   * @param T the clause type returned by {@link ChainingClause#and()}
   */
  public interface LineClause<T> extends ChainingClause<T> {
    ColumnClause<T> onLine(long lineNumber);
  }

  /**
   * The clause in the fluent API that checks that a diagnostic starts at a particular
   * {@linkplain Diagnostic#getColumnNumber() column}.
   *
   * @param T the clause type returned by {@link ChainingClause#and()}
   */
  public interface ColumnClause<T> extends ChainingClause<T> {
    ChainingClause<T> atColumn(long columnNumber);
  }

  /**
   * The clause in the fluent API that checks that files were generated.
   *
   * @param T the non-generic clause type implementing this interface
   */
  public interface GeneratedPredicateClause<T> {
    /**
     * Checks that a source file with an equivalent
     * <a href="http://en.wikipedia.org/wiki/Abstract_syntax_tree">AST</a> was generated for each of
     * the given {@linkplain JavaFileObject files}.
     */
    T generatesSources(JavaFileObject first, JavaFileObject... rest);

    /**
     * Checks that a file with equivalent kind and content was generated for each of the given
     * {@linkplain JavaFileObject files}.
     */
    T generatesFiles(JavaFileObject first, JavaFileObject... rest);

    /**
     * Checks that a file with the specified location, package, and filename was generated.
     */
    SuccessfulFileClause<T> generatesFileNamed(
        JavaFileManager.Location location, String packageName, String relativeName);
  }

  /**
   * The clause in the fluent API that checks that a generated file has the specified contents.
   *
   * @param T the non-generic clause type implementing this interface
   */
  public interface SuccessfulFileClause<T> extends ChainingClause<GeneratedPredicateClause<T>> {
    /**
     * Checks that the contents of the generated file match the contents of the specified
     * {@link ByteSource}.
     */
    SuccessfulFileClause<T> withContents(ByteSource expectedByteSource);

    /**
     * Checks that the contents of the generated file are equal to the specified string in the given
     * charset.
     */
    SuccessfulFileClause<T> withStringContents(Charset charset, String expectedString);
  }

  /** The clause in the fluent API for further tests on successful compilations. */
  public interface SuccessfulCompilationClause
      extends CompilationWithWarningsClause<SuccessfulCompilationClause>,
          ChainingClause<GeneratedPredicateClause<SuccessfulCompilationClause>> {}

  /** The clause in the fluent API for further tests on successful compilations without warnings. */
  public interface CleanCompilationClause
      extends CompilationWithNotesClause<CleanCompilationClause>,
          ChainingClause<GeneratedPredicateClause<CleanCompilationClause>> {}

  /** The clause in the fluent API for further tests on unsuccessful compilations. */
  public interface UnsuccessfulCompilationClause
      extends CompilationWithWarningsClause<UnsuccessfulCompilationClause> {
    /**
     * Checks that an error exists that contains the given fragment in the
     * {@linkplain Diagnostic#getMessage(java.util.Locale) diagnostic message}.
     */
    FileClause<UnsuccessfulCompilationClause> withErrorContaining(String messageFragment);

    /**
     * Checks that the total error count in all files matches the given amount. This only counts
     * diagnostics of the kind {@link Diagnostic.Kind#ERROR} and not (for example) warnings.
     */
    UnsuccessfulCompilationClause withErrorCount(int errorCount);
  }
}
