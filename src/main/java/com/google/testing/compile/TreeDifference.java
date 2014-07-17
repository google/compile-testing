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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;

import javax.annotation.Nullable;

/**
 * A data structure describing the set of syntactic differences between two {@link Tree}s.
 *
 * @author Stephen Pratt
 */
final class TreeDifference {

  private final ImmutableList<OneWayDiff> extraNodesOnLeft;
  private final ImmutableList<OneWayDiff> extraNodesOnRight;
  private final ImmutableList<TwoWayDiff> differingNodes;

  /** Constructs an empty {@code TreeDifference}. */
  TreeDifference() {
    this.extraNodesOnLeft = ImmutableList.<OneWayDiff>of();
    this.extraNodesOnRight = ImmutableList.<OneWayDiff>of();
    this.differingNodes = ImmutableList.<TwoWayDiff>of();
  }

  /** Constructs a {@code TreeDifference} that includes the given diffs. */
  TreeDifference(ImmutableList<OneWayDiff> extraNodesOnLeft,
      ImmutableList<OneWayDiff> extraNodesOnRight, ImmutableList<TwoWayDiff> differingNodes) {
    this.extraNodesOnLeft = extraNodesOnLeft;
    this.extraNodesOnRight = extraNodesOnRight;
    this.differingNodes = differingNodes;
  }

  /** Returns {@code true} iff there are no diffs. */
  boolean isEmpty() {
    return extraNodesOnLeft.isEmpty() && extraNodesOnRight.isEmpty() && differingNodes.isEmpty();
  }

  /**
   * Returns {@code OneWayDiff}s describing nodes on the left tree that are unmatched on the right.
   */
  ImmutableList<OneWayDiff> getExtraNodesOnLeft() {
    return extraNodesOnLeft;
  }

  /**
   *Returns {@code OneWayDiff}s describing nodes on the right tree that are unmatched on the left.
   */
  ImmutableList<OneWayDiff> getExtraNodesOnRight() {
    return extraNodesOnRight;
  }

  /** Returns {@code TwoWayDiff}s describing nodes that differ on the left and right trees. */
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
   * Returns a {@code String} reporting all diffs known to this {@code TreeDifference}. If a left
   * or right {@code TreeContext} is provided, then it will be used to contextualize corresponding
   * entries in the report.
   */
  String getDiffReport(@Nullable TreeContext leftContext, @Nullable TreeContext rightContext) {
    ImmutableList.Builder<String> reportLines = new ImmutableList.Builder<>();
    if (!extraNodesOnLeft.isEmpty()) {
      reportLines.add(String.format("Found %s unmatched nodes in the expected tree. %n",
              extraNodesOnLeft.size()));
      for (OneWayDiff diff : extraNodesOnLeft) {
        reportLines.add(createMessage(diff.getDetails(), diff.getNodePath(), leftContext, true));
      }
    }
    if (!extraNodesOnRight.isEmpty()) {
      reportLines.add(String.format("Found %s unmatched nodes in the actual tree. %n",
              extraNodesOnRight.size()));
      for (OneWayDiff diff : extraNodesOnRight) {
        reportLines.add(createMessage(diff.getDetails(), diff.getNodePath(), rightContext, false));
      }
    }
    if (!differingNodes.isEmpty()) {
      reportLines.add(String.format("Found %s nodes that differed in expected and actual trees. %n",
              differingNodes.size()));
      for (TwoWayDiff diff : differingNodes) {
        reportLines.add(createMessage(diff.getDetails(), diff.getLeftNodePath(), leftContext,
                diff.getRightNodePath(), rightContext));
      }
    }
    return Joiner.on('\n').join(reportLines.build());
  }

  /** Creates a log entry about an extra node on the left or right tree. */
  private String createMessage(String details, TreePath nodePath, @Nullable TreeContext treeContext,
      boolean onLeft) {
    String contextStr = (treeContext == null) ? "[context unavailable]" : String.format(
        "Line %s %s.", treeContext.getNodeStartLine(nodePath.getLeaf()),
        Breadcrumbs.describeTreePath(nodePath));
    return Joiner.on('\n').join(
        String.format("> Extra node in %s tree.", onLeft ? "expected" : "actual"),
        String.format("\t %s", contextStr),
        String.format("\t Node contents: <%s>.", nodeContents(nodePath.getLeaf())),
        String.format("\t %s", details),
        "");
  }

