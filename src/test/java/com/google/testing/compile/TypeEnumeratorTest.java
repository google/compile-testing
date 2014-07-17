/*
 * Copyright (C) 2014 Google, Inc.
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

import static org.truth0.Truth.ASSERT;

import com.sun.source.tree.CompilationUnitTree;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Unit test for {@link TypeEnumerator}
 */
@RunWith(JUnit4.class)
public class TypeEnumeratorTest {
  @Test
  public void getTopLevelTypes_singleQualifiedType() {
    CompilationUnitTree compilation = MoreTrees.parseLinesToTree(
        "package path.to.test;",
        "import java.util.List;",
        "",
        "public class HelloWorld {",
        "  public static void main(String[] args) {",
        "    System.out.println(\"Hello World!\");",
        "  }",
        "}");

    ASSERT.that(TypeEnumerator.getTopLevelTypes(compilation)).has().exactly(
        "path.to.test.HelloWorld");
  }

  @Test
  public void getTopLevelTypes_manyQualifiedTypes() {
    CompilationUnitTree compilation = MoreTrees.parseLinesToTree(
        "package path.to.test;",
        "import java.util.List;",
        "",
        "public class HelloWorld {",
        "  public static void main(String[] args) {",
        "    System.out.println(\"Hello World!\");",
        "  }",
        "}",
        "",
        "final class HelperWorld {}");

    ASSERT.that(TypeEnumerator.getTopLevelTypes(compilation)).has().exactly(
        "path.to.test.HelloWorld", "path.to.test.HelperWorld");
  }

  @Test
  public void getTopLevelTypes_singleSimpleTypes() {
    CompilationUnitTree compilation = MoreTrees.parseLinesToTree(
        "import java.util.List;",
        "",
        "public class HelloWorld {",
        "  public static void main(String[] args) {",
        "    System.out.println(\"Hello World!\");",
        "  }",
        "}");

    ASSERT.that(TypeEnumerator.getTopLevelTypes(compilation)).has().exactly(
        "HelloWorld");
  }

  @Test
  public void getTopLevelTypes_manySimpleTypes() {
    CompilationUnitTree compilation = MoreTrees.parseLinesToTree(
        "import java.util.List;",
        "",
        "public class HelloWorld {",
        "  public static void main(String[] args) {",
        "    System.out.println(\"Hello World!\");",
        "  }",
        "}",
        "",
        "final class HelperWorld {}");

    ASSERT.that(TypeEnumerator.getTopLevelTypes(compilation)).has().exactly(
        "HelloWorld", "HelperWorld");
  }

  @Test
  public void getTopLevelTypes_worksForAnnotationTypes() {
    CompilationUnitTree compilation = MoreTrees.parseLinesToTree(
        "package path.to.test;",
        "import java.util.List;",
        "",
        "public @interface HelloWorld {}");

    ASSERT.that(TypeEnumerator.getTopLevelTypes(compilation)).has().exactly(
        "path.to.test.HelloWorld");
  }

  @Test
  public void getTopLevelTypes_worksForEnums() {
    CompilationUnitTree compilation = MoreTrees.parseLinesToTree(
        "package path.to.test;",
        "import java.util.List;",
        "",
        "public enum HelloWorld {",
        "  HELLO,",
        "  WORLD;",
        "}");

    ASSERT.that(TypeEnumerator.getTopLevelTypes(compilation)).has().exactly(
        "path.to.test.HelloWorld");
  }

  @Test
  public void getTopLevelTypes_worksForInterfaces() {
    CompilationUnitTree compilation = MoreTrees.parseLinesToTree(
        "package path.to.test;",
        "import java.util.List;",
        "",
        "public interface HelloWorld {",
        "  public String getSalutation();",
        "}");

    ASSERT.that(TypeEnumerator.getTopLevelTypes(compilation)).has().exactly(
        "path.to.test.HelloWorld");
  }

  @Test
  public void getTopLevelTypes_worksForNull() {
    ASSERT.that(TypeEnumerator.getTopLevelTypes(null)).isEmpty();
  }
}
