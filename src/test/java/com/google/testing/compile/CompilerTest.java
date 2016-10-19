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
import static com.google.testing.compile.Compiler.javac;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import javax.annotation.processing.Processor;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link Compiler}. */
@RunWith(JUnit4.class)
public final class CompilerTest {

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
}
