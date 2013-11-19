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

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.truth0.FailureStrategy;
import org.truth0.TestVerb;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

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
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource(Resources.getResource("HelloWorld.java")))
        .compilesWithoutError();
    ASSERT.about(javaSource())
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
  public void compilesWithoutError_throws() {
    try {
      VERIFY.about(javaSource())
          .that(JavaFileObjects.forResource("HelloWorld-broken.java"))
          .compilesWithoutError();
      fail();
    } catch (VerificationException expected) {
      ASSERT.that(expected.getMessage()).startsWith("Compilation produced the following errors:\n");
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
      ASSERT.that(expected.getCause()).is(e);
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
      ASSERT.that(expected.getMessage()).isEqualTo(
          "Compilation was expected to fail, but contained no errors");
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
      ASSERT.that(expected.getMessage()).startsWith(
          "Expected an error containing \"some error\", but only found [\"");
      // some versions of javac wedge the file and position in the middle
      ASSERT.that(expected.getMessage()).endsWith("expected error!\"]");
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
      ASSERT.that(expected.getMessage())
          .isEqualTo(String.format("Expected an error in %s, but only found errors in [%s]",
              otherFileObject.getName(), fileObject.getName()));
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
      ASSERT.that(expected.getMessage())
          .isEqualTo(String.format(
              "Expected an error on line 1 of %s, but only found errors on line(s) [18]",
              fileObject.getName()));
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
      ASSERT.that(expected.getMessage())
          .isEqualTo(String.format(
              "Expected an error at 18:1 of %s, but only found errors at column(s) [8]",
              fileObject.getName()));
    }
  }

  @Test
  public void failsToCompile() {
    JavaFileObject brokenFileObject = JavaFileObjects.forResource("HelloWorld-broken.java");
    ASSERT.about(javaSource())
        .that(brokenFileObject)
        .failsToCompile()
        .withErrorContaining("not a statement").in(brokenFileObject).onLine(23).atColumn(5);

    JavaFileObject happyFileObject = JavaFileObjects.forResource("HelloWorld.java");
    ASSERT.about(javaSource())
        .that(happyFileObject)
        .processedWith(new ErrorProcessor())
        .failsToCompile()
        .withErrorContaining("expected error!").in(happyFileObject).onLine(18).atColumn(8);
  }

  @Test
  public void generates() {
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("HelloWorld.java"))
        .processedWith(new GeneratingProcessor())
        .compilesWithoutError()
        .and().generatesSources(JavaFileObjects.forSourceString(
            GeneratingProcessor.GENERATED_CLASS_NAME,
            GeneratingProcessor.GENERATED_SOURCE));
  }

  @Test
  public void invokesMultipleProcesors() {
    NoOpProcessor noopProcessor1 = new NoOpProcessor();
    NoOpProcessor noopProcessor2 = new NoOpProcessor();
    ASSERT.that(noopProcessor1.invoked).isFalse();
    ASSERT.that(noopProcessor2.invoked).isFalse();
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("HelloWorld.java"))
        .processedWith(noopProcessor1, noopProcessor2)
        .compilesWithoutError();
    ASSERT.that(noopProcessor1.invoked).isTrue();
    ASSERT.that(noopProcessor2.invoked).isTrue();
  }

  @Test
  public void invokesMultipleProcesors_asIterable() {
    NoOpProcessor noopProcessor1 = new NoOpProcessor();
    NoOpProcessor noopProcessor2 = new NoOpProcessor();
    ASSERT.that(noopProcessor1.invoked).isFalse();
    ASSERT.that(noopProcessor2.invoked).isFalse();
    ASSERT.about(javaSource())
        .that(JavaFileObjects.forResource("HelloWorld.java"))
        .processedWith(Arrays.asList(noopProcessor1, noopProcessor2))
        .compilesWithoutError();
    ASSERT.that(noopProcessor1.invoked).isTrue();
    ASSERT.that(noopProcessor2.invoked).isTrue();
  }


  private static final class GeneratingProcessor extends AbstractProcessor {
    static final String GENERATED_CLASS_NAME = "Blah";
    static final String GENERATED_SOURCE = "final class Blah {}";

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
