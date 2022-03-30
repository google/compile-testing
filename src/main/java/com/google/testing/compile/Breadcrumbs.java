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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import java.util.List;

/**
 * This class contains methods for providing breadcrumb {@code String}s which describe the contents
 * of a {@code TreePath}.
 *
 * @author Stephen Pratt
 */
final class Breadcrumbs {
  private static final BreadcrumbVisitor BREADCRUMB_VISITOR = new BreadcrumbVisitor();
  private Breadcrumbs() {}

  /**
   * Returns a string describing the {@link TreePath} given.
   */
  static String describeTreePath(TreePath path) {
    return Joiner.on("->").join(getBreadcrumbList(path));
  }

  /**
   * Returns a list of breadcrumb strings describing the {@link TreePath} given.
   */
  static List<String> getBreadcrumbList(TreePath path) {
    return Lists.reverse(FluentIterable.from(path)
        .transform(new Function<Tree, String>() {
          @Override public String apply(Tree t) {
            return t.accept(BREADCRUMB_VISITOR, null);
          }
        }).toList());
  }

  /**
   * A {@link SimpleTreeVisitor} for providing a breadcrumb {@code String} for a {@link Tree} node.
   * The breadcrumb {@code String} will not be unique, but can be used to give context about the
   * node as it exists within a {@code TreePath}.
   */
  @SuppressWarnings("restriction") // Sun APIs usage intended
  static final class BreadcrumbVisitor extends SimpleTreeVisitor<String, Void> {

    /** Returns a {@code String} describing the {@code Tree.Kind} of the given {@code Tree}. */
    private String kindString(Tree t) {
      return t.getKind().toString();
    }

    /**
     * Returns a {@code String} describing the {@code Tree.Kind} of the given {@code Tree}.
     * The string will be specified by the {@code toString()} value of the detail object given.
     */
    private String detailedKindString(Tree t, Object detail) {
      return String.format("%s(%s)", kindString(t), detail);
    }

    @Override
    public String defaultAction(Tree t, Void v) {
      return (t != null) ? kindString(t) : "";
    }

    @Override
    public String visitBlock(BlockTree reference, Void v) {
      return (reference != null)
          ? detailedKindString(reference, reference.isStatic() ? "static" : "non-static") : "";
    }

    @Override
    public String visitBreak(BreakTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getLabel()) : "";
    }

    @Override
    public String visitClass(ClassTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getSimpleName()) : "";
    }

    @Override
    public String visitContinue(ContinueTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getLabel()) : "";
    }

    @Override
    public String visitIdentifier(IdentifierTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getName()) : "";
    }

    @Override
    public String visitImport(ImportTree reference, Void v) {
      return (reference != null) ?
          detailedKindString(reference, reference.isStatic() ? "static" : "non-static") : "";
    }

    @Override
    public String visitLabeledStatement(LabeledStatementTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getLabel()) : "";
    }

    @Override
    public String visitLiteral(LiteralTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getValue()) : "";
    }

    @Override
    public String visitMethod(MethodTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getName()) : "";
    }

    @Override
    public String visitModifiers(ModifiersTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getFlags()) : "";
    }

    @Override
    public String visitMemberSelect(MemberSelectTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getIdentifier()) : "";
    }

    @Override
    public String visitPrimitiveType(PrimitiveTypeTree reference, Void v) {
      return (reference != null)
          ? detailedKindString(reference, reference.getPrimitiveTypeKind()) : "";
    }

    @Override
    public String visitTypeParameter(TypeParameterTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getName()) : "";
    }

    @Override
    public String visitVariable(VariableTree reference, Void v) {
      return (reference != null) ? detailedKindString(reference, reference.getName()) : "";
    }
  }
}
