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

import static com.google.common.truth.ExpectFailure.assertThat;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.truth.ExpectFailure;
import java.util.Arrays;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests {@link JavaSourcesSubjectFactory} (and {@link JavaSourceSubjectFactory}).
 *
 * @author Gregory Kick
 */
@RunWith(JUnit4.class)
public class JavaSourcesSubjectFactoryTest {

  private static final JavaFileObject HELLO_WORLD_RESOURCE =
      JavaFileObjects.forResource("test/HelloWorld.java");

  private static final JavaFileObject HELLO_WORLD =
      JavaFileObjects.forSourceLines(
          "test.HelloWorld",
          "package test;",
          "",
          "import " + DiagnosticMessage.class.getCanonicalName() + ";",
          "",
          "@DiagnosticMessage",
          "public class HelloWorld {",
          "  @DiagnosticMessage Object foo;",
          "}");

  private static final JavaFileObject HELLO_WORLD_BROKEN =
      JavaFileObjects.forSourceLines(
          "test.HelloWorld",
          "package test;",
          "",
          "import " + DiagnosticMessage.class.getCanonicalName() + ";",
          "",
          "@DiagnosticMessage",
          "public class HelloWorld {",
          "  @DiagnosticMessage Object foo;",
          "  Bar noSuchClass;",
          "}");

  @Rule public final ExpectFailure expectFailure = new ExpectFailure();

  @Test
  public void compilesWithoutError() {
    assertAbout(javaSource()).that(HELLO_WORLD_RESOURCE).compilesWithoutError();
    assertAbout(javaSource())
        .that(
            JavaFileObjects.forSourceLines(
                "test.HelloWorld",
                "package test;",
                "",
                "public class HelloWorld {",
                "  public static void main(String[] args) {",
                "    System.out.println(\"Hello World!\");",
                "  }",
                "}"))
        .compilesWithoutError();
  }

  @Test
  public void compilesWithoutWarnings() {
    assertAbout(javaSource()).that(HELLO_WORLD).compilesWithoutWarnings();
  }

  @Test
  public void compilesWithoutError_warnings() {
    assertAbout(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .compilesWithoutError()
        .withWarningContaining("this is a message")
        .in(HELLO_WORLD)
        .onLine(6)
        .atColumn(8)
        .and()
        .withWarningContaining("this is a message")
        .in(HELLO_WORLD)
        .onLine(7)
        .atColumn(29);
  }

  @Test
  public void compilesWithoutWarnings_failsWithWarnings() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .compilesWithoutWarnings();
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected 0 warnings, but found the following 2 warnings:\n");
  }

