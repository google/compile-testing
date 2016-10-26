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
import com.google.testing.compile.Parser.ParseResult;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A unit test for {@link TreeContext}.
 */
@RunWith(JUnit4.class)
public class TreeContextTest {
  @Rule public final ExpectedException expectedExn = ExpectedException.none();
  private static final String LITERAL_VALUE = "literal";
  private static final ImmutableList<String> baseTreeSource = ImmutableList.of(
      "package test;",
      "",
      "final class TestClass {",
      "    public String toString() {",
      "        Object variable = new Object();",
      "        return \"" + LITERAL_VALUE + "\" + variable;",
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

  private static final ParseResult PARSE_RESULTS = MoreTrees.parseLines(baseTreeSource);
  private static final CompilationUnitTree COMPILATION_UNIT =
      PARSE_RESULTS.compilationUnits().iterator().next();
  private static final Trees TREES = PARSE_RESULTS.trees();


  @Test
  public void getPositionInfo() {
    assertThat(treeContext().getNodeStartLine(compilationSubtree())).isEqualTo(subtreeStartLine());
    assertThat(treeContext().getNodeEndLine(compilationSubtree())).isEqualTo(subtreeEndLine());
    assertThat(treeContext().getNodeStartColumn(compilationSubtree()))
        .isEqualTo(subtreeStartColumn());
    assertThat(treeContext().getNodeEndColumn(compilationSubtree())).isEqualTo(subtreeEndColumn());
  }

  @Test
  public void getPositionInfo_invalid() {
    expectedExn.expect(IllegalArgumentException.class);
    long unused = treeContext().getNodeStartLine(invalidNode());
    expectedExn.expect(IllegalArgumentException.class);
    unused = treeContext().getNodeStartColumn(invalidNode());
    expectedExn.expect(IllegalArgumentException.class);
    unused = treeContext().getNodeEndLine(invalidNode());
    expectedExn.expect(IllegalArgumentException.class);
    unused = treeContext().getNodeEndColumn(invalidNode());
  }

  @Test
  public void getNodePath() {
    assertThat(treeContext().getNodePath(compilationSubtree()).getCompilationUnit())
        .isEqualTo(COMPILATION_UNIT);
  }

  @Test
  public void getNodePath_invalid() {
    expectedExn.expect(IllegalArgumentException.class);
    TreePath unused = treeContext().getNodePath(invalidNode());
  }

  private TreeContext treeContext() {
    return new TreeContext(COMPILATION_UNIT, TREES);
  }

  private Tree compilationSubtree() {
    return MoreTrees.findSubtree(
        COMPILATION_UNIT, Tree.Kind.STRING_LITERAL, LITERAL_VALUE);
  }

  private int subtreeStartIdx() {
    for (int i = 0; i < baseTreeSource.size(); i++) {
      if (baseTreeSource.get(i).contains(LITERAL_VALUE)) {
        return i;
      }
    }
    throw new IllegalStateException(String.format("Couldn't find a literal with value \"%s\" in "
            + "the test tree source", LITERAL_VALUE));
  }

  private long subtreeStartLine() {
    return subtreeStartIdx() + 1;  // Line numbers start at 1
  }

  private long subtreeEndLine() {
    return subtreeStartLine();
  }

  private long subtreeStartColumn() {
    return baseTreeSource.get(subtreeStartIdx()).indexOf(LITERAL_VALUE);
  }

  private long subtreeEndColumn() {
    // Add one character for the quote and another to go past the end of the literal
    return subtreeStartColumn() + LITERAL_VALUE.length() + 2;
  }

  private Tree invalidNode() {
    return MoreTrees.parseLinesToTree(
        "package test;",
        "",
        "final class OtherClass {}");
  }
}