  /** Creates a log entry about two differing nodes. */
  private String createMessage(String details, TreePath leftNodePath,
      @Nullable TreeContext leftTreeContext, TreePath rightNodePath,
      @Nullable TreeContext rightTreeContext) {
    String leftContextStr = (leftTreeContext == null) ? "[context unavailable]" : String.format(
        "Line %s %s.", leftTreeContext.getNodeStartLine(leftNodePath.getLeaf()),
        Breadcrumbs.describeTreePath(leftNodePath));
    String rightContextStr = (rightTreeContext == null) ? "[context unavailable]" : String.format(
        "Line %s %s.", rightTreeContext.getNodeStartLine(rightNodePath.getLeaf()),
        Breadcrumbs.describeTreePath(rightNodePath));
    return Joiner.on('\n').join(
        "> Difference in expected tree and actual tree.",
        String.format("\t Expected node: %s", leftContextStr),
        String.format("\t Actual node: %s", rightContextStr),
        String.format("\t %s", details),
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

    private final ImmutableList.Builder<OneWayDiff> extraNodesOnLeftBuilder;
    private final ImmutableList.Builder<OneWayDiff> extraNodesOnRightBuilder;
    private final ImmutableList.Builder<TwoWayDiff> differingNodesBuilder;

    Builder() {
      this.extraNodesOnLeftBuilder = new ImmutableList.Builder<>();
      this.extraNodesOnRightBuilder = new ImmutableList.Builder<>();
      this.differingNodesBuilder = new ImmutableList.Builder<>();
    }

    /** Logs an extra node on the left tree in the {@code TreeDifference} being built. */
    Builder addExtraNodeOnLeft(TreePath extraNode) {
      return addExtraNodeOnLeft(extraNode, "");
    }

    /** Logs an extra node on the left tree in the {@code TreeDifference} being built. */
    Builder addExtraNodeOnLeft(TreePath extraNode, String message) {
      extraNodesOnLeftBuilder.add(new OneWayDiff(extraNode, message));
      return this;
    }

    /** Logs an extra node on the right tree in the {@code TreeDifference} being built. */
    Builder addExtraNodeOnRight(TreePath extraNode, String message) {
      extraNodesOnRightBuilder.add(new OneWayDiff(extraNode, message));
      return this;
    }

    /** Logs an extra node on the right tree in the {@code TreeDifference} being built. */
    Builder addExtraNodeOnRight(TreePath extraNode) {
      return addExtraNodeOnRight(extraNode, "");
    }

    /**
     * Logs a discrepancy between a left and right node in the {@code TreeDifference} being built.
     */
    Builder addDifferingNodes(TreePath leftNode, TreePath rightNode) {
      return addDifferingNodes(leftNode, rightNode, "");
    }

    /**
     * Logs a discrepancy between a left and right node in the {@code TreeDifference} being built.
     */
    Builder addDifferingNodes(TreePath leftNode, TreePath rightNode, String message) {
      differingNodesBuilder.add(new TwoWayDiff(leftNode, rightNode, message));
      return this;
    }

    /** Builds and returns the {@code TreeDifference}. */
    TreeDifference build() {
      return new TreeDifference(extraNodesOnLeftBuilder.build(),
          extraNodesOnRightBuilder.build(), differingNodesBuilder.build());
    }
  }

  /**
   * A class describing an extra node on either the left or right tree.
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
   * A class describing a difference between a node on the left tree and a corresponding node on
   * the right.
   */
  static final class TwoWayDiff {
    private final TreePath leftNodePath;
    private final TreePath rightNodePath;
    private String details;

    TwoWayDiff(TreePath leftNodePath, TreePath rightNodePath, String details) {
      this.leftNodePath = leftNodePath;
      this.rightNodePath = rightNodePath;
      this.details = details;
    }

    TreePath getLeftNodePath() {
      return leftNodePath;
    }

    TreePath getRightNodePath() {
      return rightNodePath;
    }

    /** Returns a string that provides contextual details about the diff. */
    String getDetails() {
      return details;
    }
  }
}