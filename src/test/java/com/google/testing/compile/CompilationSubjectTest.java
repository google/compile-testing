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

import static com.google.common.truth.Truth.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.CompilationSubject.compilations;
import static com.google.testing.compile.Compiler.javac;
import static com.google.testing.compile.VerificationFailureStrategy.VERIFY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.testing.compile.VerificationFailureStrategy.VerificationException;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
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
      JavaFileObjects.forResource("HelloWorld.java");

  private static final JavaFileObject HELLO_WORLD_BROKEN_RESOURCE =
      JavaFileObjects.forResource("HelloWorld-broken.java");

  private static final JavaFileObject HELLO_WORLD_DIFFERENT_RESOURCE =
      JavaFileObjects.forResource("HelloWorld-different.java");

  @RunWith(JUnit4.class)
  public static class StatusTest {
    @Test
    public void succeeded() {
      assertThat(javac().compile(HELLO_WORLD)).succeeded();
      assertThat(javac().compile(HELLO_WORLD_RESOURCE)).succeeded();
    }

    @Test
    public void succeeded_failureReportsGeneratedFiles() {
      try {
        verifyThat(compilerWithGeneratorAndError().compile(HELLO_WORLD_RESOURCE)).succeeded();
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage()).contains("Compilation produced the following errors:\n");
        assertThat(expected.getMessage()).contains(FailingGeneratingProcessor.GENERATED_CLASS_NAME);
        assertThat(expected.getMessage()).contains(FailingGeneratingProcessor.GENERATED_SOURCE);
      }
    }

    @Test
    public void succeeded_failureReportsNoGeneratedFiles() {
      try {
        verifyThat(javac().compile(HELLO_WORLD_BROKEN_RESOURCE)).succeeded();
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith("Compilation produced the following errors:\n");
        assertThat(expected.getMessage()).contains("No files were generated.");
      }
    }

    @Test
    public void succeeded_exceptionCreatedOrPassedThrough() {
      RuntimeException e = new RuntimeException();
      try {
        verifyThat(throwingCompiler(e).compile(HELLO_WORLD_RESOURCE)).succeeded();
        fail();
      } catch (CompilationFailureException expected) {
        // some old javacs don't pass through exceptions, so we create one
      } catch (RuntimeException expected) {
        // newer jdks throw a runtime exception whose cause is the original exception
        assertThat(expected.getCause()).isEqualTo(e);
      }
    }

    @Test
    public void succeededWithoutWarnings() {
      assertThat(javac().compile(HELLO_WORLD)).succeededWithoutWarnings();
    }

    @Test
    public void succeededWithoutWarnings_failsWithWarnings() {
      try {
        verifyThat(compilerWithWarning().compile(HELLO_WORLD)).succeededWithoutWarnings();
        fail();
      } catch (VerificationException e) {
        assertThat(e.getMessage())
            .contains("Expected 0 warnings, but found the following 2 warnings:\n");
      }
    }

    @Test
    public void failedToCompile() {
      assertThat(javac().compile(HELLO_WORLD_BROKEN_RESOURCE)).failed();
    }

    @Test
    public void failedToCompile_compilationSucceeded() {
      try {
        verifyThat(javac().compile(HELLO_WORLD_RESOURCE)).failed();
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith("Compilation was expected to fail, but contained no errors");
        assertThat(expected.getMessage()).contains("No files were generated.");
      }
    }
  }

  /**
   * Tests for {@link CompilationSubject}'s assertions about warnings and notes, for both successful
   * and unsuccessful compilations.
   */
  @RunWith(Parameterized.class)
  public static final class WarningAndNoteTest {
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
    public void hadWarningContaining() {
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
      try {
        verifyThat(compilerWithWarning().compile(sourceFile)).hadWarningContaining("what is it?");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith("Expected a warning containing \"what is it?\", but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("this is a message\n");
      }
    }

    @Test
    public void hadWarningContainingMatch_noSuchWarning() {
      try {
        verifyThat(compilerWithWarning().compile(sourceFile))
            .hadWarningContainingMatch("(what|where) is it?");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith(
                "Expected a warning containing match for /(what|where) is it?/, but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("this is a message\n");
      }
    }

    @Test
    public void hadWarningContainingMatch_pattern_noSuchWarning() {
      try {
        verifyThat(compilerWithWarning().compile(sourceFile))
            .hadWarningContainingMatch(Pattern.compile("(what|where) is it?"));
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith(
                "Expected a warning containing match for /(what|where) is it?/, but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("this is a message\n");
      }
    }

    @Test
    public void hadWarningContainingInFile_wrongFile() {
      try {
        verifyThat(compilerWithWarning().compile(sourceFile))
            .hadWarningContaining("this is a message")
            .inFile(HELLO_WORLD_DIFFERENT_RESOURCE);
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains(
                String.format(
                    "Expected a warning in %s", HELLO_WORLD_DIFFERENT_RESOURCE.getName()));
        assertThat(expected.getMessage()).contains(sourceFile.getName());
      }
    }

    @Test
    public void hadWarningContainingInFileOnLine_wrongLine() {
      try {
        verifyThat(compilerWithWarning().compile(sourceFile))
            .hadWarningContaining("this is a message")
            .inFile(sourceFile)
            .onLine(1);
        fail();
      } catch (VerificationException expected) {
        int actualErrorLine = 6;
        assertThat(expected.getMessage())
            .contains(String.format("Expected a warning on line 1 of %s", sourceFile.getName()));
        assertThat(expected.getMessage()).contains("" + actualErrorLine);
      }
    }

    @Test
    public void hadWarningContainingInFileOnLineAtColumn_wrongColumn() {
      try {
        verifyThat(compilerWithWarning().compile(sourceFile))
            .hadWarningContaining("this is a message")
            .inFile(sourceFile)
            .onLine(6)
            .atColumn(1);
        fail();
      } catch (VerificationException expected) {
        int actualErrorCol = 8;
        assertThat(expected.getMessage())
            .contains(String.format("Expected a warning at 6:1 of %s", sourceFile.getName()));
        assertThat(expected.getMessage()).contains("[" + actualErrorCol + "]");
      }
    }

    @Test
    public void hadWarningCount() {
      assertThat(compilerWithWarning().compile(sourceFile)).hadWarningCount(2);
    }

    @Test
    public void hadWarningCount_wrongCount() {
      try {
        verifyThat(compilerWithWarning().compile(sourceFile)).hadWarningCount(42);
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains("Expected 42 warnings, but found the following 2 warnings:\n");
      }
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
      try {
        verifyThat(compilerWithNote().compile(sourceFile)).hadNoteContaining("what is it?");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith("Expected a note containing \"what is it?\", but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("this is a message\n");
      }
    }

    @Test
    public void hadNoteContainingMatch_noSuchNote() {
      try {
        verifyThat(compilerWithNote().compile(sourceFile))
            .hadNoteContainingMatch("(what|where) is it?");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith(
                "Expected a note containing match for /(what|where) is it?/, but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("this is a message\n");
      }
    }

    @Test
    public void hadNoteContainingMatch_pattern_noSuchNote() {
      try {
        verifyThat(compilerWithNote().compile(sourceFile))
            .hadNoteContainingMatch(Pattern.compile("(what|where) is it?"));
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith(
                "Expected a note containing match for /(what|where) is it?/, but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("this is a message\n");
      }
    }

    @Test
    public void hadNoteContainingInFile_wrongFile() {
      try {
        verifyThat(compilerWithNote().compile(sourceFile))
            .hadNoteContaining("this is a message")
            .inFile(HELLO_WORLD_DIFFERENT_RESOURCE);
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains(
                String.format("Expected a note in %s", HELLO_WORLD_DIFFERENT_RESOURCE.getName()));
        assertThat(expected.getMessage()).contains(sourceFile.getName());
      }
    }

    @Test
    public void hadNoteContainingInFileOnLine_wrongLine() {
      try {
        verifyThat(compilerWithNote().compile(sourceFile))
            .hadNoteContaining("this is a message")
            .inFile(sourceFile)
            .onLine(1);
        fail();
      } catch (VerificationException expected) {
        int actualErrorLine = 6;
        assertThat(expected.getMessage())
            .contains(String.format("Expected a note on line 1 of %s", sourceFile.getName()));
        assertThat(expected.getMessage()).contains("" + actualErrorLine);
      }
    }

    @Test
    public void hadNoteContainingInFileOnLineAtColumn_wrongColumn() {
      try {
        verifyThat(compilerWithNote().compile(sourceFile))
            .hadNoteContaining("this is a message")
            .inFile(sourceFile)
            .onLine(6)
            .atColumn(1);
        fail();
      } catch (VerificationException expected) {
        int actualErrorCol = 8;
        assertThat(expected.getMessage())
            .contains(String.format("Expected a note at 6:1 of %s", sourceFile.getName()));
        assertThat(expected.getMessage()).contains("[" + actualErrorCol + "]");
      }
    }

    @Test
    public void hadNoteCount() {
      assertThat(compilerWithNote().compile(sourceFile)).hadNoteCount(2);
    }

    @Test
    public void hadNoteCount_wrongCount() {
      try {
        verifyThat(compilerWithNote().compile(sourceFile)).hadNoteCount(42);
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains("Expected 42 notes, but found the following 2 notes:\n");
      }
    }
  }

  /** Tests for {@link CompilationSubject}'s assertions about errors. */
  @RunWith(JUnit4.class)
  public static final class ErrorTest {
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
      try {
        verifyThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
            .hadErrorContaining("some error");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith("Expected an error containing \"some error\", but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("expected error!\n");
      }
    }

    @Test
    public void hadErrorContainingMatch_noSuchError() {
      try {
        verifyThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
            .hadErrorContainingMatch("(what|where) is it?");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith(
                "Expected an error containing match for /(what|where) is it?/, but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("expected error!\n");
      }
    }

    @Test
    public void hadErrorContainingMatch_pattern_noSuchError() {
      try {
        verifyThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
            .hadErrorContainingMatch(Pattern.compile("(what|where) is it?"));
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .startsWith(
                "Expected an error containing match for /(what|where) is it?/, but only found:\n");
        // some versions of javac wedge the file and position in the middle
        assertThat(expected.getMessage()).endsWith("expected error!\n");
      }
    }

    @Test
    public void hadErrorContainingInFile_wrongFile() {
      try {
        verifyThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
            .hadErrorContaining("expected error!")
            .inFile(HELLO_WORLD_DIFFERENT_RESOURCE);
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains(
                String.format("Expected an error in %s", HELLO_WORLD_DIFFERENT_RESOURCE.getName()));
        assertThat(expected.getMessage()).contains(HELLO_WORLD_RESOURCE.getName());
        //                  "(no associated file)")));
      }
    }

    @Test
    public void hadErrorContainingInFileOnLine_wrongLine() {
      try {
        verifyThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
            .hadErrorContaining("expected error!")
            .inFile(HELLO_WORLD_RESOURCE)
            .onLine(1);
        fail();
      } catch (VerificationException expected) {
        int actualErrorLine = 18;
        assertThat(expected.getMessage())
            .contains(
                String.format("Expected an error on line 1 of %s", HELLO_WORLD_RESOURCE.getName()));
        assertThat(expected.getMessage()).contains("" + actualErrorLine);
      }
    }

    @Test
    public void hadErrorContainingInFileOnLineAtColumn_wrongColumn() {
      try {
        verifyThat(compilerWithError().compile(HELLO_WORLD_RESOURCE))
            .hadErrorContaining("expected error!")
            .inFile(HELLO_WORLD_RESOURCE)
            .onLine(18)
            .atColumn(1);
        fail();
      } catch (VerificationException expected) {
        int actualErrorCol = 8;
        assertThat(expected.getMessage())
            .contains(
                String.format("Expected an error at 18:1 of %s", HELLO_WORLD_RESOURCE.getName()));
        assertThat(expected.getMessage()).contains("" + actualErrorCol);
      }
    }

    @Test
    public void hadErrorCount() {
      assertThat(compilerWithError().compile(HELLO_WORLD_BROKEN_RESOURCE)).hadErrorCount(4);
    }

    @Test
    public void hadErrorCount_wrongCount() {
      try {
        verifyThat(compilerWithError().compile(HELLO_WORLD_RESOURCE)).hadErrorCount(42);
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains("Expected 42 errors, but found the following 2 errors:\n");
      }
    }
  }

  @RunWith(JUnit4.class)
  public static class GeneratedFilesTest {
    @Test
    public void generatedSourceFile() {
      assertThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedSourceFile(GeneratingProcessor.GENERATED_CLASS_NAME)
          .hasSourceEquivalentTo(
              JavaFileObjects.forSourceString(
                  GeneratingProcessor.GENERATED_CLASS_NAME, GeneratingProcessor.GENERATED_SOURCE));
    }

    @Test
    public void generatedSourceFile_fail() {
      try {
        verifyThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
            .generatedSourceFile("ThisIsNotTheRightFile");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage()).contains("generated the file ThisIsNotTheRightFile.java");
        assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
      }
    }

    @Test
    public void generatedFilePath() {
      assertThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedFile(CLASS_OUTPUT, "com/google/testing/compile/Foo")
          .hasContents(ByteSource.wrap("Bar".getBytes(UTF_8)));
    }

    @Test
    public void generatedFilePath_fail() {
      try {
        verifyThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
            .generatedFile(CLASS_OUTPUT, "com/google/testing/compile/Bogus.class");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains("generated the file com/google/testing/compile/Bogus.class");
      }
    }

    @Test
    public void generatedFilePackageFile() {
      assertThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
          .generatedFile(CLASS_OUTPUT, "com.google.testing.compile", "Foo")
          .hasContents(ByteSource.wrap("Bar".getBytes(UTF_8)));
    }

    @Test
    public void generatedFilePackageFile_fail() {
      try {
        verifyThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
            .generatedFile(CLASS_OUTPUT, "com.google.testing.compile", "Bogus.class");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains(
                "generated the file named \"Bogus.class\" "
                    + "in package \"com.google.testing.compile\"");
      }
    }

    @Test
    public void generatedFileDefaultPackageFile_fail() {
      try {
        verifyThat(compilerWithGenerator().compile(HELLO_WORLD_RESOURCE))
            .generatedFile(CLASS_OUTPUT, "", "File.java");
        fail();
      } catch (VerificationException expected) {
        assertThat(expected.getMessage())
            .contains("generated the file named \"File.java\" in the default package");
        assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
      }
    }
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

  private static CompilationSubject verifyThat(Compilation compilation) {
    return VERIFY.about(compilations()).that(compilation);
  }
}
