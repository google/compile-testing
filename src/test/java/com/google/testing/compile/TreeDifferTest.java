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

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A test for {@link DetailedEqualityScanner}
 */
@RunWith(JUnit4.class)
public class TreeDifferTest {
  @Rule public final ExpectedException expectedExn = ExpectedException.none();
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
  @Test
  public void scan_differingCompilationUnits() {
    TreeDifference diff = TreeDiffer.diffCompilationUnits(EXPECTED_TREE, ACTUAL_TREE);
    assertThat(diff.isEmpty()).isFalse();

    ImmutableList<SimplifiedDiff> extraNodesExpected = ImmutableList.of(
        new SimplifiedDiff(Tree.Kind.INT_LITERAL, ""),
        new SimplifiedDiff(Tree.Kind.METHOD, ""));

    ImmutableList<SimplifiedDiff> differingNodesExpected = ImmutableList.of(
        new SimplifiedDiff(Tree.Kind.MEMBER_SELECT,
            "Expected member identifier to be <Set> but was <List>."),
        new SimplifiedDiff(Tree.Kind.VARIABLE,
            "Expected variable name to be <numbers> but was <numberz>."),
        new SimplifiedDiff(Tree.Kind.IDENTIFIER,
            "Expected identifier to be <numbers> but was <numberz>."),
        new SimplifiedDiff(Tree.Kind.IDENTIFIER,
            "Expected identifier to be <IllegalStateException> but was <RuntimeException>."),
        new SimplifiedDiff(Tree.Kind.BREAK,
            "Expected label on break statement to be <loop> but was <null>."));
    assertThat(diff.getExtraExpectedNodes().isEmpty()).isTrue();
    assertThat(diff.getExtraActualNodes().size()).isEqualTo(extraNodesExpected.size());

    ImmutableList.Builder<SimplifiedDiff> extraNodesFound =
        new ImmutableList.Builder<SimplifiedDiff>();
    for (TreeDifference.OneWayDiff extraNode : diff.getExtraActualNodes()) {
      extraNodesFound.add(SimplifiedDiff.create(extraNode));
    }
    assertThat(extraNodesExpected).containsExactlyElementsIn(extraNodesFound.build()).inOrder();
    ImmutableList.Builder<SimplifiedDiff> differingNodesFound =
        new ImmutableList.Builder<SimplifiedDiff>();
    for (TreeDifference.TwoWayDiff differingNode : diff.getDifferingNodes()) {
      differingNodesFound.add(SimplifiedDiff.create(differingNode));
    }
    assertThat(differingNodesExpected)
        .containsExactlyElementsIn(differingNodesFound.build())
        .inOrder();
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
          .contains("Expected literal value to be <3> but was <4>");
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

    Tree.Kind getKind() {
      return kind;
    }

    String getDetails() {
      return details;
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
      if (!(o instanceof SimplifiedDiff)) {
        return false;
      }

      // Checked by the above instanceof
      @SuppressWarnings("unchecked")
      SimplifiedDiff otherDiff = (SimplifiedDiff) o;
      return otherDiff.kind.equals(this.kind) && otherDiff.details.equals(this.details);
    }
  }
}
