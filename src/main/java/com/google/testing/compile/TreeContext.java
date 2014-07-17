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

import static com.google.common.base.Preconditions.checkArgument;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;

/**
 * A class for managing and retrieving contextual information for Compilation Trees.
 *
 * <p>This class is used to pair a {@code CompilationUnitTree} with its corresponding {@code Trees},
 * {@code SourcePositions}, and {@code LineMap} instances. It acts as a client to the contextual
 * information these objects can provide for {@code Tree}s within the {@code CompilationUnitTree}.
 *
 * @author Stephen Pratt
 */
final class TreeContext {
  private final CompilationUnitTree compilationUnit;
  private final Trees trees;
  private final SourcePositions sourcePositions;
  private final LineMap lineMap;

  TreeContext(CompilationUnitTree compilationUnit, Trees trees) {
    this.compilationUnit = compilationUnit;
    this.trees = trees;
    this.sourcePositions = trees.getSourcePositions();
    this.lineMap = compilationUnit.getLineMap();
  }

  /** Returns the {@code CompilationUnitTree} instance for this {@code TreeContext}. */
  CompilationUnitTree getCompilationUnit() {
    return compilationUnit;
  }

  /** Returns the {@code Trees} instance for this {@code TreeContext}. */
  Trees getTrees() {
    return trees;
  }

  /**
   * Returns the {@code TreePath} to the given sub-{@code Tree} of this object's
   * {@code CompilationUnitTree}
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  TreePath getNodePath(Tree node) {
    TreePath treePath = trees.getPath(compilationUnit, node);
    checkArgument(treePath != null, "The node provided was not a subtree of the "
        + "CompilationUnitTree in this TreeContext. CompilationUnit: %s; Node:",
        compilationUnit, node);
    return treePath;
  }

  /**
   * Returns start line of the given sub-{@code Tree} of this object's {@code CompilationUnitTree}.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeStartLine(Tree node) {
    return lineMap.getLineNumber(getNodeStartPosition(node));
  }

  /**
   * Returns start column of the given sub-{@code Tree} of this object's
   * {@code CompilationUnitTree}.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeStartColumn(Tree node) {
    return lineMap.getColumnNumber(getNodeStartPosition(node));
  }

  /**
   * Returns end line of the given sub-{@code Tree} of this object's {@code CompilationUnitTree}.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeEndLine(Tree node) {
    return lineMap.getLineNumber(getNodeEndPosition(node));
  }

  /**
   * Returns end column of the given sub-{@code Tree} of this object's {@code CompilationUnitTree}.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeEndColumn(Tree node) {
    return lineMap.getColumnNumber(getNodeEndPosition(node));
  }

  /**
   * Returns start position of the given sub-{@code Tree} of this object's
   * {@code CompilationUnitTree}.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeStartPosition(Tree node) {
    long startPosition = sourcePositions.getStartPosition(compilationUnit, node);
    checkArgument(startPosition != Diagnostic.NOPOS,
        "The node provided was not a subtree of this context's CompilationUnitTree: %s", node);
    return startPosition;
  }

  /**
   * Returns end position of the given sub-{@code Tree} of this object's
   * {@code CompilationUnitTree}.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeEndPosition(Tree node) {
    long endPosition = sourcePositions.getEndPosition(compilationUnit, node);
    checkArgument(endPosition != Diagnostic.NOPOS,
        "The node provided was not a subtree of this context's CompilationUnitTree: %s", node);
    return endPosition;
  }
}