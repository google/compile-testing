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
    ASSERT.that(onlyExpectedDiffs().isEmpty()).isFalse();
    ASSERT.that(onlyActualDiffs().isEmpty()).isFalse();
    ASSERT.that(twoWayDiffs().isEmpty()).isFalse();
    ASSERT.that(multiDiffs().isEmpty()).isFalse();
  }

  @Test
  public void getExtraExpectedNodes() {
    ASSERT.that(emptyDiff().getExtraExpectedNodes().size()).isEqualTo(0);
    ASSERT.that(onlyExpectedDiffs().getExtraExpectedNodes().size()).isEqualTo(2);
    ASSERT.that(onlyActualDiffs().getExtraExpectedNodes().size()).isEqualTo(0);
    ASSERT.that(twoWayDiffs().getExtraExpectedNodes().size()).isEqualTo(0);
    ASSERT.that(multiDiffs().getExtraExpectedNodes().size()).isEqualTo(1);
  }

  @Test
  public void getExtraActualNodes() {
    ASSERT.that(emptyDiff().getExtraActualNodes().size()).isEqualTo(0);
    ASSERT.that(onlyExpectedDiffs().getExtraActualNodes().size()).isEqualTo(0);
    ASSERT.that(onlyActualDiffs().getExtraActualNodes().size()).isEqualTo(2);
    ASSERT.that(twoWayDiffs().getExtraActualNodes().size()).isEqualTo(0);
    ASSERT.that(multiDiffs().getExtraActualNodes().size()).isEqualTo(1);
  }

  @Test
  public void getDifferingNodes() {
    ASSERT.that(emptyDiff().getDifferingNodes().size()).isEqualTo(0);
    ASSERT.that(onlyExpectedDiffs().getDifferingNodes().size()).isEqualTo(0);
    ASSERT.that(onlyActualDiffs().getDifferingNodes().size()).isEqualTo(0);
    ASSERT.that(twoWayDiffs().getDifferingNodes().size()).isEqualTo(2);
    ASSERT.that(multiDiffs().getDifferingNodes().size()).isEqualTo(1);
  }

  @Test
  public void getDiffReport_NoContext() {
    ASSERT.that(emptyDiff().getDiffReport() != null).isTrue();
    ASSERT.that(onlyExpectedDiffs().getDiffReport())
        .contains("unmatched nodes in the expected tree");
    ASSERT.that(onlyExpectedDiffs().getDiffReport()).contains(expectedDiffMessage());
    ASSERT.that(onlyActualDiffs().getDiffReport()).contains("unmatched nodes in the actual tree");
    ASSERT.that(onlyActualDiffs().getDiffReport()).contains(actualDiffMessage());
    ASSERT.that(twoWayDiffs().getDiffReport()).contains("differed in expected and actual");
    ASSERT.that(twoWayDiffs().getDiffReport()).contains(twoWayDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport()).contains(expectedDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport()).contains(actualDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport()).contains(twoWayDiffMessage());
  }

  @Test
  public void getDiffReport_WithContext() {
    ASSERT.that(emptyDiff().getDiffReport(treeContext(), treeContext()) != null).isTrue();
    ASSERT.that(onlyExpectedDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(expectedDiffMessage());
    ASSERT.that(onlyExpectedDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(expectedDiffContextStr());
    ASSERT.that(onlyActualDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(actualDiffMessage());
    ASSERT.that(onlyActualDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(actualDiffContextStr());
    ASSERT.that(twoWayDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(twoWayDiffMessage());
    ASSERT.that(twoWayDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(twoWayDiffContextStr());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(expectedDiffContextStr());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(actualDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(actualDiffContextStr());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(twoWayDiffMessage());
    ASSERT.that(multiDiffs().getDiffReport(treeContext(), treeContext()))
        .contains(twoWayDiffContextStr());
  }

  @Test
  public void getDiffReport_emptyElementContext() {
    CompilationUnitTree modifiersPresent =
      MoreTrees.parseLinesToTree("package test;",
          "final class TestClass {",
          "   TestClass() {}",
          "}");
    CompilationUnitTree modifiersAbsent =
      MoreTrees.parseLinesToTree("package test;",
          "class TestClass {",
          "   TestClass() {}",
          "}");
    TreeDifference diff =
        TreeDiffer.diffCompilationUnits(modifiersPresent, modifiersAbsent);
    ASSERT.that(
        diff.getDiffReport(treeContext(modifiersPresent), treeContext(modifiersAbsent))
        .isEmpty()).isFalse();
    diff = TreeDiffer.diffCompilationUnits(modifiersAbsent, modifiersPresent);
    ASSERT.that(
        diff.getDiffReport(treeContext(modifiersAbsent), treeContext(modifiersPresent))
        .isEmpty()).isFalse();
  }

  private TreeDifference emptyDiff() {
    return new TreeDifference();
  }

  private TreeDifference onlyExpectedDiffs() {
    return new TreeDifference.Builder()
        .addExtraExpectedNode(expectedDiffSubtree(), expectedDiffMessage())
        .addExtraExpectedNode(expectedDiffSubtree(), expectedDiffMessage())
        .build();
  }

  private String expectedDiffMessage() {
    return "expected";
  }

  private TreePath expectedDiffSubtree() {
    return MoreTrees.findSubtreePath(COMPILATION_UNIT, Tree.Kind.METHOD);
  }

  private String expectedDiffContextStr() {
    return Tree.Kind.METHOD.toString();
  }

  private TreeDifference onlyActualDiffs() {
    return new TreeDifference.Builder()
        .addExtraActualNode(actualDiffSubtree(), actualDiffMessage())
        .addExtraActualNode(actualDiffSubtree(), actualDiffMessage())
        .build();
  }

  private String actualDiffMessage() {
    return "actual";
  }

  private TreePath actualDiffSubtree() {
    return MoreTrees.findSubtreePath(COMPILATION_UNIT, Tree.Kind.THROW);
  }

  private String actualDiffContextStr() {
    return Tree.Kind.THROW.toString();
  }

  private TreeDifference twoWayDiffs() {
    return new TreeDifference.Builder()
        .addDifferingNodes(expectedDiffSubtree(), actualDiffSubtree(), twoWayDiffMessage())
        .addDifferingNodes(actualDiffSubtree(), expectedDiffSubtree(), twoWayDiffMessage())
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
        .addExtraExpectedNode(expectedDiffSubtree(), expectedDiffMessage())
        .addExtraActualNode(actualDiffSubtree(), actualDiffMessage())
        .addDifferingNodes(expectedDiffSubtree(), actualDiffSubtree(), twoWayDiffMessage())
        .build();
  }

  private TreeContext treeContext() {
    return new TreeContext(COMPILATION_UNIT, TREES);
  }

  private TreeContext treeContext(CompilationUnitTree compilationUnit) {
    return new TreeContext(compilationUnit, TREES);
  }
}
