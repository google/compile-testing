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

import javax.tools.Diagnostic;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;

/**
 * The root of the fluent API for testing the result of compilation.
 *
 * <p>This interface exists only to faciliate a fluent API and is subject to change. Implementing
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
  
  /** The clause in the fluent API that tests for successful compilation. */
  SuccessfulCompilationClause compilesWithoutError();

  /** The clause in the fluent API that tests for unsuccessful compilation. */
  UnsuccessfulCompilationClause failsToCompile();

  /** The clause in the fluent API that allows for chaining test conditions. */
  public interface ChainingClause<T> {
    T and();
  }

  /**
   * The clause in the fluent API that checks that an error is associated with a particular
   * {@link JavaFileObject}.
   */
  public interface FileClause extends ChainingClause<UnsuccessfulCompilationClause> {
    LineClause in(JavaFileObject file);
  }

  /**
   * The clause in the fluent API that checks that an error is on a particular
   * {@linkplain Diagnostic#getLineNumber() line}.
   */
  public interface LineClause extends ChainingClause<UnsuccessfulCompilationClause> {
    ColumnClause onLine(long lineNumber);
  }

  /**
   * The clause in the fluent API that checks that an error starts at a particular
   * {@linkplain Diagnostic#getColumnNumber() column}.
   */
  public interface ColumnClause extends ChainingClause<UnsuccessfulCompilationClause> {
    ChainingClause<UnsuccessfulCompilationClause> atColumn(long columnNumber);
  }

  /** The clause in the fluent API that checks that files were generated. */
  public interface GeneratedPredicateClause {
    /**
     * Checks that a source file with an equivalent
     * <a href="http://en.wikipedia.org/wiki/Abstract_syntax_tree">AST</a> was generated for each of
     * the given {@linkplain JavaFileObject files}.
     */
    SuccessfulCompilationClause generatesSources(JavaFileObject first, JavaFileObject... rest);

    /**
     * Checks that a file with equivalent kind and content was generated for each of the given
     * {@linkplain JavaFileObject files}.
     */
    SuccessfulCompilationClause generatesFiles(JavaFileObject first, JavaFileObject... rest);

    /**
     * Checks that a file with the specified location, package, and filename was generated.
     */
    SuccessfulFileClause generatesFileNamed(
        JavaFileManager.Location location, String packageName, String relativeName);
  }

  /** The clause in the fluent API that checks that a generated file has the specified contents. */
  public interface SuccessfulFileClause extends ChainingClause<GeneratedPredicateClause> {
    /**
     * Checks that the contents of the generated file match the contents of the specified
     * {@link ByteSource}.
     */
    SuccessfulFileClause withContents(ByteSource expectedByteSource);
  }

  /** The clause in the fluent API for further tests on successful compilations. */
  public interface SuccessfulCompilationClause extends ChainingClause<GeneratedPredicateClause> {}

  /** The clause in the fluent API for further tests on unsuccessful compilations. */
  public interface UnsuccessfulCompilationClause {
    /**
     * Checks that an error exists that contains the given fragment in the
     * {@linkplain Diagnostic#getMessage(java.util.Locale) diagnostic message}.
     */
    FileClause withErrorContaining(String messageFragment);
  }
}
