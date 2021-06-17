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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.testing.compile.Parser.ParseResult;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.Arrays;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A class containing methods which are useful for gaining access to {@code Tree} instances from
 * within unit tests.
 */
@SuppressWarnings("restriction") // Sun APIs usage intended
final class MoreTrees {

  /** Parses the source given into a {@link CompilationUnitTree}. */
  static CompilationUnitTree parseLinesToTree(String... source) {
    return parseLinesToTree(Arrays.asList(source));
  }

  /** Parses the source given into a {@link CompilationUnitTree}. */
  static CompilationUnitTree parseLinesToTree(Iterable<String> source) {
    Iterable<? extends CompilationUnitTree> parseResults =
        Parser.parse(ImmutableList.of(JavaFileObjects.forSourceLines("", source)))
            .compilationUnits();
    return Iterables.getOnlyElement(parseResults);
  }

  /** Parses the source given and produces a {@link ParseResult}. */
  static ParseResult parseLines(String... source) {
    return parseLines(Arrays.asList(source));
  }

  /** Parses the source given and produces a {@link ParseResult}. */
  static ParseResult parseLines(Iterable<String> source) {
    return Parser.parse(ImmutableList.of(JavaFileObjects.forSourceLines("", source)));
  }

  /**
   * Finds the first instance of the given {@link Tree.Kind} that is a subtree of the root provided.
   *
   * @throws IllegalArgumentException if no such subtree exists.
   */
  static Tree findSubtree(CompilationUnitTree root, Tree.Kind treeKind) {
    return findSubtree(root, treeKind, null);
  }

  /**
   * Finds the first instance of the given {@link Tree.Kind} that is a subtree of the root provided
   * and which matches identifier string.
   *
   * <p>See the doc on {@link #findSubtreePath} for details on the identifier param.
   *
   * @throws IllegalArgumentException if no such subtree exists.
   */
  static Tree findSubtree(
      CompilationUnitTree root, Tree.Kind treeKind, @Nullable String identifier) {
    return findSubtreePath(root, treeKind, identifier).getLeaf();
  }

  /**
   * Finds a path to the first instance of the given {@link Tree.Kind} that is a subtree of the root
   * provided.
   *
   * @throws IllegalArgumentException if no such subtree exists.
   */
  static TreePath findSubtreePath(CompilationUnitTree root, Tree.Kind treeKind) {
    return findSubtreePath(root, treeKind, null);
  }

  /**
   * Finds a TreePath terminating at the first instance of the given {@link Tree.Kind} that is a
   * subtree of the root provided and which matches the optional identifier string.
   *
   * <p>Identifier strings are only valid for some {@link Tree} and may take different meanings. The
   * following list provides a quick summary of the matching behavior:
   * <ul>
   * <li>{@link Tree}s with kind {@code BREAK}, {@code CONTINUE}, and {@code LABELED_STATEMENT}
   * match on their {@code getLabel()} methods.
   * <li>{@link Tree}s with kind {@code ANNOTATION_TYPE}, {@code CLASS}, {@code ENUM},
   * and {@code INTERFACE} match on their {@code getSimpleName()} method.
   * <li>{@link Tree}s with kind {@code *_LITERAL} match on their {@code getValue()} method.
   * <li>{@link Tree}s with kind {@code IDENTIFIER}, {@code METHOD}, and {@code TYPE_PARAMETER}
   * match on their {@code getName()} method.
   * <li>{@link Tree}s with kind {@code MEMBER_SELECT} matches on their {@code getIdentifier()}
   * method.
   *
   * @throws IllegalArgumentException if no such subtree exists or if an identifier-based match
   * is requested for any type but one of the following:
   */
  static TreePath findSubtreePath(CompilationUnitTree root, Tree.Kind treeKind,
      @Nullable String identifier) {
    SearchScanner subtreeFinder = new SearchScanner(treeKind, Optional.ofNullable(identifier));
    Optional<TreePath> res = subtreeFinder.scan(root, null);
    Preconditions.checkArgument(res.isPresent(), "Couldn't find any subtree matching the given "
        + "criteria. Root: %s, Class: %s, Identifier: %s", root, treeKind, identifier);
    return res.get();
  }

