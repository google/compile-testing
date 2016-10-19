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
import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaFileObjects.asByteSource;
import static com.google.testing.compile.TreeDiffer.diffCompilationUnits;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.testing.compile.Parser.ParseResult;
import com.sun.source.tree.CompilationUnitTree;
import java.io.IOException;
import java.nio.charset.Charset;
import javax.annotation.Nullable;
import javax.tools.JavaFileObject;

/** Assertions about {@link JavaFileObject}s. */
public final class JavaFileObjectSubject extends Subject<JavaFileObjectSubject, JavaFileObject> {

  private static final SubjectFactory<JavaFileObjectSubject, JavaFileObject> FACTORY =
      new JavaFileObjectSubjectFactory();

  /** Returns a {@link SubjectFactory} for {@link JavaFileObjectSubject}s. */
  public static SubjectFactory<JavaFileObjectSubject, JavaFileObject> javaFileObjects() {
    return FACTORY;
  }

  /** Starts making assertions about a {@link JavaFileObject}. */
  public static JavaFileObjectSubject assertThat(JavaFileObject actual) {
    return assertAbout(FACTORY).that(actual);
  }

  JavaFileObjectSubject(FailureStrategy failureStrategy, JavaFileObject actual) {
    super(failureStrategy, actual);
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
        fail("is equal to", other);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /** Asserts that the actual file's contents are equal to {@code expected}. */
  public void hasContents(ByteSource expected) {
    try {
      if (!asByteSource(actual()).contentEquals(expected)) {
        fail("has contents", expected);
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
      return check()
          .that(JavaFileObjects.asByteSource(actual()).asCharSource(charset).read())
          .named("the contents of " + actualAsString());
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
   * Asserts that the actual file is a source file with contents equivalent to {@code
   * expectedSource}.
   */
  public void hasSourceEquivalentTo(JavaFileObject expectedSource) {
    ParseResult actualResult = Parser.parse(ImmutableList.of(actual()));
    CompilationUnitTree actualTree = getOnlyElement(actualResult.compilationUnits());

    ParseResult expectedResult = Parser.parse(ImmutableList.of(expectedSource));
    CompilationUnitTree expectedTree = getOnlyElement(expectedResult.compilationUnits());

    TreeDifference treeDifference = diffCompilationUnits(expectedTree, actualTree);

    if (!treeDifference.isEmpty()) {
      String diffReport =
          treeDifference.getDiffReport(
              new TreeContext(expectedTree, expectedResult.trees()),
              new TreeContext(actualTree, actualResult.trees()));
      try {
        fail(
            Joiner.on('\n')
                .join(
                    String.format("is equivalent to <%s>.", expectedSource.toUri().getPath()),
                    "",
                    "Diffs:",
                    "======",
                    "",
                    diffReport,
                    "",
                    "Expected Source:",
                    "================",
                    "",
                    expectedSource.getCharContent(false),
                    "",
                    "Actual Source:",
                    "==============",
                    "",
                    actual().getCharContent(false)));
      } catch (IOException e) {
        throw new IllegalStateException(
            "Couldn't read from JavaFileObject when it was already in memory.", e);
      }
    }
  }
}
