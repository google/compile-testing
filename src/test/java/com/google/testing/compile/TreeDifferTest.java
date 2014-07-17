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

import com.google.common.collect.ImmutableList;

import com.sun.source.tree.*;

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
  private static final CompilationUnitTree BASE_TREE =
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
          "        for (int x : numbers) {",
          "            if (x % 2 == 0) {",
          "                throw new IllegalStateException();",
          "            }",
          "        }",
          "    }",
          "}");
  private static final CompilationUnitTree DIFF_TREE =
      MoreTrees.parseLinesToTree("package test;",
          "import java.util.List;",
          "",
          "",
          "final class TestClass {",
          "    public String toString() {",
          "        Object variable = new Object();",
          "        return \"literal\" + variable;",
          "    }",
          "",
          "    public void nonsense() {",
          "        int[] numberz = {0, 1, 2, 3, 4, 5};",
          "        for (int x : numberz) {",
          "            if (x % 2 == 0) {",
          "                throw new RuntimeException();",
          "            }",
          "        }",
          "    }",
          "",
          "    public int extraNonsense() {",
          "      return 0;",
          "    }",
          "}");

  @Test
  public void scan_differingCompilationUnits() {
    TreeDifference diff = TreeDiffer.diffCompilationUnits(BASE_TREE, DIFF_TREE);
    ASSERT.that(diff.isEmpty()).isFalse();

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
            "Expected identifier to be <IllegalStateException> but was <RuntimeException>."));
    ASSERT.that(diff.getExtraNodesOnLeft().isEmpty()).isTrue();
    ASSERT.that(diff.getExtraNodesOnRight().size()).isEqualTo(extraNodesExpected.size());

    ImmutableList.Builder<SimplifiedDiff> extraNodesFound = new ImmutableList.Builder<>();
    for (TreeDifference.OneWayDiff extraNode : diff.getExtraNodesOnRight()) {
      extraNodesFound.add(SimplifiedDiff.create(extraNode));
    }
    ASSERT.that(extraNodesExpected).iteratesAs(extraNodesFound.build());
    ImmutableList.Builder<SimplifiedDiff> differingNodesFound = new ImmutableList.Builder<>();
    for (TreeDifference.TwoWayDiff differingNode : diff.getDifferingNodes()) {
      differingNodesFound.add(SimplifiedDiff.create(differingNode));
    }
    ASSERT.that(differingNodesExpected).iteratesAs(differingNodesFound.build());

  }

  @Test
  public void scan_sameCompilationUnit() {
    ASSERT.that(TreeDiffer.diffCompilationUnits(BASE_TREE, BASE_TREE).isEmpty()).isTrue();
  }

  @Test
  public void scan_identicalMethods() {
    ASSERT.that(
        TreeDiffer.diffSubtrees(BASE_TREE, baseToStringTree(), DIFF_TREE, diffToStringTree())
        .isEmpty()).isTrue();
  }

  @Test
  public void scan_differentTypes() {
    TreeDifference diff =
        TreeDiffer.diffSubtrees(BASE_TREE, BASE_TREE, DIFF_TREE, diffToStringTree());
    ASSERT.that(diff.isEmpty()).isFalse();
    for (TreeDifference.TwoWayDiff differingNode : diff.getDifferingNodes()) {
      ASSERT.that(differingNode.getDetails()).contains("Expected node kind to be");
    }
  }

  private Tree baseToStringTree() {
    return MoreTrees.findSubtree(BASE_TREE, Tree.Kind.METHOD, "toString");
  }

  private Tree diffToStringTree() {
    return MoreTrees.findSubtree(DIFF_TREE, Tree.Kind.METHOD, "toString");
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
      return new SimplifiedDiff(other.getLeftNodePath().getLeaf().getKind(), other.getDetails());
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