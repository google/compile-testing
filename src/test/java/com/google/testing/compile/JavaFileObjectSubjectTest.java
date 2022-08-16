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

import static com.google.common.truth.ExpectFailure.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.JavaFileObjectSubject.assertThat;
import static com.google.testing.compile.JavaFileObjectSubject.javaFileObjects;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.truth.ExpectFailure;
import java.io.IOException;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JavaFileObjectSubjectTest {
  @Rule public final ExpectFailure expectFailure = new ExpectFailure();

  private static final JavaFileObject CLASS =
      JavaFileObjects.forSourceLines(
          "test.TestClass", //
          "package test;",
          "",
          "public class TestClass {}");

  private static final JavaFileObject DIFFERENT_NAME =
      JavaFileObjects.forSourceLines(
          "test.TestClass2", //
          "package test;",
          "",
          "public class TestClass2 {}");

  private static final JavaFileObject CLASS_WITH_FIELD =
      JavaFileObjects.forSourceLines(
          "test.TestClass", //
          "package test;",
          "",
          "public class TestClass {",
          "  Object field;",
          "}");

  private static final JavaFileObject UNKNOWN_TYPES =
      JavaFileObjects.forSourceLines(
          "test.TestClass",
          "package test;",
          "",
          "public class TestClass {",
          "  Bar badMethod(Baz baz) { return baz.what(); }",
          "}");

  @Test
  public void hasContents() {
    assertThat(CLASS_WITH_FIELD).hasContents(JavaFileObjects.asByteSource(CLASS_WITH_FIELD));
  }

  @Test
  public void hasContents_failure() {
    expectFailure
        .whenTesting()
        .about(javaFileObjects())
        .that(CLASS_WITH_FIELD)
        .hasContents(JavaFileObjects.asByteSource(DIFFERENT_NAME));
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage()).contains(CLASS_WITH_FIELD.getName());
  }

  @Test
  public void contentsAsString() {
    assertThat(CLASS_WITH_FIELD).contentsAsString(UTF_8).containsMatch("Object +field;");
  }

  @Test
  public void contentsAsString_fail() {
    expectFailure
        .whenTesting()
        .about(javaFileObjects())
        .that(CLASS)
        .contentsAsString(UTF_8)
        .containsMatch("bad+");
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected).factValue("value of").isEqualTo("javaFileObject.contents()");
    assertThat(expected).factValue("javaFileObject was").startsWith(CLASS.getName());
    assertThat(expected).factValue("expected to contain a match for").isEqualTo("bad+");
  }

  @Test
  public void hasSourceEquivalentTo() {
    assertThat(CLASS_WITH_FIELD).hasSourceEquivalentTo(CLASS_WITH_FIELD);
  }

  @Test
  public void hasSourceEquivalentTo_unresolvedReferences() {
    assertThat(UNKNOWN_TYPES).hasSourceEquivalentTo(UNKNOWN_TYPES);
  }

  @Test
  public void hasSourceEquivalentTo_failOnDifferences() throws IOException {
    expectFailure
        .whenTesting()
        .about(javaFileObjects())
        .that(CLASS)
        .hasSourceEquivalentTo(DIFFERENT_NAME);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected).factKeys().contains("expected to be equivalent to");
    assertThat(expected.getMessage()).contains(CLASS.getName());
    assertThat(expected).factValue("but was").isEqualTo(CLASS.getCharContent(false));
  }

  @Test
  public void hasSourceEquivalentTo_failOnExtraInExpected() throws IOException {
    expectFailure
        .whenTesting()
        .about(javaFileObjects())
        .that(CLASS)
        .hasSourceEquivalentTo(CLASS_WITH_FIELD);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected).factKeys().contains("expected to be equivalent to");
    assertThat(expected.getMessage()).contains("unmatched nodes in the expected tree");
    assertThat(expected.getMessage()).contains(CLASS.getName());
    assertThat(expected).factValue("but was").isEqualTo(CLASS.getCharContent(false));
  }

  @Test
  public void hasSourceEquivalentTo_failOnExtraInActual() throws IOException {
    expectFailure
        .whenTesting()
        .about(javaFileObjects())
        .that(CLASS_WITH_FIELD)
        .hasSourceEquivalentTo(CLASS);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected).factKeys().contains("expected to be equivalent to");
    assertThat(expected.getMessage()).contains("unmatched nodes in the actual tree");
    assertThat(expected.getMessage()).contains(CLASS_WITH_FIELD.getName());
    assertThat(expected).factValue("but was").isEqualTo(CLASS_WITH_FIELD.getCharContent(false));
  }

  private static final JavaFileObject SAMPLE_ACTUAL_FILE_FOR_MATCHING =
      JavaFileObjects.forSourceLines(
          "test.SomeFile",
          "package test;",
          "",
          "import pkg.AnAnnotation;",
          "import static another.something.Special.CONSTANT;",
          "",
          "@AnAnnotation(with = @Some(values = {1,2,3}), and = \"a string\")",
          "public class SomeFile {",
          "  private static final int CONSTANT_TIMES_2 = CONSTANT * 2;",
          "  private static final int CONSTANT_TIMES_3 = CONSTANT * 3;",
          "  private static final int CONSTANT_TIMES_4 = CONSTANT * 4;",
          "",
          "  @Nullable private MaybeNull field;",
          "",
          "  @Inject SomeFile() {",
          "    this.field = MaybeNull.constructorBody();",
          "  }",
          "",
          "  protected int method(Parameter p, OtherParam o) {",
          "    return CONSTANT_TIMES_4 / p.hashCode() + o.hashCode();",
          "  }",
          "",
          "  public static class InnerClass {",
          "    private static final int CONSTANT_TIMES_8 = CONSTANT_TIMES_4 * 2;",
          "",
          "    @Nullable private MaybeNull innerClassField;",
          "",
          "    @Inject",
          "    InnerClass() {",
          "      this.innerClassField = MaybeNull.constructorBody();",
          "    }",
          "",
          "    protected int innerClassMethod(Parameter p, OtherParam o) {",
          "      return CONSTANT_TIMES_8 / p.hashCode() + o.hashCode();",
          "    }",
          "  }",
          "}");

  @Test
  public void containsElementsIn_completeMatch() {
    assertThat(SAMPLE_ACTUAL_FILE_FOR_MATCHING).containsElementsIn(SAMPLE_ACTUAL_FILE_FOR_MATCHING);
  }

  private static final JavaFileObject SIMPLE_INVALID_FILE =
      JavaFileObjects.forSourceLines(
          "test.SomeClass", //
          "package test;",
          "",
          "public syntax error class SomeClass {",
          "}");
  private static final JavaFileObject SIMPLE_VALID_FILE =
      JavaFileObjects.forSourceLines(
          "test.SomeClass", //
          "package test;",
          "",
          "public class SomeClass {",
          "}");

  @Test
  public void containsElementsIn_badActual() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> assertThat(SIMPLE_INVALID_FILE).containsElementsIn(SIMPLE_VALID_FILE));

    assertThat(ex).hasMessageThat().startsWith("Error while parsing *actual* source:\n");
  }

  @Test
  public void containsElementsIn_badExpected() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> assertThat(SIMPLE_VALID_FILE).containsElementsIn(SIMPLE_INVALID_FILE));

    assertThat(ex).hasMessageThat().startsWith("Error while parsing *expected* source:\n");
  }
}