  @Test
  public void compilesWithoutError_noWarning() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .compilesWithoutError()
        .withWarningContaining("what is it?");
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected a warning containing \"what is it?\", but only found:\n");
    // some versions of javac wedge the file and position in the middle
    assertThat(expected).hasMessageThat().contains("this is a message\n");
  }

  @Test
  public void compilesWithoutError_warningNotInFile() {
    JavaFileObject otherSource = HELLO_WORLD_RESOURCE;
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .compilesWithoutError()
        .withWarningContaining("this is a message")
        .in(otherSource);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a warning containing \"this is a message\" in %s",
                otherSource.getName()));
    assertThat(expected.getMessage()).contains(HELLO_WORLD.getName());
  }

  @Test
  public void compilesWithoutError_warningNotOnLine() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .compilesWithoutError()
        .withWarningContaining("this is a message")
        .in(HELLO_WORLD)
        .onLine(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorLine = 6;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a warning containing \"this is a message\" in %s on line:\n   1: ",
                HELLO_WORLD.getName()));
    assertThat(expected.getMessage()).contains("" + actualErrorLine);
  }

  @Test
  public void compilesWithoutError_warningNotAtColumn() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .compilesWithoutError()
        .withWarningContaining("this is a message")
        .in(HELLO_WORLD)
        .onLine(6)
        .atColumn(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorCol = 8;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a warning containing \"this is a message\" in %s at column 1 of line 6",
                HELLO_WORLD.getName()));
    assertThat(expected.getMessage()).contains("[" + actualErrorCol + "]");
  }

  @Test
  public void compilesWithoutError_wrongWarningCount() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .compilesWithoutError()
        .withWarningCount(42);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected 42 warnings, but found the following 2 warnings:\n");
  }

  @Test
  public void compilesWithoutError_notes() {
    assertAbout(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .compilesWithoutError()
        .withNoteContaining("this is a message")
        .in(HELLO_WORLD)
        .onLine(6)
        .atColumn(8)
        .and()
        .withNoteContaining("this is a message")
        .in(HELLO_WORLD)
        .onLine(7)
        .atColumn(29)
        .and()
        .withNoteCount(2);
  }

  @Test
  public void compilesWithoutError_noNote() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .compilesWithoutError()
        .withNoteContaining("what is it?");
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected a note containing \"what is it?\", but only found:\n");
    // some versions of javac wedge the file and position in the middle
    assertThat(expected).hasMessageThat().contains("this is a message\n");
  }

  @Test
  public void compilesWithoutError_noteNotInFile() {
    JavaFileObject otherSource = HELLO_WORLD_RESOURCE;
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .compilesWithoutError()
        .withNoteContaining("this is a message")
        .in(otherSource);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a note containing \"this is a message\" in %s", otherSource.getName()));
    assertThat(expected.getMessage()).contains(HELLO_WORLD.getName());
  }

  @Test
  public void compilesWithoutError_noteNotOnLine() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .compilesWithoutError()
        .withNoteContaining("this is a message")
        .in(HELLO_WORLD)
        .onLine(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorLine = 6;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a note containing \"this is a message\" in %s on line:\n   1: ",
                HELLO_WORLD.getName()));
    assertThat(expected.getMessage()).contains("" + actualErrorLine);
  }

  @Test
  public void compilesWithoutError_noteNotAtColumn() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .compilesWithoutError()
        .withNoteContaining("this is a message")
        .in(HELLO_WORLD)
        .onLine(6)
        .atColumn(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorCol = 8;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a note containing \"this is a message\" in %s at column 1 of line 6",
                HELLO_WORLD.getName()));
    assertThat(expected.getMessage()).contains("[" + actualErrorCol + "]");
  }

  @Test
  public void compilesWithoutError_wrongNoteCount() {
    JavaFileObject fileObject = HELLO_WORLD;
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(fileObject)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .compilesWithoutError()
        .withNoteCount(42);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected 42 notes, but found the following 2 notes:\n");
  }

  @Test
  public void compilesWithoutError_failureReportsFiles() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new FailingGeneratingProcessor())
        .compilesWithoutError();
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage()).contains("Compilation produced the following diagnostics:\n");
    assertThat(expected.getMessage()).contains(FailingGeneratingProcessor.GENERATED_CLASS_NAME);
    assertThat(expected.getMessage()).contains(FailingGeneratingProcessor.GENERATED_SOURCE);
  }

  @Test
  public void compilesWithoutError_throws() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(JavaFileObjects.forResource("test/HelloWorld-broken.java"))
        .compilesWithoutError();
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected)
        .hasMessageThat()
        .contains("Compilation produced the following" + " diagnostics:\n");
    assertThat(expected.getMessage()).contains("No files were generated.");
  }

  @Test
  public void compilesWithoutError_exceptionCreatedOrPassedThrough() {
    RuntimeException e = new RuntimeException();
    try {
      assertAbout(javaSource())
          .that(HELLO_WORLD_RESOURCE)
          .processedWith(new ThrowingProcessor(e))
          .compilesWithoutError();
      fail();
    } catch (CompilationFailureException expected) {
      // some old javacs don't pass through exceptions, so we create one
    } catch (RuntimeException expected) {
      // newer jdks throw a runtime exception whose cause is the original exception
      assertThat(expected.getCause()).isEqualTo(e);
    }
  }

  @Test
  public void parsesAs() {
    assertAbout(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .parsesAs(
            JavaFileObjects.forSourceLines(
                "test.HelloWorld",
                "package test;",
                "",
                "public class HelloWorld {",
                "  public static void main(String[] args) {",
                "    System.out.println(\"Hello World!\");",
                "  }",
                "}"));
  }

  @Test
  public void parsesAs_expectedFileFailsToParse() {
    try {
      assertAbout(javaSource())
          .that(HELLO_WORLD_RESOURCE)
          .parsesAs(JavaFileObjects.forResource("test/HelloWorld-broken.java"));
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).startsWith("Error while parsing *expected* source:\n");
    }
  }

  @Test
  public void parsesAs_actualFileFailsToParse() {
    try {
      assertAbout(javaSource())
          .that(JavaFileObjects.forResource("test/HelloWorld-broken.java"))
          .parsesAs(HELLO_WORLD_RESOURCE);
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).startsWith("Error while parsing *actual* source:\n");
    }
  }

  @Test
  public void failsToCompile_throws() {
    expectFailure.whenTesting().about(javaSource()).that(HELLO_WORLD_RESOURCE).failsToCompile();
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Compilation was expected to fail, but contained no errors");
    assertThat(expected.getMessage()).contains("No files were generated.");
  }

  @Test
  public void failsToCompile_throwsNoMessage() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new ErrorProcessor())
        .failsToCompile()
        .withErrorContaining("some error");
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected an error containing \"some error\", but only found:\n");
    // some versions of javac wedge the file and position in the middle
    assertThat(expected).hasMessageThat().contains("expected error!\n");
  }

  @Test
  public void failsToCompile_throwsNotInFile() {
    JavaFileObject fileObject = HELLO_WORLD_RESOURCE;
    JavaFileObject otherFileObject = JavaFileObjects.forResource("test/HelloWorld-different.java");
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(fileObject)
        .processedWith(new ErrorProcessor())
        .failsToCompile()
        .withErrorContaining("expected error!")
        .in(otherFileObject);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected an error containing \"expected error!\" in %s",
                otherFileObject.getName()));
    assertThat(expected.getMessage()).contains(fileObject.getName());
  }

  @Test
  public void failsToCompile_throwsNotOnLine() {
    JavaFileObject fileObject = HELLO_WORLD_RESOURCE;
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(fileObject)
        .processedWith(new ErrorProcessor())
        .failsToCompile()
        .withErrorContaining("expected error!")
        .in(fileObject)
        .onLine(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorLine = 18;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected an error containing \"expected error!\" in %s on line:\n   1: ",
                fileObject.getName()));
    assertThat(expected.getMessage()).contains("" + actualErrorLine);
  }

  @Test
  public void failsToCompile_throwsNotAtColumn() {
    JavaFileObject fileObject = HELLO_WORLD_RESOURCE;
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(fileObject)
        .processedWith(new ErrorProcessor())
        .failsToCompile()
        .withErrorContaining("expected error!")
        .in(fileObject)
        .onLine(18)
        .atColumn(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorCol = 8;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected an error containing \"expected error!\" in %s at column 1 of line 18",
                fileObject.getName()));
    assertThat(expected.getMessage()).contains("" + actualErrorCol);
  }

  @Test
  public void failsToCompile_wrongErrorCount() {
    JavaFileObject fileObject = HELLO_WORLD_RESOURCE;
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(fileObject)
        .processedWith(new ErrorProcessor())
        .failsToCompile()
        .withErrorCount(42);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected 42 errors, but found the following 2 errors:\n");
  }

  @Test
  public void failsToCompile_noWarning() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .failsToCompile()
        .withWarningContaining("what is it?");
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected a warning containing \"what is it?\", but only found:\n");
    // some versions of javac wedge the file and position in the middle
    assertThat(expected).hasMessageThat().contains("this is a message\n");
  }

  @Test
  public void failsToCompile_warningNotInFile() {
    JavaFileObject otherSource = HELLO_WORLD_RESOURCE;
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .failsToCompile()
        .withWarningContaining("this is a message")
        .in(otherSource);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a warning containing \"this is a message\" in %s",
                otherSource.getName()));
    assertThat(expected.getMessage()).contains(HELLO_WORLD_BROKEN.getName());
  }

  @Test
  public void failsToCompile_warningNotOnLine() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .failsToCompile()
        .withWarningContaining("this is a message")
        .in(HELLO_WORLD_BROKEN)
        .onLine(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorLine = 6;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a warning containing \"this is a message\" in %s on line:\n   1: ",
                HELLO_WORLD_BROKEN.getName()));
    assertThat(expected.getMessage()).contains("" + actualErrorLine);
  }

  @Test
  public void failsToCompile_warningNotAtColumn() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .failsToCompile()
        .withWarningContaining("this is a message")
        .in(HELLO_WORLD_BROKEN)
        .onLine(6)
        .atColumn(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorCol = 8;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a warning containing \"this is a message\" in %s at column 1 of line 6",
                HELLO_WORLD_BROKEN.getName()));
    assertThat(expected.getMessage()).contains("[" + actualErrorCol + "]");
  }

  @Test
  public void failsToCompile_wrongWarningCount() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING))
        .failsToCompile()
        .withWarningCount(42);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected 42 warnings, but found the following 2 warnings:\n");
  }

  @Test
  public void failsToCompile_noNote() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .failsToCompile()
        .withNoteContaining("what is it?");
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected a note containing \"what is it?\", but only found:\n");
    // some versions of javac wedge the file and position in the middle
    assertThat(expected).hasMessageThat().contains("this is a message\n");
  }

  @Test
  public void failsToCompile_noteNotInFile() {
    JavaFileObject otherSource = HELLO_WORLD_RESOURCE;
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .failsToCompile()
        .withNoteContaining("this is a message")
        .in(otherSource);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a note containing \"this is a message\" in %s", otherSource.getName()));
    assertThat(expected.getMessage()).contains(HELLO_WORLD_BROKEN.getName());
  }

  @Test
  public void failsToCompile_noteNotOnLine() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .failsToCompile()
        .withNoteContaining("this is a message")
        .in(HELLO_WORLD_BROKEN)
        .onLine(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorLine = 6;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a note containing \"this is a message\" in %s on line:\n   1: ",
                HELLO_WORLD_BROKEN.getName()));
    assertThat(expected.getMessage()).contains("" + actualErrorLine);
  }

  @Test
  public void failsToCompile_noteNotAtColumn() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .failsToCompile()
        .withNoteContaining("this is a message")
        .in(HELLO_WORLD_BROKEN)
        .onLine(6)
        .atColumn(1);
    AssertionError expected = expectFailure.getFailure();
    int actualErrorCol = 8;
    assertThat(expected.getMessage())
        .contains(
            String.format(
                "Expected a note containing \"this is a message\" in %s at column 1 of line 6",
                HELLO_WORLD_BROKEN.getName()));
    assertThat(expected.getMessage()).contains("[" + actualErrorCol + "]");
  }

  @Test
  public void failsToCompile_wrongNoteCount() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_BROKEN)
        .processedWith(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE))
        .failsToCompile()
        .withNoteCount(42);
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Expected 42 notes, but found the following 2 notes:\n");
  }

  @Test
  public void failsToCompile() {
    JavaFileObject brokenFileObject = JavaFileObjects.forResource("test/HelloWorld-broken.java");
    assertAbout(javaSource())
        .that(brokenFileObject)
        .failsToCompile()
        .withErrorContaining("not a statement")
        .in(brokenFileObject)
        .onLine(23)
        .atColumn(5)
        .and()
        .withErrorCount(4);

    JavaFileObject happyFileObject = HELLO_WORLD_RESOURCE;
    assertAbout(javaSource())
        .that(happyFileObject)
        .processedWith(new ErrorProcessor())
        .failsToCompile()
        .withErrorContaining("expected error!")
        .in(happyFileObject)
        .onLine(18)
        .atColumn(8);
  }

  @Test
  public void generatesSources() {
    assertAbout(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forSourceString(
                GeneratingProcessor.GENERATED_CLASS_NAME, GeneratingProcessor.GENERATED_SOURCE));
  }

  @Test
  public void generatesSources_packageInfo() {
    GeneratingProcessor generatingProcessor = new GeneratingProcessor("test.generated");
    assertAbout(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(generatingProcessor)
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forSourceString(
                "test.generated.package-info", generatingProcessor.generatedPackageInfoSource()));
  }

  @Test
  public void generatesSources_failOnUnexpected() {
    String failingExpectationSource = "abstract class Blah {}";
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forSourceString(
                GeneratingProcessor.GENERATED_CLASS_NAME, failingExpectationSource));
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage()).contains("didn't match exactly");
    assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
    assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_SOURCE);
  }

  @Test
  public void generatesSources_failOnExtraExpected() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forSourceLines(
                GeneratingProcessor.GENERATED_CLASS_NAME,
                "import java.util.List;  // Extra import",
                "final class Blah {",
                "   String blah = \"blah\";",
                "}"));
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage()).contains("didn't match exactly");
    assertThat(expected.getMessage()).contains("unmatched nodes in the expected tree");
    assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
    assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_SOURCE);
  }

  @Test
  public void generatesSources_failOnExtraActual() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forSourceLines(
                GeneratingProcessor.GENERATED_CLASS_NAME,
                "final class Blah {",
                "  // missing field",
                "}"));
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage()).contains("didn't match exactly");
    assertThat(expected.getMessage()).contains("unmatched nodes in the actual tree");
    assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
    assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_SOURCE);
  }

  @Test
  public void generatesSources_failWithNoCandidates() {
    String failingExpectationName = "ThisIsNotTheRightFile";
    String failingExpectationSource = "abstract class ThisIsNotTheRightFile {}";
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forSourceString(failingExpectationName, failingExpectationSource));
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage()).contains("top-level types that were not present");
    assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
    assertThat(expected.getMessage()).contains(failingExpectationName);
  }

  @Test
  public void generatesSources_failWithNoGeneratedSources() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new NonGeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(
            JavaFileObjects.forSourceString(
                GeneratingProcessor.GENERATED_CLASS_NAME, GeneratingProcessor.GENERATED_SOURCE));
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage())
        .contains("Compilation generated no additional source files, though some were expected.");
  }

  @Test
  public void generatesFileNamed() {
    assertAbout(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesFileNamed(CLASS_OUTPUT, "com.google.testing.compile", "Foo")
        .withContents(ByteSource.wrap("Bar".getBytes(UTF_8)));
  }

  @Test
  public void generatesFileNamed_failOnFileExistence() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesFileNamed(CLASS_OUTPUT, "com.google.testing.compile", "Bogus")
        .withContents(ByteSource.wrap("Bar".getBytes(UTF_8)));
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected)
        .factValue("expected to generate file")
        .isEqualTo("/com/google/testing/compile/Bogus");
    assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_RESOURCE_NAME);
  }

  @Test
  public void generatesFileNamed_failOnFileContents() {
    expectFailure
        .whenTesting()
        .about(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesFileNamed(CLASS_OUTPUT, "com.google.testing.compile", "Foo")
        .withContents(ByteSource.wrap("Bogus".getBytes(UTF_8)));
    AssertionError expected = expectFailure.getFailure();
    assertThat(expected.getMessage()).contains("Foo");
    assertThat(expected.getMessage()).contains(" have contents");
  }

  @Test
  public void withStringContents() {
    assertAbout(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesFileNamed(CLASS_OUTPUT, "com.google.testing.compile", "Foo")
        .withStringContents(UTF_8, "Bar");
  }

  @Test
  public void passesOptions() {
    NoOpProcessor processor = new NoOpProcessor();
    assertAbout(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .withCompilerOptions("-Aa=1")
        .withCompilerOptions(ImmutableList.of("-Ab=2", "-Ac=3"))
        .processedWith(processor)
        .compilesWithoutError();
    assertThat(processor.options).containsEntry("a", "1");
    assertThat(processor.options).containsEntry("b", "2");
    assertThat(processor.options).containsEntry("c", "3");
    assertThat(processor.options).hasSize(3);
  }

  @Test
  public void invokesMultipleProcesors() {
    NoOpProcessor noopProcessor1 = new NoOpProcessor();
    NoOpProcessor noopProcessor2 = new NoOpProcessor();
    assertThat(noopProcessor1.invoked).isFalse();
    assertThat(noopProcessor2.invoked).isFalse();
    assertAbout(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(noopProcessor1, noopProcessor2)
        .compilesWithoutError();
    assertThat(noopProcessor1.invoked).isTrue();
    assertThat(noopProcessor2.invoked).isTrue();
  }

  @Test
  public void invokesMultipleProcesors_asIterable() {
    NoOpProcessor noopProcessor1 = new NoOpProcessor();
    NoOpProcessor noopProcessor2 = new NoOpProcessor();
    assertThat(noopProcessor1.invoked).isFalse();
    assertThat(noopProcessor2.invoked).isFalse();
    assertAbout(javaSource())
        .that(HELLO_WORLD_RESOURCE)
        .processedWith(Arrays.asList(noopProcessor1, noopProcessor2))
        .compilesWithoutError();
    assertThat(noopProcessor1.invoked).isTrue();
    assertThat(noopProcessor2.invoked).isTrue();
  }
}
