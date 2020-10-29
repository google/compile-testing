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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import java.io.File;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CompilationTest {
  
  private static final JavaFileObject source1 =
      JavaFileObjects.forSourceLines(
          "test.Source1", // format one per line
          "package test;",
          "",
          "class Source1 {}");

  private static final JavaFileObject source2 =
      JavaFileObjects.forSourceLines(
          "test.Source2", // format one per line
          "package test;",
          "",
          "interface Source2 {}");

  private static final JavaFileObject brokenSource =
      JavaFileObjects.forSourceLines(
          "test.BrokenSource", // format one per line
          "package test;",
          "",
          "interface BrokenSource { what is this }");
  
  @Test
  public void compiler() {
    Compiler compiler = compilerWithGenerator();
    Compilation compilation = compiler.compile(source1, source2);
    assertThat(compilation.compiler()).isEqualTo(compiler);
    assertThat(compilation.sourceFiles()).containsExactly(source1, source2).inOrder();
    assertThat(compilation.status()).isEqualTo(Compilation.Status.SUCCESS);
  }
  
  @Test
  public void compilerStatusFailure() {
    Compiler compiler = compilerWithGenerator();
    Compilation compilation = compiler.compile(brokenSource);
    assertThat(compilation.status()).isEqualTo(Compilation.Status.FAILURE);
    assertThat(compilation.errors()).hasSize(1);
    assertThat(compilation.errors().get(0).getLineNumber()).isEqualTo(3);
  }

  @Test
  public void generatedFilePath() {
    Compiler compiler = compilerWithGenerator();
    Compilation compilation = compiler.compile(source1, source2);
    assertThat(compilation.generatedFile(SOURCE_OUTPUT, "test/generated/Blah.java")).isPresent();
  }

  @Test
  public void generatedFilePackage() {
    Compiler compiler = compilerWithGenerator();
    Compilation compilation = compiler.compile(source1, source2);
    assertThat(compilation.generatedFile(SOURCE_OUTPUT, "test.generated", "Blah.java")).isPresent();
  }

  @Test
  public void generatedSourceFile() {
    Compiler compiler = compilerWithGenerator();
    Compilation compilation = compiler.compile(source1, source2);
    assertThat(compilation.generatedSourceFile("test.generated.Blah")).isPresent();
  }

  private static Compiler compilerWithGenerator() {
    return javac().withProcessors(new GeneratingProcessor("test.generated"));
  }

  public static final JavaFileObject GENERATED_CLASS_WITH_PACKAGE = JavaFileObjects.forSourceLines("test.SomeClass_Copy",
      "package test;",
      "class SomeClass_Copy {",
      "  void copyMe() { System.out.println(\"You're good.\"); }",
      "}"
  );

  public static final JavaFileObject GENERATED_CLASS_WITHOUT_PACKAGE =
      JavaFileObjects.forSourceLines("SomeClass_Copy",
          "class SomeClass_Copy {",
          "  void copyMe() { System.out.println(\"You're good.\"); }",
          "}"
      );

  @Test
  public void copyInMemSourceFileWithPackage() {
    assertThat(compilerWithCopyingProcessor()
        .withInMemorySourcePath()
        .compile(JavaFileObjects.forSourceLines("test.SomeClass",
            "package test;",
            "class SomeClass {",
            "  void copyMe() { System.out.println(\"You're good.\"); }",
            "}"
        )))
        .generatedSourceFile("test.SomeClass_Copy")
        .hasSourceEquivalentTo(GENERATED_CLASS_WITH_PACKAGE);
  }

  @Test
  public void copyInMemSourceFileWithoutPackage() {
    assertThat(compilerWithCopyingProcessor()
        .withInMemorySourcePath()
        .compile(JavaFileObjects.forSourceLines("SomeClass",
            "class SomeClass {",
            "  void copyMe() { System.out.println(\"You're good.\"); }",
            "}"
        )))
        .generatedSourceFile("SomeClass_Copy")
        .hasSourceEquivalentTo(GENERATED_CLASS_WITHOUT_PACKAGE);
  }

  @Test
  public void copySourceFileWithPackage() {
    String className = "test/SomeClass.java";
    JavaFileObject fileObject = JavaFileObjects.forResource(className);
    assertThat(compilerWithCopyingProcessor()
        .withSourcepath(ImmutableList.of(findSourcePathRootForFile(fileObject, className)))
        .compile(fileObject))
        .generatedSourceFile("test.SomeClass_Copy")
        .hasSourceEquivalentTo(GENERATED_CLASS_WITH_PACKAGE);
  }

  @Test
  public void copySourceFileWithoutPackage() {
    String className = "SomeClass.java";
    JavaFileObject fileObject = JavaFileObjects.forResource(className);
    assertThat(compilerWithCopyingProcessor()
        .withSourcepath(ImmutableList.of(findSourcePathRootForFile(fileObject, className)))
        .compile(fileObject))
        .generatedSourceFile("SomeClass_Copy")
        .hasSourceEquivalentTo(GENERATED_CLASS_WITHOUT_PACKAGE);
  }

  private static Compiler compilerWithCopyingProcessor() {
    return javac().withProcessors(new CopyingProcessor());
  }

  private static File findSourcePathRootForFile(JavaFileObject fileObject, String name) {
    String[] parts = name.split("/");
    File fileObjectPath = new File(fileObject.toUri());
    for (String ignored : parts) {
      fileObjectPath = fileObjectPath.getParentFile();
    }
    return fileObjectPath;
  }

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
