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
import static com.google.testing.compile.Compiler.javac;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assume.assumeTrue;

import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Locale;
import javax.annotation.processing.Processor;
import javax.lang.model.SourceVersion;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Compiler}. */
@RunWith(JUnit4.class)
public final class CompilerTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  private static final JavaFileObject HELLO_WORLD = JavaFileObjects.forResource("HelloWorld.java");

  @Test
  public void options() {
    NoOpProcessor processor = new NoOpProcessor();
    Object[] options1 = {"-Agone=nowhere"};
    JavaFileObject[] files = {HELLO_WORLD};
    Compilation unused =
        javac()
            .withOptions(options1)
            .withOptions(ImmutableList.of("-Ab=2", "-Ac=3"))
            .withProcessors(processor)
            .compile(files);
    assertThat(processor.options)
        .containsExactly(
            "b", "2",
            "c", "3")
        .inOrder();
  }

  @Test
  public void multipleProcesors() {
    NoOpProcessor noopProcessor1 = new NoOpProcessor();
    NoOpProcessor noopProcessor2 = new NoOpProcessor();
    NoOpProcessor noopProcessor3 = new NoOpProcessor();
    assertThat(noopProcessor1.invoked).isFalse();
    assertThat(noopProcessor2.invoked).isFalse();
    assertThat(noopProcessor3.invoked).isFalse();
    Processor[] processors = {noopProcessor1, noopProcessor3};
    JavaFileObject[] files = {HELLO_WORLD};
    Compilation unused =
        javac()
            .withProcessors(processors)
            .withProcessors(noopProcessor1, noopProcessor2)
            .compile(files);
    assertThat(noopProcessor1.invoked).isTrue();
    assertThat(noopProcessor2.invoked).isTrue();
    assertThat(noopProcessor3.invoked).isFalse();
  }

  @Test
  public void multipleProcesors_asIterable() {
    NoOpProcessor noopProcessor1 = new NoOpProcessor();
    NoOpProcessor noopProcessor2 = new NoOpProcessor();
    NoOpProcessor noopProcessor3 = new NoOpProcessor();
    assertThat(noopProcessor1.invoked).isFalse();
    assertThat(noopProcessor2.invoked).isFalse();
    assertThat(noopProcessor3.invoked).isFalse();
    JavaFileObject[] files = {HELLO_WORLD};
    Compilation unused =
        javac()
            .withProcessors(Arrays.asList(noopProcessor1, noopProcessor3))
            .withProcessors(Arrays.asList(noopProcessor1, noopProcessor2))
            .compile(files);
    assertThat(noopProcessor1.invoked).isTrue();
    assertThat(noopProcessor2.invoked).isTrue();
    assertThat(noopProcessor3.invoked).isFalse();
  }

  @Test
  public void classPath_default() {
    Compilation compilation =
        javac()
            .compile(
                JavaFileObjects.forSourceLines(
                    "Test",
                    "import com.google.testing.compile.CompilerTest;",
                    "class Test {",
                    "  CompilerTest t;",
                    "}"));
    assertThat(compilation).succeeded();
  }

  @Test
  public void classPath_empty() {
    Compilation compilation =
        javac()
            .withClasspath(ImmutableList.of())
            .compile(
                JavaFileObjects.forSourceLines(
                    "Test",
                    "import com.google.testing.compile.CompilerTest;",
                    "class Test {",
                    "  CompilerTest t;",
                    "}"));
    assertThat(compilation).hadErrorContaining("com.google.testing.compile does not exist");
  }

  /** Sets up a jar containing a single class 'Lib', for use in classpath tests. */
  private File compileTestLib() throws IOException {
    File lib = temporaryFolder.newFolder("tmp");
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(/* diagnosticListener= */ null, Locale.getDefault(), UTF_8);
    fileManager.setLocation(StandardLocation.CLASS_OUTPUT, ImmutableList.of(lib));
    CompilationTask task =
        compiler.getTask(
            /* out= */ null,
            fileManager,
            /* diagnosticListener= */ null,
            /* options= */ ImmutableList.of(),
            /* classes= */ null,
            ImmutableList.of(JavaFileObjects.forSourceLines("Lib", "class Lib {}")));
    assertThat(task.call()).isTrue();
    return lib;
  }

  @Test
  public void classPath_customFiles() throws Exception {
    File lib = compileTestLib();
    // compile with only 'Lib' on the classpath
    Compilation compilation =
        javac()
            .withClasspath(ImmutableList.of(lib))
            .withOptions("-verbose")
            .compile(
                JavaFileObjects.forSourceLines(
                    "Test", //
                    "class Test {",
                    "  Lib lib;",
                    "}"));
    assertThat(compilation).succeeded();
  }

  @Test
  public void classPath_empty_urlClassLoader() {
    Compilation compilation =
        javac()
            .withClasspathFrom(new URLClassLoader(new URL[0], Compiler.platformClassLoader))
            .compile(
                JavaFileObjects.forSourceLines(
                    "Test",
                    "import com.google.testing.compile.CompilerTest;",
                    "class Test {",
                    "  CompilerTest t;",
                    "}"));
    assertThat(compilation).hadErrorContaining("com.google.testing.compile does not exist");
  }

  @Test
  public void classPath_customFiles_urlClassLoader() throws Exception {
    File lib = compileTestLib();
    Compilation compilation =
        javac()
            .withClasspathFrom(new URLClassLoader(new URL[] {lib.toURI().toURL()}))
            .withOptions("-verbose")
            .compile(JavaFileObjects.forSourceLines("Test", "class Test {", "  Lib lib;", "}"));
    assertThat(compilation).succeeded();
  }

  @Test
  public void releaseFlag() {
    assumeTrue(isJdk9OrLater());
    Compilation compilation =
        javac()
            .withOptions("--release", "8")
            .compile(JavaFileObjects.forSourceString("HelloWorld", "final class HelloWorld {}"));
    assertThat(compilation).succeeded();
  }

  static boolean isJdk9OrLater() {
    return SourceVersion.latestSupported().compareTo(SourceVersion.RELEASE_8) > 0;
  }
}
