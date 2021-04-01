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

import static javax.tools.Diagnostic.NOPOS;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

import javax.annotation.Nullable;

/**
 * A data structure describing the set of syntactic differences between two {@link Tree}s.
 *
 * @author Stephen Pratt
 */
final class TreeDifference {
  private static final String NO_LINE = "[unavailable]";

  private final ImmutableList<OneWayDiff> extraExpectedNodes;
  private final ImmutableList<OneWayDiff> extraActualNodes;
  private final ImmutableList<TwoWayDiff> differingNodes;

  /** Constructs an empty {@code TreeDifference}. */
  TreeDifference() {
    this.extraExpectedNodes = ImmutableList.<OneWayDiff>of();
    this.extraActualNodes = ImmutableList.<OneWayDiff>of();
    this.differingNodes = ImmutableList.<TwoWayDiff>of();
  }

  /** Constructs a {@code TreeDifference} that includes the given diffs. */
  TreeDifference(ImmutableList<OneWayDiff> extraExpectedNodes,
      ImmutableList<OneWayDiff> extraActualNodes, ImmutableList<TwoWayDiff> differingNodes) {
    this.extraExpectedNodes = extraExpectedNodes;
    this.extraActualNodes = extraActualNodes;
    this.differingNodes = differingNodes;
  }

  /** Returns {@code true} iff there are no diffs. */
  boolean isEmpty() {
    return extraExpectedNodes.isEmpty() && extraActualNodes.isEmpty() && differingNodes.isEmpty();
  }

  /**
   * Returns {@code OneWayDiff}s describing nodes on the expected tree that are unmatched on the
   * actual tree.
   */
  ImmutableList<OneWayDiff> getExtraExpectedNodes() {
    return extraExpectedNodes;
  }

  /**
   * Returns {@code OneWayDiff}s describing nodes on the actual tree that are unmatched on the
   * expected tree.
   */
  ImmutableList<OneWayDiff> getExtraActualNodes() {
    return extraActualNodes;
  }

  /** Returns {@code TwoWayDiff}s describing nodes that differ on the expected and actual trees. */
  ImmutableList<TwoWayDiff> getDifferingNodes() {
    return differingNodes;
  }

  /**
   * Returns a {@code String} reporting all diffs known to this {@code TreeDifference}. No context
   * will be provided in the report.
   */
  String getDiffReport() {
    return getDiffReport(null, null);
  }

  /**
   * Returns a {@code String} reporting all diffs known to this {@code TreeDifference}. If an
   * expected or actual {@code TreeContext} is provided, then it will be used to contextualize
   * corresponding entries in the report.
   */
  String getDiffReport(@Nullable TreeContext expectedContext, @Nullable TreeContext actualContext) {
    ImmutableList.Builder<String> reportLines = new ImmutableList.Builder<String>();
    if (!extraExpectedNodes.isEmpty()) {
      reportLines.add(String.format("Found %s unmatched nodes in the expected tree. %n",
              extraExpectedNodes.size()));
      for (OneWayDiff diff : extraExpectedNodes) {
        reportLines.add(
            createMessage(diff.getDetails(), diff.getNodePath(), expectedContext, true));
      }
    }
    if (!extraActualNodes.isEmpty()) {
      reportLines.add(String.format("Found %s unmatched nodes in the actual tree. %n",
              extraActualNodes.size()));
      for (OneWayDiff diff : extraActualNodes) {
        reportLines.add(
            createMessage(diff.getDetails(), diff.getNodePath(), actualContext, false));
      }
    }
    if (!differingNodes.isEmpty()) {
      reportLines.add(String.format("Found %s nodes that differed in expected and actual trees. %n",
              differingNodes.size()));
      for (TwoWayDiff diff : differingNodes) {
        reportLines.add(createMessage(diff.getDetails(), diff.getExpectedNodePath(),
                expectedContext, diff.getActualNodePath(), actualContext));
      }
    }
    return Joiner.on('\n').join(reportLines.build());
  }

  /** Creates a log entry about an extra node on the expected or actual tree. */
  private String createMessage(String details, TreePath nodePath, @Nullable TreeContext treeContext,
      boolean onExpected) {
    long startLine = (treeContext == null)
        ? NOPOS : treeContext.getNodeStartLine(nodePath.getLeaf());
    String contextStr = String.format("Line %s %s",
        (startLine == NOPOS) ? NO_LINE : startLine,
        Breadcrumbs.describeTreePath(nodePath));
    return Joiner.on('\n').join(
        String.format("> Extra node in %s tree.", onExpected ? "expected" : "actual"),
        String.format("  %s", contextStr),
        String.format("  Node contents: <%s>.", nodeContents(nodePath.getLeaf())),
        String.format("  %s", details),
        "");
  }

