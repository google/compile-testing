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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static org.junit.Assert.fail;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.TestVerb;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

/**
 * Tests {@link JavaSourcesSubjectFactory} (and {@link JavaSourceSubjectFactory}).
 *
 * @author Gregory Kick
 */
@RunWith(JUnit4.class)
public class JavaSourcesSubjectFactoryTest {
  /** We need a {@link TestVerb} that throws anything <i>except</i> {@link AssertionError}. */
  private static final TestVerb VERIFY = new TestVerb(new FailureStrategy() {
    @Override
    public void fail(String message) {
      throw new VerificationException(message);
    }
  });

  @Test
  public void compilesWithoutError() {
    assert_().about(javaSource())
        .that(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java")))
        .compilesWithoutError();
    assert_().about(javaSource())
        .that(JavaFileObjects.forSourceLines("test.HelloWorld",
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
  public void compilesWithoutError_failureReportsFiles() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java")))
          .processedWith(new FailingGeneratingProcessor())
          .compilesWithoutError();
      fail();
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).contains("Compilation produced the following errors:\n");
      assertThat(expected.getMessage()).contains(FailingGeneratingProcessor.GENERATED_CLASS_NAME);
      assertThat(expected.getMessage()).contains(FailingGeneratingProcessor.GENERATED_SOURCE);
    }
  }

  @Test
  public void compilesWithoutError_throws() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld-broken.java"))
          .compilesWithoutError();
      fail();
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).startsWith("Compilation produced the following errors:\n");
      assertThat(expected.getMessage()).contains("No files were generated.");
    }
  }

  @Test
  public void compilesWithoutError_exceptionCreatedOrPassedThrough() {
    final RuntimeException e = new RuntimeException();
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .processedWith(new AbstractProcessor() {
            @Override
            public Set<String> getSupportedAnnotationTypes() {
              return ImmutableSet.of("*");
            }

            @Override
            public boolean process(Set<? extends TypeElement> annotations,
                RoundEnvironment roundEnv) {
              throw e;
            }
          })
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
    assert_().about(javaSource())
        .that(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java")))
        .parsesAs(JavaFileObjects.forSourceLines("test.HelloWorld",
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
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java")))
          .parsesAs(JavaFileObjects.forResource("HelloWorld-broken.java"));
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).startsWith("error while parsing:");
    }
  }

  @Test
  public void parsesAs_actualFileFailsToParse() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld-broken.java"))
          .parsesAs(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java")));
      fail();
    } catch (IllegalStateException expected) {
      assertThat(expected.getMessage()).startsWith("error while parsing:");
    }
  }

  @Test
  public void failsToCompile_throws() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .failsToCompile();
      fail();
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).startsWith(
          "Compilation was expected to fail, but contained no errors");
      assertThat(expected.getMessage()).contains("No files were generated.");
    }
  }

  @Test
  public void failsToCompile_throwsNoMessage() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .processedWith(new ErrorProcessor())
          .failsToCompile().withErrorContaining("some error");
      fail();
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).startsWith(
          "Expected an error containing \"some error\", but only found [\"");
      // some versions of javac wedge the file and position in the middle
      assertThat(expected.getMessage()).endsWith("expected error!\"]");
    }
  }

  @Test
  public void failsToCompile_throwsNotInFile() {
    JavaFileObject fileObject = JavaFileObjects.forResource("HelloWorld.java");
    JavaFileObject otherFileObject = JavaFileObjects.forResource("HelloWorld-different.java");
    try {
      VERIFY.about(javaSource())
          .that(fileObject)
          .processedWith(new ErrorProcessor())
          .failsToCompile().withErrorContaining("expected error!")
              .in(otherFileObject);
      fail();
    } catch (VerificationException expected) {
      assertThat(expected.getMessage())
          .contains(String.format("Expected an error in %s", otherFileObject.getName()));
      assertThat(expected.getMessage()).contains(fileObject.getName());
      //                  "(no associated file)")));
    }
  }

  @Test
  public void failsToCompile_throwsNotOnLine() {
    JavaFileObject fileObject = JavaFileObjects.forResource("HelloWorld.java");
    try {
      VERIFY.about(javaSource())
          .that(fileObject)
          .processedWith(new ErrorProcessor())
          .failsToCompile().withErrorContaining("expected error!")
          .in(fileObject).onLine(1);
      fail();
    } catch (VerificationException expected) {
      int actualErrorLine = 18;
      assertThat(expected.getMessage())
          .contains(String.format("Expected an error on line 1 of %s", fileObject.getName()));
      assertThat(expected.getMessage()).contains("" + actualErrorLine);
    }
  }

  @Test
  public void failsToCompile_throwsNotAtColumn() {
    JavaFileObject fileObject = JavaFileObjects.forResource("HelloWorld.java");
    try {
      VERIFY.about(javaSource())
          .that(fileObject)
          .processedWith(new ErrorProcessor())
          .failsToCompile().withErrorContaining("expected error!")
          .in(fileObject).onLine(18).atColumn(1);
      fail();
    } catch (VerificationException expected) {
      int actualErrorCol = 8;
      assertThat(expected.getMessage())
          .contains(String.format("Expected an error at 18:1 of %s", fileObject.getName()));
      assertThat(expected.getMessage()).contains("" + actualErrorCol);
    }
  }

  @Test
  public void failsToCompile() {
    JavaFileObject brokenFileObject = JavaFileObjects.forResource("HelloWorld-broken.java");
    assert_().about(javaSource())
        .that(brokenFileObject)
        .failsToCompile()
        .withErrorContaining("not a statement").in(brokenFileObject).onLine(23).atColumn(5);

    JavaFileObject happyFileObject = JavaFileObjects.forResource("HelloWorld.java");
    assert_().about(javaSource())
        .that(happyFileObject)
        .processedWith(new ErrorProcessor())
        .failsToCompile()
        .withErrorContaining("expected error!").in(happyFileObject).onLine(18).atColumn(8);
  }

  @Test
  public void generatesSources() {
    assert_().about(javaSource())
        .that(JavaFileObjects.forResource("HelloWorld.java"))
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forSourceString(
            GeneratingProcessor.GENERATED_CLASS_NAME,
            GeneratingProcessor.GENERATED_SOURCE));
  }

  @Test
  public void generatesSources_failOnUnexpected() {
    String failingExpectationSource = "abstract class Blah {}";
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .processedWith(new GeneratingProcessor())
          .compilesWithoutError()
          .and().generatesSources(JavaFileObjects.forSourceString(
              GeneratingProcessor.GENERATED_CLASS_NAME,
              failingExpectationSource));
      fail();
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).contains("didn't match exactly");
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_SOURCE);
    }
  }

  @Test
  public void generatesSources_failOnExtraExpected() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .processedWith(new GeneratingProcessor())
          .compilesWithoutError()
          .and().generatesSources(JavaFileObjects.forSourceLines(
              GeneratingProcessor.GENERATED_CLASS_NAME,
              "import java.util.List;  // Extra import",
              "final class Blah {",
              "   String blah = \"blah\";",
              "}"));
      fail();
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).contains("didn't match exactly");
      assertThat(expected.getMessage()).contains("unmatched nodes in the expected tree");
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_SOURCE);
    }
  }

  @Test
  public void generatesSources_failOnExtraActual() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .processedWith(new GeneratingProcessor())
          .compilesWithoutError()
          .and().generatesSources(JavaFileObjects.forSourceLines(
              GeneratingProcessor.GENERATED_CLASS_NAME,
              "final class Blah {",
              "  // missing field",
              "}"));
      fail();
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).contains("didn't match exactly");
      assertThat(expected.getMessage()).contains("unmatched nodes in the actual tree");
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_SOURCE);
    }
  }

  @Test
  public void generatesSources_failWithNoCandidates() {
    String failingExpectationName = "ThisIsNotTheRightFile";
    String failingExpectationSource = "abstract class ThisIsNotTheRightFile {}";
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .processedWith(new GeneratingProcessor())
          .compilesWithoutError()
          .and().generatesSources(JavaFileObjects.forSourceString(
              failingExpectationName,
              failingExpectationSource));
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).contains("top-level types that were not present");
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_CLASS_NAME);
      assertThat(expected.getMessage()).contains(failingExpectationName);
    }
  }

  @Test
  public void generatesFileNamed() {
    assert_().about(javaSource())
        .that(JavaFileObjects.forResource("HelloWorld.java"))
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and()
        .generatesFileNamed(CLASS_OUTPUT, "com.google.testing.compile", "Foo")
        .withContents(ByteSource.wrap("Bar".getBytes(UTF_8)));
  }

  @Test
  public void generatesFileNamed_failOnFileExistence() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .processedWith(new GeneratingProcessor())
          .compilesWithoutError()
          .and()
          .generatesFileNamed(CLASS_OUTPUT, "com.google.testing.compile", "Bogus")
          .withContents(ByteSource.wrap("Bar".getBytes(UTF_8)));
    } catch (VerificationException expected) {
      assertThat(expected.getMessage())
          .contains("Did not find a generated file corresponding to Bogus");
      assertThat(expected.getMessage()).contains(GeneratingProcessor.GENERATED_RESOURCE_NAME);
    }
  }

  @Test
  public void generatesFileNamed_failOnFileContents() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld.java"))
          .processedWith(new GeneratingProcessor())
          .compilesWithoutError()
          .and()
          .generatesFileNamed(CLASS_OUTPUT, "com.google.testing.compile", "Foo")
          .withContents(ByteSource.wrap("Bogus".getBytes(UTF_8)));
    } catch (VerificationException expected) {
      assertThat(expected.getMessage()).contains("Foo");
      assertThat(expected.getMessage()).contains(" did not match the expected contents");
    }
  }

  @Test
  public void invokesMultipleProcesors() {
    NoOpProcessor noopProcessor1 = new NoOpProcessor();
    NoOpProcessor noopProcessor2 = new NoOpProcessor();
    assertThat(noopProcessor1.invoked).isFalse();
    assertThat(noopProcessor2.invoked).isFalse();
    assert_().about(javaSource())
        .that(JavaFileObjects.forResource("HelloWorld.java"))
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
    assert_().about(javaSource())
        .that(JavaFileObjects.forResource("HelloWorld.java"))
        .processedWith(Arrays.asList(noopProcessor1, noopProcessor2))
        .compilesWithoutError();
    assertThat(noopProcessor1.invoked).isTrue();
    assertThat(noopProcessor2.invoked).isTrue();
  }


  private static final class GeneratingProcessor extends AbstractProcessor {
    static final String GENERATED_CLASS_NAME = "Blah";
    static final String GENERATED_SOURCE = "final class Blah {\n  String blah = \"blah\";\n}";

    static final String GENERATED_RESOURCE_NAME = "Foo";
    static final String GENERATED_RESOURCE = "Bar";

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
      try {
        JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(GENERATED_CLASS_NAME);
        Writer writer = sourceFile.openWriter();
        writer.write(GENERATED_SOURCE);
        writer.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      try {
        Writer writer = processingEnv.getFiler().createResource(CLASS_OUTPUT,
            JavaSourcesSubjectFactoryTest.class.getPackage().getName(), GENERATED_RESOURCE_NAME)
            .openWriter();
        writer.write(GENERATED_RESOURCE);
        writer.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }
  }

  private static final class FailingGeneratingProcessor extends AbstractProcessor {
    static final String GENERATED_CLASS_NAME = GeneratingProcessor.GENERATED_CLASS_NAME;
    static final String GENERATED_SOURCE = GeneratingProcessor.GENERATED_SOURCE;
    static final String ERROR_MESSAGE = "expected error!";
    final GeneratingProcessor delegate = new GeneratingProcessor();
    Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
      delegate.init(processingEnv);
      this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      delegate.process(annotations, roundEnv);
      messager.printMessage(Kind.ERROR, ERROR_MESSAGE);
      return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return delegate.getSupportedAnnotationTypes();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return delegate.getSupportedSourceVersion();
    }
  }

  private static final class NoOpProcessor extends AbstractProcessor {
    boolean invoked = false;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      invoked = true;
      return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }
  }

  private static final class VerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    VerificationException(String message) {
      super(message);
    }
  }

  private static final class ErrorProcessor extends AbstractProcessor {
    Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
      super.init(processingEnv);
      this.messager = processingEnv.getMessager();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      for (Element element : roundEnv.getRootElements()) {
        messager.printMessage(Kind.ERROR, "expected error!", element);
        messager.printMessage(Kind.ERROR, "another expected error!");
      }
      return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latestSupported();
    }
  }
}
