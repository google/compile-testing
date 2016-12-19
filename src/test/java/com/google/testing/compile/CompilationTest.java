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
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CompilationTest {
  @Test
  public void generatedFiles_unsuccessfulCompilationThrows() {
    Compilation compilation =
        javac()
            .compile(
                JavaFileObjects.forSourceLines(
                    "test.Bad", "package test;", "", "this doesn't compile!"));
    assertThat(compilation).failed();
    try {
      ImmutableList<JavaFileObject> unused = compilation.generatedFiles();
      fail();
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void describeFailureDiagnostics_includesWarnings_whenCompilingWerror() {
    // Arrange
    Compiler compiler = javac().withOptions("-Xlint:cast", "-Werror");
    JavaFileObject source =
        JavaFileObjects.forSourceLines(
            "test.CastWarning", //
            "package test;", //
            "class CastWarning {", //
            "  int i = (int) 0;", //
            "}");

    // Act
    Compilation compilation = compiler.compile(source);

    // Assert
    assertThat(compilation).failed();
    assertThat(compilation.describeFailureDiagnostics()).contains("[cast] redundant cast to int");
  }
}
