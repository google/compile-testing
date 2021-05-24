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
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.CompilationSubject.compilations;
import static com.google.testing.compile.Compiler.javac;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.truth.ExpectFailure;
import com.google.common.truth.Truth;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Tests {@link CompilationSubject}. */
@RunWith(Enclosed.class)
public class CompilationSubjectTest {
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
          "  void weird() {",
          "    foo.toString();",
          "  }",
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

  private static final JavaFileObject HELLO_WORLD_RESOURCE =
      JavaFileObjects.forResource("test/HelloWorld.java");

  private static final JavaFileObject HELLO_WORLD_BROKEN_RESOURCE =
      JavaFileObjects.forResource("test/HelloWorld-broken.java");

  private static final JavaFileObject HELLO_WORLD_DIFFERENT_RESOURCE =
      JavaFileObjects.forResource("test/HelloWorld-different.java");

  @RunWith(JUnit4.class)
  public static class StatusTest {
    @Rule public final ExpectFailure expectFailure = new ExpectFailure();

    @Test
    public void succeeded() {
      assertThat(javac().compile(HELLO_WORLD)).succeeded();
      assertThat(javac().compile(HELLO_WORLD_RESOURCE)).succeeded();
    }

    @Test
    public void succeeded_failureReportsGeneratedFiles() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithGeneratorAndError().compile(HELLO_WORLD_RESOURCE))
          .succeeded();
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage()).contains(
          "Compilation produced the following diagnostics:\n");
      assertThat(expected.getMessage()).contains(FailingGeneratingProcessor.GENERATED_CLASS_NAME);
      assertThat(expected.getMessage()).contains(FailingGeneratingProcessor.GENERATED_SOURCE);
    }

    @Test
    public void succeeded_failureReportsNoGeneratedFiles() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(javac().compile(HELLO_WORLD_BROKEN_RESOURCE))
          .succeeded();
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage()).startsWith(
          "Compilation produced the following diagnostics:\n");
      assertThat(expected.getMessage()).contains("No files were generated.");
    }

    @Test
    public void succeeded_exceptionCreatedOrPassedThrough() {
      RuntimeException e = new RuntimeException();
      try {
        Truth.assertAbout(compilations())
            .that(throwingCompiler(e).compile(HELLO_WORLD_RESOURCE))
            .succeeded();
        fail();
      } catch (CompilationFailureException expected) {
        // some old javacs don't pass through exceptions, so we create one
      } catch (RuntimeException expected) {
        // newer jdks throw a runtime exception whose cause is the original exception
        assertThat(expected.getCause()).isEqualTo(e);
      }
    }

    @Test
    public void succeeded_failureReportsWarnings() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(HELLO_WORLD_BROKEN))
          .succeeded();
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith("Compilation produced the following diagnostics:\n");
      assertThat(expected.getMessage()).contains("No files were generated.");
      // "this is a message" is output by compilerWithWarning() since the source has
      // @DiagnosticMessage
      assertThat(expected.getMessage()).contains("warning: this is a message");
    }

    @Test
    public void succeededWithoutWarnings() {
      assertThat(javac().compile(HELLO_WORLD)).succeededWithoutWarnings();
    }

    @Test
    public void succeededWithoutWarnings_failsWithWarnings() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(HELLO_WORLD))
          .succeededWithoutWarnings();
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .contains("Expected 0 warnings, but found the following 2 warnings:\n");
    }

    @Test
    public void failedToCompile() {
      assertThat(javac().compile(HELLO_WORLD_BROKEN_RESOURCE)).failed();
    }

    @Test
    public void failedToCompile_compilationSucceeded() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(javac().compile(HELLO_WORLD_RESOURCE))
          .failed();
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith("Compilation was expected to fail, but contained no errors");
      assertThat(expected.getMessage()).contains("No files were generated.");
    }
  }

  /**
   * Tests for {@link CompilationSubject}'s assertions about warnings and notes, for both successful
   * and unsuccessful compilations.
   */
  @RunWith(Parameterized.class)
  public static final class WarningAndNoteTest {
    @Rule public final ExpectFailure expectFailure = new ExpectFailure();
    private final JavaFileObject sourceFile;

    @Parameters
    public static ImmutableList<Object[]> parameters() {
      return ImmutableList.copyOf(
          new Object[][] {
            {HELLO_WORLD}, {HELLO_WORLD_BROKEN},
          });
    }

    public WarningAndNoteTest(JavaFileObject sourceFile) {
      this.sourceFile = sourceFile;
    }

    @Test
    public void hadWarningContainingInFileOnLineAtColumn() {
      assertThat(compilerWithWarning().compile(sourceFile))
          .hadWarningContaining("this is a message")
          .inFile(sourceFile)
          .onLine(6)
          .atColumn(8);
      assertThat(compilerWithWarning().compile(sourceFile))
          .hadWarningContaining("this is a message")
          .inFile(sourceFile)
          .onLine(7)
          .atColumn(29);
    }

    /* TODO(dpb): Positive cases for onLineContaining for
     * (error, warning, note) x
     * (containing(String), containingMatch(String), containingMatch(Pattern)). */
    @Test
    public void hadWarningContainingInFileOnLineContaining() {
      assertThat(compilerWithWarning().compile(sourceFile))
          .hadWarningContaining("this is a message")
          .inFile(sourceFile)
          .onLineContaining("class HelloWorld");
      assertThat(compilerWithWarning().compile(sourceFile))
          .hadWarningContaining("this is a message")
          .inFile(sourceFile)
          .onLineContaining("Object foo");
    }

    @Test
    public void hadWarningContainingMatch() {
      assertThat(compilerWithWarning().compile(sourceFile))
          .hadWarningContainingMatch("this is a? message")
          .inFile(sourceFile)
          .onLine(6)
          .atColumn(8);
      assertThat(compilerWithWarning().compile(sourceFile))
          .hadWarningContainingMatch("(this|here) is a message")
          .inFile(sourceFile)
          .onLine(7)
          .atColumn(29);
    }

    @Test
    public void hadWarningContainingMatch_pattern() {
      assertThat(compilerWithWarning().compile(sourceFile))
          .hadWarningContainingMatch(Pattern.compile("this is a? message"))
          .inFile(sourceFile)
          .onLine(6)
          .atColumn(8);
      assertThat(compilerWithWarning().compile(sourceFile))
          .hadWarningContainingMatch(Pattern.compile("(this|here) is a message"))
          .inFile(sourceFile)
          .onLine(7)
          .atColumn(29);
    }

    @Test
    public void hadWarningContaining_noSuchWarning() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(sourceFile))
          .hadWarningContaining("what is it?");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith("Expected a warning containing \"what is it?\", but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("this is a message\n");
    }

    @Test
    public void hadWarningContainingMatch_noSuchWarning() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(sourceFile))
          .hadWarningContainingMatch("(what|where) is it?");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith(
              "Expected a warning containing match for /(what|where) is it?/, but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("this is a message\n");
    }

    @Test
    public void hadWarningContainingMatch_pattern_noSuchWarning() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(sourceFile))
          .hadWarningContainingMatch(Pattern.compile("(what|where) is it?"));
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith(
              "Expected a warning containing match for /(what|where) is it?/, but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("this is a message\n");
    }

    @Test
    public void hadWarningContainingInFile_wrongFile() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(sourceFile))
          .hadWarningContaining("this is a message")
          .inFile(HELLO_WORLD_DIFFERENT_RESOURCE);
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .contains(
              String.format(
                  "Expected a warning containing \"this is a message\" in %s",
                  HELLO_WORLD_DIFFERENT_RESOURCE.getName()));
      assertThat(expected.getMessage()).contains(sourceFile.getName());
    }

    @Test
    public void hadWarningContainingInFileOnLine_wrongLine() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(sourceFile))
          .hadWarningContaining("this is a message")
          .inFile(sourceFile)
          .onLine(1);
      AssertionError expected = expectFailure.getFailure();
      int actualErrorLine = 6;
      assertThat(expected.getMessage())
          .contains(
              lines(
                  format(
                      "Expected a warning containing \"this is a message\" in %s on line:",
                      sourceFile.getName()),
                  "   1: "));
      assertThat(expected.getMessage()).contains("" + actualErrorLine);
    }

    @Test
    public void hadWarningContainingInFileOnLine_lineTooBig() throws IOException {
      long lineCount = new BufferedReader(sourceFile.openReader(false)).lines().count();
      IllegalArgumentException exception =
          assertThrows(
              IllegalArgumentException.class,
              () ->
                  assertThat(compilerWithWarning().compile(sourceFile))
                      .hadWarningContainingMatch("this is a+ message")
                      .inFile(sourceFile)
                      .onLine(100));
      assertThat(exception)
          .hasMessageThat()
          .isEqualTo("Invalid line number 100; number of lines is only " + lineCount);
    }

    /* TODO(dpb): Negative cases for onLineContaining for
     * (warning, error, note) x
     * (containing(String), containingMatch(String), containingMatch(Pattern)). */
    @Test
    public void hadNoteContainingInFileOnLineContaining_wrongLine() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithNote().compile(sourceFile))
          .hadNoteContaining("this is a message")
          .inFile(sourceFile)
          .onLineContaining("package");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .isEqualTo(
              lines(
                  format(
                      "Expected a note containing \"this is a message\" in %s on line:",
                      sourceFile.getName()),
                  "   1: package test;",
                  "but found it on line(s):",
                  "   6: public class HelloWorld {",
                  "   7:   @DiagnosticMessage Object foo;"));
    }

    @Test
    public void hadWarningContainingMatchInFileOnLineContaining_noMatches() {
      try {
        Truth.assertAbout(compilations())
            .that(compilerWithWarning().compile(sourceFile))
            .hadWarningContainingMatch("this is a+ message")
            .inFile(sourceFile)
            .onLineContaining("not found!");
        fail();
      } catch (IllegalArgumentException expected) {
        assertThat(expected.getMessage())
            .isEqualTo(format("No line in %s contained \"not found!\"", sourceFile.getName()));
      }
    }

    @Test
    public void hadWarningContainingInFileOnLineContaining_moreThanOneMatch() {
      try {
        Truth.assertAbout(compilations())
            .that(compilerWithWarning().compile(sourceFile))
            .hadWarningContainingMatch(Pattern.compile("this is ab? message"))
            .inFile(sourceFile)
            .onLineContaining("@DiagnosticMessage");
        fail();
      } catch (IllegalArgumentException expected) {
        assertThat(expected.getMessage())
            .isEqualTo(
                lines(
                    format(
                        "More than one line in %s contained \"@DiagnosticMessage\":",
                        sourceFile.getName()),
                    "   5: @DiagnosticMessage",
                    "   7:   @DiagnosticMessage Object foo;"));
      }
    }

    @Test
    public void hadWarningContainingInFileOnLineAtColumn_wrongColumn() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(sourceFile))
          .hadWarningContaining("this is a message")
          .inFile(sourceFile)
          .onLine(6)
          .atColumn(1);
      AssertionError expected = expectFailure.getFailure();
      int actualErrorCol = 8;
      assertThat(expected.getMessage())
          .contains(
              format(
                  "Expected a warning containing \"this is a message\" in %s "
                      + "at column 1 of line 6",
                  sourceFile.getName()));
      assertThat(expected.getMessage()).contains("[" + actualErrorCol + "]");
    }

    @Test
    public void hadWarningCount() {
      assertThat(compilerWithWarning().compile(sourceFile)).hadWarningCount(2);
    }

    @Test
    public void hadWarningCount_wrongCount() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithWarning().compile(sourceFile))
          .hadWarningCount(42);
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .contains("Expected 42 warnings, but found the following 2 warnings:\n");
    }

    @Test
    public void hadNoteContaining() {
      assertThat(compilerWithNote().compile(sourceFile))
          .hadNoteContaining("this is a message")
          .inFile(sourceFile)
          .onLine(6)
          .atColumn(8);
      assertThat(compilerWithNote().compile(sourceFile))
          .hadNoteContaining("this is a message")
          .inFile(sourceFile)
          .onLine(7)
          .atColumn(29);
    }

    @Test
    public void hadNoteContainingMatch() {
      assertThat(compilerWithNote().compile(sourceFile))
          .hadNoteContainingMatch("this is a? message")
          .inFile(sourceFile)
          .onLine(6)
          .atColumn(8);
      assertThat(compilerWithNote().compile(sourceFile))
          .hadNoteContainingMatch("(this|here) is a message")
          .inFile(sourceFile)
          .onLine(7)
          .atColumn(29);
    }

    @Test
    public void hadNoteContainingMatch_pattern() {
      assertThat(compilerWithNote().compile(sourceFile))
          .hadNoteContainingMatch(Pattern.compile("this is a? message"))
          .inFile(sourceFile)
          .onLine(6)
          .atColumn(8);
      assertThat(compilerWithNote().compile(sourceFile))
          .hadNoteContainingMatch(Pattern.compile("(this|here) is a message"))
          .inFile(sourceFile)
          .onLine(7)
          .atColumn(29);
    }

    @Test
    public void hadNoteContaining_noSuchNote() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithNote().compile(sourceFile))
          .hadNoteContaining("what is it?");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith("Expected a note containing \"what is it?\", but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("this is a message\n");
    }

    @Test
    public void hadNoteContainingMatch_noSuchNote() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithNote().compile(sourceFile))
          .hadNoteContainingMatch("(what|where) is it?");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith(
              "Expected a note containing match for /(what|where) is it?/, but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("this is a message\n");
    }

    @Test
    public void hadNoteContainingMatch_pattern_noSuchNote() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithNote().compile(sourceFile))
          .hadNoteContainingMatch(Pattern.compile("(what|where) is it?"));
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith(
              "Expected a note containing match for /(what|where) is it?/, but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("this is a message\n");
    }

    @Test
    public void hadNoteContainingInFile_wrongFile() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithNote().compile(sourceFile))
          .hadNoteContaining("this is a message")
          .inFile(HELLO_WORLD_DIFFERENT_RESOURCE);
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .contains(
              format(
                  "Expected a note containing \"this is a message\" in %s",
                  HELLO_WORLD_DIFFERENT_RESOURCE.getName()));
      assertThat(expected.getMessage()).contains(sourceFile.getName());
    }

    @Test
    public void hadNoteContainingInFileOnLine_wrongLine() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithNote().compile(sourceFile))
          .hadNoteContaining("this is a message")
          .inFile(sourceFile)
          .onLine(1);
      AssertionError expected = expectFailure.getFailure();
      int actualErrorLine = 6;
      assertThat(expected.getMessage())
          .contains(
              lines(
                  format(
                      "Expected a note containing \"this is a message\" in %s on line:",
                      sourceFile.getName()),
                  "   1: "));
      assertThat(expected.getMessage()).contains("" + actualErrorLine);
    }

    @Test
    public void hadNoteContainingInFileOnLineAtColumn_wrongColumn() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithNote().compile(sourceFile))
          .hadNoteContaining("this is a message")
          .inFile(sourceFile)
          .onLine(6)
          .atColumn(1);
      AssertionError expected = expectFailure.getFailure();
      int actualErrorCol = 8;
      assertThat(expected.getMessage())
          .contains(
              format(
                  "Expected a note containing \"this is a message\" in %s at column 1 of line 6",
                  sourceFile.getName()));
      assertThat(expected.getMessage()).contains("[" + actualErrorCol + "]");
    }

    @Test
    public void hadNoteCount() {
      assertThat(compilerWithNote().compile(sourceFile)).hadNoteCount(2);
    }

    @Test
    public void hadNoteCount_wrongCount() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithNote().compile(sourceFile))
          .hadNoteCount(42);
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .contains("Expected 42 notes, but found the following 2 notes:\n");
    }
  }

  /** Tests for {@link CompilationSubject}'s assertions about errors. */
  @RunWith(JUnit4.class)
  public static final class ErrorTest {
    @Rule public final ExpectFailure expectFailure = new ExpectFailure();

    @Test
    public void hadErrorContaining() {
      assertThat(javac().compile(HELLO_WORLD_BROKEN_RESOURCE))
          .hadErrorContaining("not a statement")
          .inFile(HELLO_WORLD_BROKEN_RESOURCE)
          .onLine(23)
          .atColumn(5);
      assertThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContaining("expected error!")
          .inFile(HELLO_WORLD_RESOURCE)
          .onLine(18)
          .atColumn(8);
    }

    @Test
    public void hadErrorContainingMatch() {
      assertThat(compilerWithError().compile(HELLO_WORLD_BROKEN_RESOURCE))
          .hadErrorContainingMatch("not+ +a? statement")
          .inFile(HELLO_WORLD_BROKEN_RESOURCE)
          .onLine(23)
          .atColumn(5);
      assertThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContainingMatch("(wanted|expected) error!")
          .inFile(HELLO_WORLD_RESOURCE)
          .onLine(18)
          .atColumn(8);
    }

    @Test
    public void hadErrorContainingMatch_pattern() {
      assertThat(compilerWithError().compile(HELLO_WORLD_BROKEN_RESOURCE))
          .hadErrorContainingMatch("not+ +a? statement")
          .inFile(HELLO_WORLD_BROKEN_RESOURCE)
          .onLine(23)
          .atColumn(5);
      assertThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContainingMatch("(wanted|expected) error!")
          .inFile(HELLO_WORLD_RESOURCE)
          .onLine(18)
          .atColumn(8);
    }

    @Test
    public void hadErrorContaining_noSuchError() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContaining("some error");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith("Expected an error containing \"some error\", but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("expected error!\n");
    }

    @Test
    public void hadErrorContainingMatch_noSuchError() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContainingMatch("(what|where) is it?");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith(
              "Expected an error containing match for /(what|where) is it?/, but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("expected error!\n");
    }

    @Test
    public void hadErrorContainingMatch_pattern_noSuchError() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContainingMatch(Pattern.compile("(what|where) is it?"));
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .startsWith(
              "Expected an error containing match for /(what|where) is it?/, but only found:\n");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("expected error!\n");
    }

    @Test
    public void hadErrorContainingInFile_wrongFile() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContaining("expected error!")
          .inFile(HELLO_WORLD_DIFFERENT_RESOURCE);
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .contains(
              format(
                  "Expected an error containing \"expected error!\" in %s",
                  HELLO_WORLD_DIFFERENT_RESOURCE.getName()));
      assertThat(expected.getMessage()).contains(HELLO_WORLD_RESOURCE.getName());
      //                  "(no associated file)")));
    }

    @Test
    public void hadErrorContainingInFileOnLine_wrongLine() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContaining("expected error!")
          .inFile(HELLO_WORLD_RESOURCE)
          .onLine(1);
      AssertionError expected = expectFailure.getFailure();
      int actualErrorLine = 18;
      assertThat(expected.getMessage())
          .contains(
              lines(
                  format(
                      "Expected an error containing \"expected error!\" in %s on line:",
                      HELLO_WORLD_RESOURCE.getName()),
                  "   1: "));
      assertThat(expected.getMessage()).contains("" + actualErrorLine);
    }

    @Test
    public void hadErrorContainingInFileOnLineAtColumn_wrongColumn() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorContaining("expected error!")
          .inFile(HELLO_WORLD_RESOURCE)
          .onLine(18)
          .atColumn(1);
      AssertionError expected = expectFailure.getFailure();
      int actualErrorCol = 8;
      assertThat(expected.getMessage())
          .contains(
              format(
                  "Expected an error containing \"expected error!\" in %s at column 1 of line 18",
                  HELLO_WORLD_RESOURCE.getName()));
      assertThat(expected.getMessage()).contains("" + actualErrorCol);
    }

    @Test
    public void hadErrorCount() {
      assertThat(compilerWithError().compile(HELLO_WORLD_BROKEN_RESOURCE)).hadErrorCount(4);
    }

    @Test
    public void hadErrorCount_wrongCount() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithError().compile(HELLO_WORLD_RESOURCE))
          .hadErrorCount(42);
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected.getMessage())
          .contains("Expected 42 errors, but found the following 2 errors:\n");
    }
  }

  @RunWith(JUnit4.class)
  public static class GeneratedFilesTest {
    @Rule public final ExpectFailure expectFailure = new ExpectFailure();

    @Test
    public void generatedSourceFile() {
      assertThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedSourceFile(GeneratingProcessor.GENERATED_CLASS_NAME)
          .hasSourceEquivalentTo(
              JavaFileObjects.forSourceString(
                  GeneratingProcessor.GENERATED_CLASS_NAME, GeneratingProcessor.GENERATED_SOURCE));
    }

    @Test
    public void generatedSourceFile_packageInfo() {
      GeneratingProcessor generatingProcessor = new GeneratingProcessor("test");
      assertThat(javac().withProcessors(generatingProcessor).compile(HELLO_WORLD_RESOURCE))
          .generatedSourceFile("test.package-info")
          .hasSourceEquivalentTo(
              JavaFileObjects.forSourceString(
                  "test.package-info", generatingProcessor.generatedPackageInfoSource()));
    }

    @Test
    public void generatedSourceFile_fail() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedSourceFile("ThisIsNotTheRightFile");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected)
          .factValue("expected to generate file")
          .isEqualTo("/ThisIsNotTheRightFile.java");
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
    }

    @Test
    public void generatedFilePath() {
      assertThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedFile(CLASS_OUTPUT, "com/google/testing/compile/Foo")
          .hasContents(ByteSource.wrap("Bar".getBytes(UTF_8)));
    }

    @Test
    public void generatedFilePath_fail() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedFile(CLASS_OUTPUT, "com/google/testing/compile/Bogus.class");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected)
          .factValue("expected to generate file")
          .isEqualTo("/com/google/testing/compile/Bogus.class");
    }

    @Test
    public void generatedFilePackageFile() {
      assertThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedFile(CLASS_OUTPUT, "com.google.testing.compile", "Foo")
          .hasContents(ByteSource.wrap("Bar".getBytes(UTF_8)));
    }

    @Test
    public void generatedFilePackageFile_fail() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedFile(CLASS_OUTPUT, "com.google.testing.compile", "Bogus.class");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected)
          .factValue("expected to generate file")
          .isEqualTo("/com/google/testing/compile/Bogus.class");
    }

    @Test
    public void generatedFileDefaultPackageFile_fail() {
      expectFailure
          .whenTesting()
          .about(compilations())
          .that(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedFile(CLASS_OUTPUT, "", "File.java");
      AssertionError expected = expectFailure.getFailure();
      assertThat(expected).factValue("expected to generate file").isEqualTo("/File.java");
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
    }
  }

  private static String lines(String... lines) {
    return Stream.of(lines).collect(joining("\n"));
  }

  private static Compiler compilerWithError() {
    return javac().withProcessors(new ErrorProcessor());
  }

  private static Compiler compilerWithWarning() {
    return javac().withProcessors(new DiagnosticMessage.Processor(Diagnostic.Kind.WARNING));
  }

  private static Compiler compilerWithNote() {
    return javac().withProcessors(new DiagnosticMessage.Processor(Diagnostic.Kind.NOTE));
  }

  private static Compiler compilerWithGenerator() {
    return javac().withProcessors(new GeneratingProcessor());
  }

  private static Compiler compilerWithGeneratorAndError() {
    return javac().withProcessors(new FailingGeneratingProcessor());
  }

  private static Compiler throwingCompiler(RuntimeException e) {
    return javac().withProcessors(new ThrowingProcessor(e));
  }
}
