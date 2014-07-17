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

import com.google.common.collect.Iterables;

import com.sun.source.tree.*;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A unit test for {@link TreeDifference}
 */
@RunWith(JUnit4.class)
public class TreeDifferenceTest {
  private static final Compilation.ParseResult PARSE_RESULTS =
      MoreTrees.parseLines("package test;",
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
  private static final CompilationUnitTree COMPILATION_UNIT =
      Iterables.getOnlyElement(PARSE_RESULTS.compilationUnits());
  private static final Trees TREES = PARSE_RESULTS.trees();

  @Test
  public void isEmpty() {
    ASSERT.that(emptyDiff().isEmpty()).isTrue();
    ASSERT.that(onlyLeftDiffs().isEmpty()).isFalse();
    ASSERT.that(onlyRightDiffs().isEmpty()).isFalse();
    ASSERT.that(twoWayDiffs().isEmpty()).isFalse();
    ASSERT.that(multiDiffs().isEmpty()).isFalse();
  }

  @Test
  public void getExtraNodesOnLeft() {
    ASSERT.that(emptyDiff().getExtraNodesOnLeft().size()).isEqualTo(0);
    ASSERT.that(onlyLeftDiffs().getExtraNodesOnLeft().size()).isEqualTo(2);
    ASSERT.that(onlyRightDiffs().getExtraNodesOnLeft().size()).isEqualTo(0);
    ASSERT.that(twoWayDiffs().getExtraNodesOnLeft().size()).isEqualTo(0);
    ASSERT.that(multiDiffs().getExtraNodesOnLeft().size()).isEqualTo(1);
  }

  @Test
  public void getExtraNodesOnRight() {
    ASSERT.that(emptyDiff().getExtraNodesOnRight().size()).isEqualTo(0);
    ASSERT.that(onlyLeftDiffs().getExtraNodesOnRight().size()).isEqualTo(0);
    ASSERT.that(onlyRightDiffs().getExtraNodesOnRight().size()).isEqualTo(2);
    ASSERT.that(twoWayDiffs().getExtraNodesOnRight().size()).isEqualTo(0);
    ASSERT.that(multiDiffs().getExtraNodesOnRight().size()).isEqualTo(1);
  }

  @Test
  public void getDifferingNodes() {
    ASSERT.that(emptyDiff().getDifferingNodes().size()).isEqualTo(0);
    ASSERT.that(onlyLeftDiffs().getDifferingNodes().size()).isEqualTo(0);
    ASSERT.that(onlyRightDiffs().getDifferingNodes().size()).isEqualTo(0);
    ASSERT.that(twoWayDiffs().getDifferingNodes().size()).isEqualTo(2);
    ASSERT.that(multiDiffs().getDifferingNodes().size()).isEqualTo(1);
  }

  @Test
  public void getDiffReport_NoContext() {
    ASSERT.that(emptyDiff().getDiffReport() != null).isTrue();
    ASSERT.that(onlyLeftDiffs().getDiffReport()).contains("unmatched nodes in the expected tree");
    ASSERT.that(onlyLeftDiffs().getDiffReport()).contains(leftDiffMessage());
    ASSERT.that(onlyRightDiffs().getDiffReport()).contains("unmatched nodes in the actual tree");
    ASSERT.that(onlyRightDiffs().getDiffReport()).contains(rightDiffMessage());
    ASSERT.that(twoWayDiffs().getDiffReport()).contains("differed in expected and actual");
    ASSERT.that(twoWayDiffs().getDiffReport()).contains(twoWayDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport()).contains(leftDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport()).contains(rightDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport()).contains(twoWayDiffMessage());
  }

  @Test
  public void getDiffReport_WithContext() {
    ASSERT.that(emptyDiff().getDiffReport(treeContext(), treeContext()) != null).isTrue();
    ASSERT.that(onlyLeftDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(leftDiffMessage());
    ASSERT.that(onlyLeftDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(leftDiffContextStr());
    ASSERT.that(onlyRightDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(rightDiffMessage());
    ASSERT.that(onlyRightDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(rightDiffContextStr());
    ASSERT.that(twoWayDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(twoWayDiffMessage());
    ASSERT.that(twoWayDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(twoWayDiffContextStr());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(leftDiffContextStr());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(rightDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(rightDiffContextStr());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(twoWayDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(twoWayDiffContextStr());
  }

  private TreeDifference emptyDiff() {
    return new TreeDifference();
  }

  private TreeDifference onlyLeftDiffs() {
    return new TreeDifference.Builder()
        .addExtraNodeOnLeft(leftDiffSubtree(), leftDiffMessage())
        .addExtraNodeOnLeft(leftDiffSubtree(), leftDiffMessage())
        .build();
  }

  private String leftDiffMessage() {
    return "left";
  }

  private TreePath leftDiffSubtree() {
    return MoreTrees.findSubtreePath(COMPILATION_UNIT, Tree.Kind.METHOD);
  }

  private String leftDiffContextStr() {
    return Tree.Kind.METHOD.toString();
  }

  private TreeDifference onlyRightDiffs() {
    return new TreeDifference.Builder()
        .addExtraNodeOnRight(rightDiffSubtree(), rightDiffMessage())
        .addExtraNodeOnRight(rightDiffSubtree(), rightDiffMessage())
        .build();
  }

  private String rightDiffMessage() {
    return "right";
  }

  private TreePath rightDiffSubtree() {
    return MoreTrees.findSubtreePath(COMPILATION_UNIT, Tree.Kind.THROW);
  }

  private String rightDiffContextStr() {
    return Tree.Kind.THROW.toString();
  }

  private TreeDifference twoWayDiffs() {
    return new TreeDifference.Builder()
        .addDifferingNodes(leftDiffSubtree(), rightDiffSubtree(), twoWayDiffMessage())
        .addDifferingNodes(rightDiffSubtree(), leftDiffSubtree(), twoWayDiffMessage())
        .build();
  }

  private String twoWayDiffMessage() {
    return "center";
  }

  private String twoWayDiffContextStr() {
    return Tree.Kind.COMPILATION_UNIT.toString();
  }

  private TreeDifference multiDiffs() {
    return new TreeDifference.Builder()
        .addExtraNodeOnLeft(leftDiffSubtree(), leftDiffMessage())
        .addExtraNodeOnRight(rightDiffSubtree(), rightDiffMessage())
        .addDifferingNodes(leftDiffSubtree(), rightDiffSubtree(), twoWayDiffMessage())
        .build();
  }

  private TreeContext treeContext() {
    return new TreeContext(COMPILATION_UNIT, TREES);
  }
}
