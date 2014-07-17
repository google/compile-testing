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
import static javax.tools.Diagnostic.NOPOS;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.Tree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

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
   * Returns start line of the given sub-{@code Tree} of this object's {@code CompilationUnitTree},
   * climbing the associated {@code TreePath} until a value other than
   * {@link javax.tools.Diagnostic.NOPOS} is found.
   *
   * <p>This method will return {@link javax.tools.Diagnostic.NOPOS} if that value is returned
   * by a call to {@link SourcePositions#getStartPosition} for every node in the {@link TreePath}
   * provided.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeStartLine(Tree node) {
    long startPosition = getNodeStartPosition(node);
    return startPosition == NOPOS ? NOPOS : lineMap.getLineNumber(startPosition);
  }

  /**
   * Returns start column of the given sub-{@code Tree} of this object's
   * {@code CompilationUnitTree}, climbing the associated {@code TreePath} until a value other than
   * {@link javax.tools.Diagnostic.NOPOS} is found.
   *
   * <p>This method will return {@link javax.tools.Diagnostic.NOPOS} if that value is returned
   * by a call to {@link SourcePositions#getStartPosition} for every node in the {@link TreePath}
   * provided.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeStartColumn(Tree node) {
    long startPosition = getNodeStartPosition(node);
    return startPosition == NOPOS ? NOPOS : lineMap.getColumnNumber(startPosition);
  }

  /**
   * Returns end line of the given sub-{@code Tree} of this object's {@code CompilationUnitTree}.
   * climbing the associated {@code TreePath} until a value other than
   * {@link javax.tools.Diagnostic.NOPOS} is found.
   *
   * <p>This method will return {@link javax.tools.Diagnostic.NOPOS} if that value is returned
   * by a call to {@link SourcePositions#getEndPosition} for every node in the {@link TreePath}
   * provided.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeEndLine(Tree node) {
    long endPosition = getNodeEndPosition(node);
    return endPosition == NOPOS ? NOPOS : lineMap.getLineNumber(endPosition);
  }

  /**
   * Returns end column of the given sub-{@code Tree} of this object's {@code CompilationUnitTree}.
   * climbing the associated {@code TreePath} until a value other than
   * {@link javax.tools.Diagnostic.NOPOS} is found.
   *
   * <p>This method will return {@link javax.tools.Diagnostic.NOPOS} if that value is returned
   * by a call to {@link SourcePositions#getEndPosition} for every node in the {@link TreePath}
   * provided.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeEndColumn(Tree node) {
    long endPosition = getNodeEndPosition(node);
    return endPosition == NOPOS ? NOPOS : lineMap.getColumnNumber(endPosition);
  }

  /**
   * Returns start position of the given sub-{@code Tree} of this object's
   * {@code CompilationUnitTree}, climbing the associated {@code TreePath} until a value other than
   * {@link javax.tools.Diagnostic.NOPOS} is found.
   *
   * <p>This method will return {@link javax.tools.Diagnostic.NOPOS} if that value is returned
   * by a call to {@link SourcePositions#getStartPosition} for every node in the {@link TreePath}
   * provided.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeStartPosition(Tree node) {
    TreePath currentNode = getNodePath(node);
    while (currentNode != null) {
      long startPosition = sourcePositions.getStartPosition(compilationUnit, currentNode.getLeaf());
      if (startPosition != NOPOS) {
        return startPosition;
      }
      currentNode = currentNode.getParentPath();
    }
    return NOPOS;
  }

  /**
   * Returns end position of the given sub-{@code Tree} of this object's
   * {@code CompilationUnitTree}, climbing the associated {@code TreePath} until a value other than
   * {@link javax.tools.Diagnostic.NOPOS} is found.
   *
   * <p>This method will return {@link javax.tools.Diagnostic.NOPOS} if that value is returned
   * by a call to {@link SourcePositions#getEndPosition} for every node in the {@link TreePath}
   * provided.
   *
   * @throws IllegalArgumentException if the node provided is not a sub-{@code Tree} of this
   *   object's {@code CompilationUnitTree}.
   */
  long getNodeEndPosition(Tree node) {
    TreePath currentNode = getNodePath(node);
    while (node != null) {
      long endPosition = sourcePositions.getEndPosition(compilationUnit, currentNode.getLeaf());
      if (endPosition != NOPOS) {
        return endPosition;
      }
      currentNode = currentNode.getParentPath();
    }
    return NOPOS;
  }
}