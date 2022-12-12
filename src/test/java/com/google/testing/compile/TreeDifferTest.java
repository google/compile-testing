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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.Parser.ParseResult;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A test for {@link TreeDiffer}.
 */
@RunWith(JUnit4.class)
public class TreeDifferTest {
  private static final CompilationUnitTree EXPECTED_TREE =
      MoreTrees.parseLinesToTree("package test;",
          "import java.util.Set;",
          "",
          "final class TestClass {",
          "    public String toString() {",
          "        Object variable = new Object();",
          "        return \"literal\" + variable;",
          "    }",
          "",
          "    public void nonsense() {",
          "        int[] numbers = {0, 1, 2, 3, 4};",
          "        loop: for (int x : numbers) {",
          "            if (x % 2 == 0) {",
          "                throw new IllegalStateException();",
          "            }",
          "            break loop;",
          "        }",
          "    }",
          "}");
  private static final CompilationUnitTree ACTUAL_TREE =
      MoreTrees.parseLinesToTree("package test;",
          "import java.util.List;",
          "",
          "final class TestClass {",
          "    public String toString() {",
          "        Object variable = new Object();",
          "        return \"literal\" + variable;",
          "    }",
          "",
          "    public void nonsense() {",
          "        int[] numberz = {0, 1, 2, 3, 4, 5};",
          "        loop: for (int x : numberz) {",
          "            if (x % 2 == 0) {",
          "                throw new RuntimeException();",
          "            }",
          "            break;",
          "        }",
          "    }",
          "    public int extraNonsense() {",
          "      return 0;",
          "    }",
          "}");
  private static final CompilationUnitTree ASSERT_TREE_WITH_MESSAGE =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "   private void fail() {",
          "      assert false : \"details\";",
          "   }",
          "}");
  private static final CompilationUnitTree ASSERT_TREE_WITHOUT_MESSAGE =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "   private void fail() {",
          "      assert false;",
          "   }",
          "}");

  // These are used to test null tree iterators.
  // getInitializers() on NewArrayTrees will return null if the array is dimension-defined.
  private static final CompilationUnitTree NEW_ARRAY_SIZE_THREE =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final int[] myArray = new int[3];",
          "}");

  private static final CompilationUnitTree NEW_ARRAY_SIZE_FOUR =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final int[] myArray = new int[4];",
          "}");

  private static final CompilationUnitTree NEW_ARRAY_STATIC_INITIALIZER =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final int[] myArray = {1, 2, 3};",
          "}");

  private static final CompilationUnitTree LAMBDA_1 =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final Consumer<Integer> NEWLINE = (int i) -> System.out.println(i);",
          "}");

  private static final CompilationUnitTree LAMBDA_2 =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final Consumer<Integer> NEWLINE =",
          "      (int i) -> System.out.println(i);",
          "}");

  private static final CompilationUnitTree LAMBDA_IMPLICIT_ARG_TYPE =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final Consumer<Integer> NEWLINE =",
          "      (i) -> System.out.println(i);",
          "}");

  private static final CompilationUnitTree LAMBDA_IMPLICIT_ARG_TYPE_NO_PARENS =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final Consumer<Integer> NEWLINE =",
          "      i -> System.out.println(i);",
          "}");

  private static final CompilationUnitTree METHOD_REFERENCE =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final Runnable NEWLINE = System.out::println;",
          "}");

  private static final CompilationUnitTree ANONYMOUS_CLASS =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  private static final Runnable NEWLINE = new Runnable() {",
          "    public void run() { System.out.println(); }",
          "  };",
          "}");

  private static final CompilationUnitTree TRY_WITH_RESOURCES_1 =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  void f() {",
          "    try (Resource1 r = new Resource1()) {}",
          "  }",
          "}");

  private static final CompilationUnitTree TRY_WITH_RESOURCES_2 =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "  void f() {",
          "    try (Resource2 r = new Resource2()) {}",
          "  }",
          "}");

  private static final ImmutableList<String> ANNOTATED_TYPE_SOURCE =
      ImmutableList.of(
          "package test;",
          "",
          "import java.lang.annotation.*;",
          "import java.util.List;",
          "",
          "@Target(ElementType.TYPE_USE)",
          "@interface Nullable {}",
          "",
          "interface NullableStringList extends List<@Nullable String> {}");

  private static final CompilationUnitTree ANNOTATED_TYPE_1 =
      MoreTrees.parseLinesToTree(ANNOTATED_TYPE_SOURCE);

  private static final CompilationUnitTree ANNOTATED_TYPE_2 =
      MoreTrees.parseLinesToTree(
          ANNOTATED_TYPE_SOURCE.stream()
              .map(s -> s.replace("@Nullable ", ""))
              .collect(toImmutableList()));

  private static final ImmutableList<String> MULTICATCH_SOURCE =
      ImmutableList.of(
          "package test;",
          "",
          "class TestClass {",
          "  void f() {",
          "    try {",
          "      System.gc();",
          "    } catch (IllegalArgumentException | NullPointerException e) {",
          "    }",
          "  }",
          "}");

  private static final CompilationUnitTree MULTICATCH_1 =
      MoreTrees.parseLinesToTree(MULTICATCH_SOURCE);

  private static final CompilationUnitTree MULTICATCH_2 =
      MoreTrees.parseLinesToTree(
          MULTICATCH_SOURCE.stream()
              .map(s -> s.replace("IllegalArgumentException", "IllegalStateException"))
              .collect(toImmutableList()));

  @Test
  public void scan_differingCompilationUnits() {
    TreeDifference diff = TreeDiffer.diffCompilationUnits(EXPECTED_TREE, ACTUAL_TREE);
    assertThat(diff.isEmpty()).isFalse();

    ImmutableList<SimplifiedDiff> extraNodesExpected = ImmutableList.of(
        new SimplifiedDiff(Tree.Kind.INT_LITERAL, ""),
        new SimplifiedDiff(Tree.Kind.METHOD, ""));

    ImmutableList<SimplifiedDiff> differingNodesExpected = ImmutableList.of(
        new SimplifiedDiff(Tree.Kind.MEMBER_SELECT,
            "Expected member-select identifier to be <Set> but was <List>."),
        new SimplifiedDiff(Tree.Kind.VARIABLE,
            "Expected variable name to be <numbers> but was <numberz>."),
        new SimplifiedDiff(Tree.Kind.IDENTIFIER,
            "Expected identifier name to be <numbers> but was <numberz>."),
        new SimplifiedDiff(Tree.Kind.IDENTIFIER,
            "Expected identifier name to be <IllegalStateException> but was <RuntimeException>."),
        new SimplifiedDiff(Tree.Kind.BREAK,
            "Expected break label to be <loop> but was <null>."));
    assertThat(diff.getExtraExpectedNodes().isEmpty()).isTrue();
    assertThat(diff.getExtraActualNodes().size()).isEqualTo(extraNodesExpected.size());

    ImmutableList.Builder<SimplifiedDiff> extraNodesFound =
        new ImmutableList.Builder<SimplifiedDiff>();
    for (TreeDifference.OneWayDiff extraNode : diff.getExtraActualNodes()) {
      extraNodesFound.add(SimplifiedDiff.create(extraNode));
    }
    assertThat(extraNodesFound.build()).containsExactlyElementsIn(extraNodesExpected).inOrder();
    ImmutableList.Builder<SimplifiedDiff> differingNodesFound =
        new ImmutableList.Builder<SimplifiedDiff>();
    for (TreeDifference.TwoWayDiff differingNode : diff.getDifferingNodes()) {
      differingNodesFound.add(SimplifiedDiff.create(differingNode));
    }
    assertThat(differingNodesFound.build()).containsExactlyElementsIn(differingNodesExpected);
  }

  @Test
  public void scan_testExtraFields() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(ASSERT_TREE_WITH_MESSAGE, ASSERT_TREE_WITHOUT_MESSAGE);
    assertThat(diff.isEmpty()).isFalse();
    diff = TreeDiffer.diffCompilationUnits(ASSERT_TREE_WITHOUT_MESSAGE, ASSERT_TREE_WITH_MESSAGE);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void scan_sameCompilationUnit() {
    assertThat(TreeDiffer.diffCompilationUnits(EXPECTED_TREE, EXPECTED_TREE).isEmpty()).isTrue();
  }

  @Test
  public void scan_identicalMethods() {
    assertThat(TreeDiffer.diffSubtrees(baseToStringTree(), diffToStringTree())
        .isEmpty()).isTrue();
  }

  @Test
  public void scan_differentTypes() {
    TreeDifference diff = TreeDiffer.diffSubtrees(asPath(EXPECTED_TREE), diffToStringTree());
    assertThat(diff.isEmpty()).isFalse();
    for (TreeDifference.TwoWayDiff differingNode : diff.getDifferingNodes()) {
      assertThat(differingNode.getDetails()).contains("Expected node kind to be");
    }
  }

  @Test
  public void scan_testTwoNullIterableTrees() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(NEW_ARRAY_SIZE_THREE, NEW_ARRAY_SIZE_FOUR);
    assertThat(diff.isEmpty()).isFalse();
    for (TreeDifference.TwoWayDiff differingNode : diff.getDifferingNodes()) {
      assertThat(differingNode.getDetails())
          .contains("Expected int-literal value to be <3> but was <4>");
    }
  }

  @Test
  public void scan_testExpectedNullIterableTree() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(NEW_ARRAY_SIZE_THREE, NEW_ARRAY_STATIC_INITIALIZER);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void scan_testActualNullIterableTree() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(NEW_ARRAY_STATIC_INITIALIZER, NEW_ARRAY_SIZE_FOUR);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void scan_testLambdas() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(LAMBDA_1, LAMBDA_2);
    assertThat(diff.getDiffReport()).isEmpty();
  }

  @Test
  public void scan_testLambdasExplicitVsImplicit() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(LAMBDA_1, LAMBDA_IMPLICIT_ARG_TYPE);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void scan_testLambdasParensVsNone() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(
            LAMBDA_IMPLICIT_ARG_TYPE, LAMBDA_IMPLICIT_ARG_TYPE_NO_PARENS);
    assertThat(diff.getDiffReport()).isEmpty();
  }

  @Test
  public void scan_testLambdaVersusMethodReference() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(LAMBDA_1, METHOD_REFERENCE);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void scan_testLambdaVersusAnonymousClass() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(LAMBDA_1, ANONYMOUS_CLASS);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void scan_testTryWithResources() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(TRY_WITH_RESOURCES_1, TRY_WITH_RESOURCES_1);
    assertThat(diff.getDiffReport()).isEmpty();
  }

  @Test
  public void scan_testTryWithResourcesDifferent() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(TRY_WITH_RESOURCES_1, TRY_WITH_RESOURCES_2);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void scan_testAnnotatedType() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(ANNOTATED_TYPE_1, ANNOTATED_TYPE_1);
    assertThat(diff.getDiffReport()).isEmpty();
  }

  @Test
  public void scan_testAnnotatedTypeDifferent() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(ANNOTATED_TYPE_1, ANNOTATED_TYPE_2);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void scan_testMulticatch() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(MULTICATCH_1, MULTICATCH_1);
    assertThat(diff.getDiffReport()).isEmpty();
  }

  @Test
  public void scan_testMulticatchDifferent() {
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(MULTICATCH_1, MULTICATCH_2);
    assertThat(diff.isEmpty()).isFalse();
  }

  @Test
  public void matchCompilationUnits() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "",
            "import not.NotUsed;",
            "import is.IsUsed;",
            "",
            "public class HasExtras { ",
            "  private NotUsed skipped;",
            "  private Object matched;",
            "  private IsUsed usedFromImport;",
            "  private Object skipped2;",
            "",
            "  HasExtras() {}",
            "  HasExtras(int overloadedConstructor) {}",
            "",
            "  public String skippedMethod() { return null; }",
            "  public String matchedMethod() { return null; }",
            "  public Object overloadedMethod(int skipWithDifferentSignature) { return null; }",
            "  public String overloadedMethod(int i, Double d) { return null; }",
            "  public String overloadedMethod(int i, Double d, IsUsed u) { return null; }",
            "",
            "  class NestedClass {",
            "    int matchMe = 0;",
            "    double ignoreMe = 0;",
            "  }",
            "",
            "  class IgnoredNestedClass {}",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "import is.IsUsed;",
            "",
            "public class HasExtras { ",
            "  private Object matched;",
            "  private IsUsed usedFromImport;",
            "",
            "  HasExtras(int overloadedConstructor) {}",
            "",
            "  public String matchedMethod() { return null; }",
            "  public String overloadedMethod(int i, Double d) { return null; }",
            "  public String overloadedMethod(int i, Double d, IsUsed u) { return null; }",
            "",
            "  class NestedClass {",
            "    int matchMe = 0;",
            "  }",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDiffReport()).isEmpty();
  }

  @Test
  public void matchCompilationUnits_unresolvedTypeInPattern() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "",
            "import is.IsUsed;",
            "",
            "public class HasExtras { ",
            "  private IsUsed usedFromImport;",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "public class HasExtras { ",
            "  private IsUsed usedFromImport;",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDiffReport()).isEmpty();
  }

  @Test
  public void matchCompilationUnits_sameSignature_differentReturnType() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "public class HasExtras { ",
            "  private Object method(int i, double d) { return null; };",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "public class HasExtras { ",
            "  private String method(int i, double d) { return null; };",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDifferingNodes()).isNotEmpty();
  }

  @Test
  public void matchCompilationUnits_sameSignature_differentParameterNames() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "public class HasExtras { ",
            "  private Object method(int i, double d) { return null; };",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "public class HasExtras { ",
            "  private Object method(int i2, double d2) { return null; };",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDifferingNodes()).isNotEmpty();
  }

  @Test
  public void matchCompilationUnits_sameSignature_differentParameters() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "public class HasExtras { ",
            "  private Object method(int i, Object o) { return null; };",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "public class HasExtras { ",
            "  private Object method(int i2, @Nullable Object o) { return null; };",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDifferingNodes()).isNotEmpty();
  }

  @Test
  public void matchCompilationUnits_sameSignature_differentModifiers() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "public class HasExtras { ",
            "  private Object method(int i, Object o) { return null; };",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "public class HasExtras { ",
            "  public Object method(int i2, @Nullable Object o) { return null; };",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDifferingNodes()).isNotEmpty();
  }

  @Test
  public void matchCompilationUnits_sameSignature_differentThrows() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "public class HasExtras { ",
            "  private void method() throws RuntimeException {}",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "public class HasExtras { ",
            "  private void method() throws Error {}",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDifferingNodes()).isNotEmpty();
  }

  @Test
  public void matchCompilationUnits_variablesWithDifferentTypes() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "public class HasExtras { ",
            "  private Object field;",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "public class HasExtras { ",
            "  private String field;",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDifferingNodes()).isNotEmpty();
  }

  @Test
  public void matchCompilationUnits_importsWithSameSimpleName() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "",
            "import foo.Imported;",
            "",
            "public class HasExtras { ",
            "  private Imported field;",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "import bar.Imported;",
            "",
            "public class HasExtras { ",
            "  private Imported field;",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getExtraExpectedNodes()).isNotEmpty();
    assertThat(diff.getExtraActualNodes()).isEmpty();
  }

  @Test
  public void matchCompilationUnits_wrongOrder() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "",
            "class Foo {",
            "  private String method1() { return new String(); }",
            "  private String method2() { return new String(); }",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "class Foo {",
            "  private String method2() { return new String(); }",
            "  private String method1() { return new String(); }",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDifferingNodes()).isNotEmpty();
  }

  @Test
  public void matchCompilationUnits_missingParameter() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "",
            "class Foo {",
            "  private String method1(String s) { return s; }",
            "  private String method2() { return new String(); }",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "class Foo {",
            "  private String method1() { return s; }",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getExtraExpectedNodes()).isNotEmpty();
    assertThat(diff.getExtraActualNodes()).isEmpty();
  }

  @Test
  public void matchCompilationUnits_missingMethodBodyStatement() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "",
            "class Foo {",
            "  private String method1(String s) { ",
            "    System.out.println(s);",
            "    return s;",
            "  }",
            "  private String method2() { return new String(); }",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "class Foo {",
            "  private String method1(String s) { ",
            "    return s;",
            "  }",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getExtraActualNodes()).isNotEmpty();
  }

  @Test
  public void matchCompilationUnits_skipsImports() {
    ParseResult actual =
        MoreTrees.parseLines(
            "package test;",
            "",
            "import bar.Bar;",
            "",
            "class Foo {",
            "  private Bar bar;",
            "}");

    ParseResult pattern =
        MoreTrees.parseLines(
            "package test;",
            "",
            "class Foo {",
            "  private Bar bar;",
            "}");
    TreeDifference diff =
        TreeDiffer.matchCompilationUnits(
            getOnlyElement(pattern.compilationUnits()),
            pattern.trees(),
            getOnlyElement(actual.compilationUnits()),
            actual.trees());

    assertThat(diff.getDiffReport()).isEmpty();
  }

  private TreePath asPath(CompilationUnitTree compilationUnit) {
    return MoreTrees.findSubtreePath(compilationUnit, Tree.Kind.COMPILATION_UNIT);
  }

  private TreePath baseToStringTree() {
    return MoreTrees.findSubtreePath(EXPECTED_TREE, Tree.Kind.METHOD, "toString");
  }

  private TreePath diffToStringTree() {
    return MoreTrees.findSubtreePath(ACTUAL_TREE, Tree.Kind.METHOD, "toString");
  }

  private static class SimplifiedDiff {
    private final Tree.Kind kind;
    private final String details;

    SimplifiedDiff(Tree.Kind kind, String details) {
      this.kind = kind;
      this.details = details;
    }

    static SimplifiedDiff create(TreeDifference.OneWayDiff other) {
      return new SimplifiedDiff(other.getNodePath().getLeaf().getKind(), other.getDetails());
    }

    static SimplifiedDiff create(TreeDifference.TwoWayDiff other) {
      return new SimplifiedDiff(
          other.getExpectedNodePath().getLeaf().getKind(), other.getDetails());
    }

    @Override
    public String toString() {
      return String.format("%s: %s", this.kind.toString(), this.details.toString());
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      } else if (o instanceof SimplifiedDiff) {
        SimplifiedDiff that = (SimplifiedDiff) o;
        return this.kind.equals(that.kind)
            && this.details.equals(that.details);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return Objects.hash(kind, details);
    }
  }
}
