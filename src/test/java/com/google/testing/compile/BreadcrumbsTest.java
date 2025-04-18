/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.testing.compile;

import static com.google.common.truth.Truth.assertThat;

import com.google.testing.compile.Breadcrumbs.BreadcrumbVisitor;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * A test for {@link BreadcrumbVisitor}
 */
@RunWith(JUnit4.class)
public class BreadcrumbsTest {
  @Rule public final ExpectedException expectedExn = ExpectedException.none();
  private static final Breadcrumbs.BreadcrumbVisitor BREADCRUMBS =
      new Breadcrumbs.BreadcrumbVisitor();

  @Test
  public void describeTreePath() {
    assertThat(Breadcrumbs.describeTreePath(treePath()))
        .contains(classTree().getSimpleName().toString());
  }

  @Test
  public void getDescriptor_method() {
    assertThat(methodTree().accept(BREADCRUMBS, null)).contains(methodTree().getKind().toString());
    assertThat(methodTree().accept(BREADCRUMBS, null)).contains(methodTree().getName().toString());
  }

  @Test
  public void getDescriptor_literal() {
    assertThat(literalTree().accept(BREADCRUMBS, null))
        .contains(literalTree().getKind().toString());
    assertThat(literalTree().accept(BREADCRUMBS, null))
        .contains(literalTree().getValue().toString());
  }

  @Test
  public void getDescriptor_class() {
    assertThat(classTree().accept(BREADCRUMBS, null)).contains(classTree().getKind().toString());
    assertThat(classTree().accept(BREADCRUMBS, null))
        .contains(classTree().getSimpleName().toString());
  }

  @Test
  public void getDescriptor_variable() {
    assertThat(variableTree().accept(BREADCRUMBS, null))
        .contains(variableTree().getKind().toString());
    assertThat(variableTree().accept(BREADCRUMBS, null))
        .contains(variableTree().getName().toString());
  }

  @Test
  public void getDescriptor_others() {
    for (Tree tree : treePath()) {
      assertThat(tree.accept(BREADCRUMBS, null)).contains(tree.getKind().toString());
    }
  }

  private CompilationUnitTree baseTree() {
    return MoreTrees.parseLinesToTree("package test;",
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
  }

  private MethodTree methodTree() {
     // Checked implicitly in CompilationTestUtils by Tree.Kind parameter
    @SuppressWarnings("unchecked")
    MethodTree ret = (MethodTree) MoreTrees.findSubtree(baseTree(), Tree.Kind.METHOD, "toString");
    return ret;
  }

  private LiteralTree literalTree() {
     // Checked implicitly in CompilationTestUtils by Tree.Kind parameter
    @SuppressWarnings("unchecked")
    LiteralTree ret =
        (LiteralTree) MoreTrees.findSubtree(baseTree(), Tree.Kind.STRING_LITERAL, "literal");
    return ret;
  }

  private ClassTree classTree() {
     // Checked implicitly in CompilationTestUtils by Tree.Kind parameter
    @SuppressWarnings("unchecked")
    ClassTree ret = (ClassTree) MoreTrees.findSubtree(baseTree(), Tree.Kind.CLASS, "TestClass");
    return ret;
  }

  private VariableTree variableTree() {
     // Checked implicitly in CompilationTestUtils by Tree.Kind parameter
    @SuppressWarnings("unchecked")
    VariableTree ret =
        (VariableTree) MoreTrees.findSubtree(baseTree(), Tree.Kind.VARIABLE, "variable");
    return ret;
  }

  private TreePath treePath() {
    return MoreTrees.findSubtreePath(baseTree(), Tree.Kind.THROW);
  }
}