  /** A {@link TreePathScanner} to power the subtree searches in this class */
  static final class SearchScanner extends TreePathScanner<Optional<TreePath>, @Nullable Void> {
    private final Optional<String> identifier;
    private final Tree.Kind kindSought;

    public SearchScanner(Tree.Kind kindSought, Optional<String> identifier) {
      this.kindSought = kindSought;
      this.identifier = identifier;
    }

    /**
     * Returns {@code true} iff the node and corresponding id value provided match the identifier
     * and kind sought.
     */
    private boolean isMatch(Tree node, Optional<Object> idValue) {
      boolean idsMatch;
      if (!identifier.isPresent()) {
        idsMatch = true;
      } else if (!idValue.isPresent()) {
        idsMatch = false;
      } else {
        idsMatch = identifier.get().equals(idValue.get().toString());
      }
      return kindSought.equals(node.getKind()) && idsMatch;
    }

    /**
     * Returns {@code true} iff the node and corresponding id value provided match the identifier
     * and kind sought.
     */
    private boolean isMatch(Tree node, Object idValue) {
      return isMatch(node, Optional.ofNullable(idValue));
    }

    /** Returns a TreePath that includes the current path plus the node provided */
    private Optional<TreePath> currentPathPlus(Tree node) {
      return Optional.of(new TreePath(getCurrentPath(), node));
    }

    /**
     * Returns the {@code Optional} value given, or {@code Optional.empty()} if the value given was
     * {@code null}.
     */
    private Optional<TreePath> absentIfNull(Optional<TreePath> ret) {
      return (ret != null) ? ret : Optional.empty();
    }

    @Override
    public Optional<TreePath> scan(Tree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      }

      return isMatch(node, Optional.empty())
          ? currentPathPlus(node)
          : absentIfNull(super.scan(node, v));
    }

    @Override
    public Optional<TreePath> scan(Iterable<? extends Tree> nodes, @Nullable Void v) {
      return absentIfNull(super.scan(nodes, v));
    }

    /** Returns the first present value. If both values are absent, then returns absent .*/
    @Override
    public Optional<TreePath> reduce(Optional<TreePath> t1, Optional<TreePath> t2) {
      return t1.isPresent() ? t1 : t2;
    }

    @Override
    public Optional<TreePath> visitBreak(@Nullable BreakTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      }

      return isMatch(node, node.getLabel()) ? currentPathPlus(node) : Optional.empty();
    }

    @Override
    public Optional<TreePath> visitClass(@Nullable ClassTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getSimpleName())) {
        return currentPathPlus(node);
      }

      return super.visitClass(node, v);
    }

    @Override
    public Optional<TreePath> visitContinue(@Nullable ContinueTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getLabel())) {
        return currentPathPlus(node);
      }

      return super.visitContinue(node, v);
    }

    @Override
    public Optional<TreePath> visitIdentifier(@Nullable IdentifierTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getName())) {
        return currentPathPlus(node);
      }

      return super.visitIdentifier(node, v);
    }

    @Override
    public Optional<TreePath> visitLabeledStatement(
        @Nullable LabeledStatementTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getLabel())) {
        return currentPathPlus(node);
      }

      return super.visitLabeledStatement(node, v);
    }

    @Override
    public Optional<TreePath> visitLiteral(@Nullable LiteralTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getValue())) {
        return currentPathPlus(node);
      }

      return super.visitLiteral(node, v);
    }

    @Override
    public Optional<TreePath> visitMethod(@Nullable MethodTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getName())) {
        return currentPathPlus(node);
      }

      return super.visitMethod(node, v);
    }

    @Override
    public Optional<TreePath> visitMemberSelect(@Nullable MemberSelectTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getIdentifier())) {
        return currentPathPlus(node);
      }

      return super.visitMemberSelect(node, v);
    }

    @Override
    public Optional<TreePath> visitTypeParameter(
        @Nullable TypeParameterTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getName())) {
        return currentPathPlus(node);
      }

      return super.visitTypeParameter(node, v);
    }

    @Override
    public Optional<TreePath> visitVariable(@Nullable VariableTree node, @Nullable Void v) {
      if (node == null) {
        return Optional.empty();
      } else if (isMatch(node, node.getName())) {
        return currentPathPlus(node);
      }

      return super.visitVariable(node, v);
    }
  }
}