  /** Creates a log entry about two differing nodes. */
  private String createMessage(String details, TreePath expectedNodePath,
      @Nullable TreeContext expectedTreeContext, TreePath actualNodePath,
      @Nullable TreeContext actualTreeContext) {

    long expectedTreeStartLine = (expectedTreeContext == null)
        ? NOPOS : expectedTreeContext.getNodeStartLine(expectedNodePath.getLeaf());
    String expectedContextStr = String.format("Line %s %s",
        (expectedTreeStartLine == NOPOS) ? NO_LINE : expectedTreeStartLine,
        Breadcrumbs.describeTreePath(expectedNodePath));
    long actualTreeStartLine = (actualTreeContext == null)
        ? NOPOS : actualTreeContext.getNodeStartLine(actualNodePath.getLeaf());
    String actualContextStr = String.format("Line %s %s",
        (actualTreeStartLine == NOPOS) ? NO_LINE : actualTreeStartLine,
        Breadcrumbs.describeTreePath(actualNodePath));
    return Joiner.on('\n').join(
        "> Difference in expected tree and actual tree.",
        String.format("  Expected node: %s", expectedContextStr),
        String.format("  Actual node: %s", actualContextStr),
        String.format("  %s", details),
        "");
  }

  /** Returns a specially formatted String containing the contents of the given node. */
  private String nodeContents(Tree node) {
    return node.toString().replaceFirst("\n", "");  // nodes begin with an ugly newline.
  }

  /**
   * A {@code Builder} class for {@code TreeDifference} objects.
   */
  static final class Builder {

    private final ImmutableList.Builder<OneWayDiff> extraExpectedNodesBuilder;
    private final ImmutableList.Builder<OneWayDiff> extraActualNodesBuilder;
    private final ImmutableList.Builder<TwoWayDiff> differingNodesBuilder;

    Builder() {
      this.extraExpectedNodesBuilder = new ImmutableList.Builder<OneWayDiff>();
      this.extraActualNodesBuilder = new ImmutableList.Builder<OneWayDiff>();
      this.differingNodesBuilder = new ImmutableList.Builder<TwoWayDiff>();
    }

    /** Logs an extra node on the expected tree in the {@code TreeDifference} being built. */
    @CanIgnoreReturnValue
    Builder addExtraExpectedNode(TreePath extraNode) {
      return addExtraExpectedNode(extraNode, "");
    }

    /** Logs an extra node on the expected tree in the {@code TreeDifference} being built. */
    @CanIgnoreReturnValue
    Builder addExtraExpectedNode(TreePath extraNode, String message) {
      extraExpectedNodesBuilder.add(new OneWayDiff(extraNode, message));
      return this;
    }

    /** Logs an extra node on the actual tree in the {@code TreeDifference} being built. */
    @CanIgnoreReturnValue
    Builder addExtraActualNode(TreePath extraNode, String message) {
      extraActualNodesBuilder.add(new OneWayDiff(extraNode, message));
      return this;
    }

    /** Logs an extra node on the actual tree in the {@code TreeDifference} being built. */
    @CanIgnoreReturnValue
    Builder addExtraActualNode(TreePath extraNode) {
      return addExtraActualNode(extraNode, "");
    }

    /**
     * Logs a discrepancy between an expected and actual node in the {@code TreeDifference} being
     * built.
     */
    @CanIgnoreReturnValue
    Builder addDifferingNodes(TreePath expectedNode, TreePath actualNode) {
      return addDifferingNodes(expectedNode, actualNode, "");
    }

    /**
     * Logs a discrepancy between an expected and actual node in the {@code TreeDifference} being
     * built.
     */
    @CanIgnoreReturnValue
    Builder addDifferingNodes(TreePath expectedNode, TreePath actualNode, String message) {
      differingNodesBuilder.add(new TwoWayDiff(expectedNode, actualNode, message));
      return this;
    }

    /** Builds and returns the {@code TreeDifference}. */
    TreeDifference build() {
      return new TreeDifference(extraExpectedNodesBuilder.build(),
          extraActualNodesBuilder.build(), differingNodesBuilder.build());
    }
  }

  /**
   * A class describing an extra node on either the expected or actual tree.
   */
  static final class OneWayDiff {
    private final TreePath nodePath;
    private String details;

    OneWayDiff(TreePath nodePath, String details) {
      this.nodePath = nodePath;
      this.details = details;
    }

    TreePath getNodePath() {
      return nodePath;
    }

    /** Returns a string that provides contextual details about the diff. */
    String getDetails() {
      return details;
    }
  }

  /**
   * A class describing a difference between a node on the expected tree and a corresponding node on
   * the actual tree.
   */
  static final class TwoWayDiff {
    private final TreePath expectedNodePath;
    private final TreePath actualNodePath;
    private String details;

    TwoWayDiff(TreePath expectedNodePath, TreePath actualNodePath, String details) {
      this.expectedNodePath = expectedNodePath;
      this.actualNodePath = actualNodePath;
      this.details = details;
    }

    TreePath getExpectedNodePath() {
      return expectedNodePath;
    }

    TreePath getActualNodePath() {
      return actualNodePath;
    }

    /** Returns a string that provides contextual details about the diff. */
    String getDetails() {
      return details;
    }
  }
}
