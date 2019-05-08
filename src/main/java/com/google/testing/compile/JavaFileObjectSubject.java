/*
 * Copyright (C) 2016 Google, Inc.
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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Fact.fact;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaFileObjects.asByteSource;
import static com.google.testing.compile.TreeDiffer.diffCompilationUnits;
import static com.google.testing.compile.TreeDiffer.matchCompilationUnits;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.testing.compile.Parser.ParseResult;
import com.sun.source.tree.CompilationUnitTree;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.function.BiFunction;
import javax.annotation.Nullable;
import javax.tools.JavaFileObject;

/** Assertions about {@link JavaFileObject}s. */
public final class JavaFileObjectSubject extends Subject<JavaFileObjectSubject, JavaFileObject> {

  private static final Subject.Factory<JavaFileObjectSubject, JavaFileObject> FACTORY =
      new JavaFileObjectSubjectFactory();

  /** Returns a {@link Subject.Factory} for {@link JavaFileObjectSubject}s. */
  public static Subject.Factory<JavaFileObjectSubject, JavaFileObject> javaFileObjects() {
    return FACTORY;
  }

  /** Starts making assertions about a {@link JavaFileObject}. */
  public static JavaFileObjectSubject assertThat(JavaFileObject actual) {
    return assertAbout(FACTORY).that(actual);
  }

  JavaFileObjectSubject(FailureMetadata failureMetadata, JavaFileObject actual) {
    super(failureMetadata, actual);
  }

  @Override
  protected String actualCustomStringRepresentation() {
    return actual().toUri().getPath();
  }

  /**
   * If {@code other} is a {@link JavaFileObject}, tests that their contents are equal. Otherwise
   * uses {@link Object#equals(Object)}.
   */
  @Override
  public void isEqualTo(@Nullable Object other) {
    if (!(other instanceof JavaFileObject)) {
      super.isEqualTo(other);
    }

    JavaFileObject otherFile = (JavaFileObject) other;
    try {
      if (!asByteSource(actual()).contentEquals(asByteSource(otherFile))) {
        failWithActual("expected to be equal to", other);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Asserts that the actual file's contents are equal to {@code expected}. */
  public void hasContents(ByteSource expected) {
    try {
      if (!asByteSource(actual()).contentEquals(expected)) {
        failWithActual("expected to have contents", expected);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a {@link StringSubject} that makes assertions about the contents of the actual file as
   * a string.
   */
  public StringSubject contentsAsString(Charset charset) {
    try {
      return check("contents()")
          .that(JavaFileObjects.asByteSource(actual()).asCharSource(charset).read());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns a {@link StringSubject} that makes assertions about the contents of the actual file as
   * a UTF-8 string.
   */
  public StringSubject contentsAsUtf8String() {
    return contentsAsString(UTF_8);
  }

  /**
   * Asserts that the actual file is a source file that has an equivalent <a
   * href="https://en.wikipedia.org/wiki/Abstract_syntax_tree">AST</a> to that of {@code
   * expectedSource}.
   */
  public void hasSourceEquivalentTo(JavaFileObject expectedSource) {
    performTreeDifference(
        expectedSource,
        "expected to be equivalent to",
        "expected",
        (expectedResult, actualResult) ->
            diffCompilationUnits(
                getOnlyElement(expectedResult.compilationUnits()),
                getOnlyElement(actualResult.compilationUnits())));
  }

  /**
   * Asserts that the every node in the <a
   * href="https://en.wikipedia.org/wiki/Abstract_syntax_tree">AST</a> of {@code expectedPattern}
   * exists in the actual file's AST, in the same order.
   *
   * <p>Methods, constructors, fields, and types that are in the pattern must have the exact same
   * modifiers and annotations as the actual AST. Ordering of AST nodes is also important (i.e. a
   * type with identical members in a different order will fail the assertion). Types must match the
   * entire type declaration: type parameters, {@code extends}/{@code implements} clauses, etc.
   * Methods must also match the throws clause as well.
   *
   * <p>The body of a method or constructor, or field initializer in the actual AST must match the
   * pattern in entirety if the member is present in the pattern.
   *
   * <p>Said in another way (from a graph-theoretic perspective): the pattern AST must be a subgraph
   * of the actual AST. If a method, constructor, or field is in the pattern, that entire subtree,
   * including modifiers and annotations, must be equal to the corresponding subtree in the actual
   * AST (no proper subgraphs).
   */
  public void containsElementsIn(JavaFileObject expectedPattern) {
    performTreeDifference(
        expectedPattern,
        "expected to contain elements in",
        "expected pattern",
        (expectedResult, actualResult) ->
            matchCompilationUnits(
                getOnlyElement(expectedResult.compilationUnits()),
                actualResult.trees(),
                getOnlyElement(actualResult.compilationUnits()),
                expectedResult.trees()));
  }

  private void performTreeDifference(
      JavaFileObject expected,
      String failureVerb,
      String expectedTitle,
      BiFunction<ParseResult, ParseResult, TreeDifference> differencingFunction) {
    ParseResult actualResult = Parser.parse(ImmutableList.of(actual()));
    CompilationUnitTree actualTree = getOnlyElement(actualResult.compilationUnits());

    ParseResult expectedResult = Parser.parse(ImmutableList.of(expected));
    CompilationUnitTree expectedTree = getOnlyElement(expectedResult.compilationUnits());

    TreeDifference treeDifference = differencingFunction.apply(expectedResult, actualResult);

    if (!treeDifference.isEmpty()) {
      String diffReport =
          treeDifference.getDiffReport(
              new TreeContext(expectedTree, expectedResult.trees()),
              new TreeContext(actualTree, actualResult.trees()));
      try {
        failWithoutActual(
            fact("for file", actual().toUri().getPath()),
            fact(failureVerb, expected.toUri().getPath()),
            fact("diff", diffReport),
            fact(expectedTitle, expected.getCharContent(false)),
            fact("but was", actual().getCharContent(false)));
      } catch (IOException e) {
        throw new IllegalStateException(
            "Couldn't read from JavaFileObject when it was already in memory.", e);
      }
    }
  }
}
