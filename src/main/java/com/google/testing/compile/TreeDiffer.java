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
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Objects;
import com.google.common.base.Optional;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssertTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LabeledStatementTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SynchronizedTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;

import java.util.Iterator;

import javax.annotation.Nullable;

/**
 * A class for determining how two compilation {@code Tree}s differ from each other.
 *
 * <p>This class takes source ordering into account. That is, two isomorphic
 * {@code CompilationUnitTrees} will have {@code TreeDifference} entires if their child
 * nodes do not appear in the same order. However, the ordering of the {@code TreeDifference}
 * entries that this class produces is always unspecified.
 *
 * <p>Future releases of this class will likely attempt to match corresponding sub{@code Tree}s
 * and ensure that those similarities are reflected in the diff report. No such attempt is made in
 * this release.
 *
 * @author Stephen Pratt
 */
@SuppressWarnings("restriction") // Sun APIs usage intended
final class TreeDiffer {
  private TreeDiffer() {}

  /**
   * Returns a {@code TreeDifference} describing the difference between the two
   * {@code CompilationUnitTree}s provided.
   */
  static final TreeDifference diffCompilationUnits(@Nullable CompilationUnitTree reference,
      @Nullable CompilationUnitTree base) {
    TreeDifference.Builder diffBuilder = new TreeDifference.Builder();
    DiffVisitor diffVisitor = new DiffVisitor(diffBuilder);
    diffVisitor.scan(reference, base);
    return diffBuilder.build();
  }

  /**
   * Returns a {@code TreeDifference} describing the difference between the two
   * sub-{@code Tree}s of the {@code CompilationUnitTree}s provided.
   *
   * @throws IllegalArgumentException if the subtrees given are not members of their respective
   *   compilation units.
   */
  static final TreeDifference diffSubtrees(CompilationUnitTree referenceCompilationUnit,
      @Nullable Tree referenceSubtree, CompilationUnitTree baseCompilationUnit,
      @Nullable Tree baseSubtree) {
    TreeDifference.Builder diffBuilder = new TreeDifference.Builder();
    DiffVisitor diffVisitor = new DiffVisitor(diffBuilder,
        referenceCompilationUnit, referenceSubtree, baseCompilationUnit, baseSubtree);
    diffVisitor.scan(referenceSubtree, baseSubtree);
    return diffBuilder.build();
  }

  /**
   * A {@code SimpleTreeVisitor} that traverses a {@link Tree} and an argument {@link Tree},
   * verifying equality along the way. Appends each diff it finds to a
   * {@link TreeDifference.Builder}.
   */
  static final class DiffVisitor extends SimpleTreeVisitor<Void, Tree> {
    private TreePath referencePath;
    private TreePath basePath;

    private final TreeDifference.Builder diffBuilder;

    public DiffVisitor(TreeDifference.Builder diffBuilder) {
      this.diffBuilder = diffBuilder;
      referencePath = null;
      basePath = null;
    }

    /**
     * Constructs a DiffVisitor whose {@code TreePath}s are initialized with paths constructed
     * from the trees provided
     *
     * @throws IllegalArgumentException if the subtrees given are not members of their respective
     *   compilation units.
     */
    public DiffVisitor(TreeDifference.Builder diffBuilder,
        CompilationUnitTree referenceCompilationUnit, Tree referenceSubtree,
        CompilationUnitTree baseCompilationUnit, Tree baseSubtree) {
      this.diffBuilder = diffBuilder;
      referencePath = TreePath.getPath(referenceCompilationUnit, referenceSubtree);
      basePath = TreePath.getPath(baseCompilationUnit, baseSubtree);
      checkArgument(referencePath != null, "Couldn't initialize differ because no "
          + "path could be found connecting the node and compilation root given.");
     checkArgument(basePath != null, "Couldn't initialize differ because no "
          + "path could be found connecting the node and compilation root given.");
    }

    /**
     * Adds a {@code TwoWayDiff} that results from two {@code Tree}s having different
     * {@code Tree.Kind}s.
     */
    public void addTypeMismatch(Tree reference, Tree base) {
      diffBuilder.addDifferingNodes(referencePathPlus(reference), basePathPlus(base),
          String.format("Expected node kind to be <%s> but was <%s>.",
              reference.getKind(), base.getKind()));
    }

    /**
     * Adds a {@code TwoWayDiff} if the predicate given evaluates to false. The {@code TwoWayDiff}
     * is parameterized by the {@code Tree}s and message format provided.
     */
    private void checkForDiff(boolean p, String message, Object... formatArgs) {
      if (!p) {
        diffBuilder.addDifferingNodes(referencePath, basePath, String.format(message, formatArgs));
      }
    }

    private TreePath basePathPlus(Tree base) {
      return new TreePath(basePath, base);
    }

    private TreePath referencePathPlus(Tree reference) {
      return new TreePath(referencePath, reference);
    }

    /**
     * Pushes the {@code reference} and {@code base} {@link Tree}s onto their respective
     * {@link TreePath}s and recurses with {@code reference.accept(this, base)}, popping the
     * stack when the call completes.
     *
     * <p>This should be the ONLY place where either {@link TreePath} is mutated.
     */
    private Void pushPathAndAccept(Tree reference, Tree base) {
      TreePath prevReferencePath = referencePath;
      TreePath prevBasePath = basePath;
      referencePath = referencePathPlus(reference);
      basePath = basePathPlus(base);
      try {
        return reference.accept(this, base);
      } finally {
        referencePath = prevReferencePath;
        basePath = prevBasePath;
      }
    }

    public Void scan(@Nullable Tree reference, @Nullable Tree base) {
      if (reference == null) {
        if (base != null) {
          diffBuilder.addExtraNodeOnRight(basePathPlus(base));
        }
        return null;
      }
      return pushPathAndAccept(reference, base);
    }

    private Void parallelScan(Iterable<? extends Tree> references, Iterable<? extends Tree> bases) {
      if (references != null && bases != null) {
        Iterator<? extends Tree> referencesIterator = references.iterator();
        Iterator<? extends Tree> basesIterator = bases.iterator();
        while (referencesIterator.hasNext() && basesIterator.hasNext()) {
          pushPathAndAccept(referencesIterator.next(), basesIterator.next());
        }
        if (!referencesIterator.hasNext() && basesIterator.hasNext()) {
          diffBuilder.addExtraNodeOnRight(basePathPlus(basesIterator.next()));
        } else if (referencesIterator.hasNext() && !basesIterator.hasNext()) {
          diffBuilder.addExtraNodeOnLeft(referencePathPlus(referencesIterator.next()));
        }
      } else if (references == null && bases.iterator().hasNext()) {
        diffBuilder.addExtraNodeOnRight(basePathPlus(bases.iterator().next()));
      } else if (bases == null && references.iterator().hasNext()) {
        diffBuilder.addExtraNodeOnLeft(referencePathPlus(references.iterator().next()));
      }
      return null;
    }

    @Override
    public Void visitAnnotation(AnnotationTree reference, Tree base) {
      Optional<AnnotationTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getAnnotationType(), other.get().getAnnotationType());
      parallelScan(reference.getArguments(), other.get().getArguments());
      return null;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree reference, Tree base) {
      Optional<MethodInvocationTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      parallelScan(reference.getTypeArguments(), other.get().getTypeArguments());
      scan(reference.getMethodSelect(), other.get().getMethodSelect());
      parallelScan(reference.getArguments(), other.get().getArguments());
      return null;
    }

    @Override
    public Void visitAssert(AssertTree reference, Tree base) {
      Optional<AssertTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getCondition(), other.get().getCondition());
      scan(reference.getDetail(), other.get().getDetail());
      return null;
    }

    @Override
    public Void visitAssignment(AssignmentTree reference, Tree base) {
      Optional<AssignmentTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getVariable(), other.get().getVariable());
      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree reference, Tree base) {
      Optional<CompoundAssignmentTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getVariable(), other.get().getVariable());
      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitBinary(BinaryTree reference, Tree base) {
      Optional<BinaryTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getLeftOperand(), other.get().getLeftOperand());
      scan(reference.getRightOperand(), other.get().getRightOperand());
      return null;
    }

    @Override
    public Void visitBlock(BlockTree reference, Tree base) {
      Optional<BlockTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.isStatic() == other.get().isStatic(),
          "Expected block to be <%s> but was <%s>.", reference.isStatic() ? "static" : "non-static",
          other.get().isStatic() ? "static" : "non-static");

      parallelScan(reference.getStatements(), other.get().getStatements());
      return null;
    }

    @Override
    public Void visitBreak(BreakTree reference, Tree base) {
      Optional<BreakTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getLabel().contentEquals(other.get().getLabel()),
          "Expected label on break statement to be <%s> but was <%s>.",
          reference.getLabel(), other.get().getLabel());
      return null;
    }

    @Override
    public Void visitCase(CaseTree reference, Tree base) {
      Optional<CaseTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      parallelScan(reference.getStatements(), other.get().getStatements());
      return null;
    }

    @Override
    public Void visitCatch(CatchTree reference, Tree base) {
      Optional<CatchTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getParameter(), other.get().getParameter());
      scan(reference.getBlock(), other.get().getBlock());
      return null;
    }

    @Override
    public Void visitClass(ClassTree reference, Tree base) {
      Optional<ClassTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getSimpleName().contentEquals(other.get().getSimpleName()),
          "Expected name of type to be <%s> but was <%s>.",
          reference.getSimpleName(), other.get().getSimpleName());

      scan(reference.getModifiers(), other.get().getModifiers());
      parallelScan(reference.getTypeParameters(), other.get().getTypeParameters());
      scan(reference.getExtendsClause(), other.get().getExtendsClause());
      parallelScan(reference.getImplementsClause(), other.get().getImplementsClause());
      parallelScan(reference.getMembers(), other.get().getMembers());
      return null;
    }

    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree reference, Tree base) {
      Optional<ConditionalExpressionTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getCondition(), other.get().getCondition());
      scan(reference.getTrueExpression(), other.get().getTrueExpression());
      scan(reference.getFalseExpression(), other.get().getFalseExpression());
      return null;
    }

    @Override
    public Void visitContinue(ContinueTree reference, Tree base) {
      Optional<ContinueTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getLabel().contentEquals(other.get().getLabel()),
          "Expected label on continue statement to be <%s> but was <%s>.",
          reference.getLabel(), other.get().getLabel());
      return null;
    }

    @Override
    public Void visitDoWhileLoop(DoWhileLoopTree reference, Tree base) {
      Optional<DoWhileLoopTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getCondition(), other.get().getCondition());
      scan(reference.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitErroneous(ErroneousTree reference, Tree base) {
      Optional<ErroneousTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      parallelScan(reference.getErrorTrees(), other.get().getErrorTrees());
      return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatementTree reference, Tree base) {
      Optional<ExpressionStatementTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree reference, Tree base) {
      Optional<EnhancedForLoopTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getVariable(), other.get().getVariable());
      scan(reference.getExpression(), other.get().getExpression());
      scan(reference.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitForLoop(ForLoopTree reference, Tree base) {
      Optional<ForLoopTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      parallelScan(reference.getInitializer(), other.get().getInitializer());
      scan(reference.getCondition(), other.get().getCondition());
      parallelScan(reference.getUpdate(), other.get().getUpdate());
      scan(reference.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitIdentifier(IdentifierTree reference, Tree base) {
      Optional<IdentifierTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getName().contentEquals(other.get().getName()),
          "Expected identifier to be <%s> but was <%s>.",
          reference.getName(), other.get().getName());
      return null;
    }

    @Override
    public Void visitIf(IfTree reference, Tree base) {
      Optional<IfTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getCondition(), other.get().getCondition());
      scan(reference.getThenStatement(), other.get().getThenStatement());
      scan(reference.getElseStatement(), other.get().getElseStatement());
      return null;
    }

    @Override
    public Void visitImport(ImportTree reference, Tree base) {
      Optional<ImportTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.isStatic() == other.get().isStatic(),
          "Expected import to be <%s> but was <%s>.",
          reference.isStatic() ? "static" : "non-static",
          other.get().isStatic() ? "static" : "non-static");

      scan(reference.getQualifiedIdentifier(), other.get().getQualifiedIdentifier());
      return null;
    }

    @Override
    public Void visitArrayAccess(ArrayAccessTree reference, Tree base) {
      Optional<ArrayAccessTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      scan(reference.getIndex(), other.get().getIndex());
      return null;
    }

    @Override
    public Void visitLabeledStatement(LabeledStatementTree reference, Tree base) {
      Optional<LabeledStatementTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getLabel().contentEquals(other.get().getLabel()),
          "Expected statement label to be <%s> but was <%s>.",
          reference.getLabel(), other.get().getLabel());

      scan(reference.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitLiteral(LiteralTree reference, Tree base) {
      Optional<LiteralTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(Objects.equal(reference.getValue(), other.get().getValue()),
          "Expected literal value to be <%s> but was <%s>.",
          reference.getValue(), other.get().getValue());
      return null;
    }

    @Override
    public Void visitMethod(MethodTree reference, Tree base) {
      Optional<MethodTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getName().contentEquals(other.get().getName()),
          "Expected method name to be <%s> but was <%s>.",
          reference.getName(), other.get().getName());

      scan(reference.getModifiers(), other.get().getModifiers());
      scan(reference.getReturnType(), other.get().getReturnType());
      parallelScan(reference.getTypeParameters(), other.get().getTypeParameters());
      parallelScan(reference.getParameters(), other.get().getParameters());
      parallelScan(reference.getThrows(), other.get().getThrows());
      scan(reference.getBody(), other.get().getBody());
      scan(reference.getDefaultValue(), other.get().getDefaultValue());
      return null;
    }

    @Override
    public Void visitModifiers(ModifiersTree reference, Tree base) {
      Optional<ModifiersTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getFlags().equals(other.get().getFlags()),
          "Expected modifier set to be <%s> but was <%s>.",
          reference.getFlags(), other.get().getFlags());

      parallelScan(reference.getAnnotations(), other.get().getAnnotations());
      return null;
    }

    @Override
    public Void visitNewArray(NewArrayTree reference, Tree base) {
      Optional<NewArrayTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getType(), other.get().getType());
      parallelScan(reference.getDimensions(), other.get().getDimensions());
      parallelScan(reference.getInitializers(), other.get().getInitializers());
      return null;
    }

    @Override
    public Void visitNewClass(NewClassTree reference, Tree base) {
      Optional<NewClassTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getEnclosingExpression(), other.get().getEnclosingExpression());
      parallelScan(reference.getTypeArguments(), other.get().getTypeArguments());
      scan(reference.getIdentifier(), other.get().getIdentifier());
      parallelScan(reference.getArguments(), other.get().getArguments());
      scan(reference.getClassBody(), other.get().getClassBody());
      return null;
    }

    @Override
    public Void visitParenthesized(ParenthesizedTree reference, Tree base) {
      Optional<ParenthesizedTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitReturn(ReturnTree reference, Tree base) {
      Optional<ReturnTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree reference, Tree base) {
      Optional<MemberSelectTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getIdentifier().contentEquals(other.get().getIdentifier()),
          "Expected member identifier to be <%s> but was <%s>.",
          reference.getIdentifier(), other.get().getIdentifier());

      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitEmptyStatement(EmptyStatementTree reference, Tree base) {
      if (!checkTypeAndCast(reference, base).isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }
      return null;
    }

    @Override
    public Void visitSwitch(SwitchTree reference, Tree base) {
      Optional<SwitchTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      parallelScan(reference.getCases(), other.get().getCases());
      return null;
    }

    @Override
    public Void visitSynchronized(SynchronizedTree reference, Tree base) {
      Optional<SynchronizedTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      scan(reference.getBlock(), other.get().getBlock());
      return null;
    }

    @Override
    public Void visitThrow(ThrowTree reference, Tree base) {
      Optional<ThrowTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree reference, Tree base) {
      Optional<CompilationUnitTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      parallelScan(reference.getPackageAnnotations(), other.get().getPackageAnnotations());
      scan(reference.getPackageName(), other.get().getPackageName());
      parallelScan(reference.getImports(), other.get().getImports());
      parallelScan(reference.getTypeDecls(), other.get().getTypeDecls());
      return null;
    }

    @Override
    public Void visitTry(TryTree reference, Tree base) {
      Optional<TryTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getBlock(), other.get().getBlock());
      parallelScan(reference.getCatches(), other.get().getCatches());
      scan(reference.getFinallyBlock(), other.get().getFinallyBlock());
      return null;
    }

    @Override
    public Void visitParameterizedType(ParameterizedTypeTree reference, Tree base) {
      Optional<ParameterizedTypeTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getType(), other.get().getType());
      parallelScan(reference.getTypeArguments(), other.get().getTypeArguments());
      return null;
    }

    @Override
    public Void visitArrayType(ArrayTypeTree reference, Tree base) {
      Optional<ArrayTypeTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getType(), other.get().getType());
      return null;
    }

    @Override
    public Void visitTypeCast(TypeCastTree reference, Tree base) {
      Optional<TypeCastTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getType(), other.get().getType());
      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitPrimitiveType(PrimitiveTypeTree reference, Tree base) {
      Optional<PrimitiveTypeTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getPrimitiveTypeKind() == other.get().getPrimitiveTypeKind(),
          "Expected primitive type kind to be <%s> but was <%s>.",
          reference.getPrimitiveTypeKind(), other.get().getPrimitiveTypeKind());
      return null;
    }

    @Override
    public Void visitTypeParameter(TypeParameterTree reference, Tree base) {
      Optional<TypeParameterTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getName().contentEquals(other.get().getName()),
          "Expected type parameter name to be <%s> but was <%s>.",
          reference.getName(), other.get().getName());

      parallelScan(reference.getBounds(), other.get().getBounds());
      return null;
    }

    @Override
    public Void visitInstanceOf(InstanceOfTree reference, Tree base) {
      Optional<InstanceOfTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      scan(reference.getType(), other.get().getType());
      return null;
    }

    @Override
    public Void visitUnary(UnaryTree reference, Tree base) {
      Optional<UnaryTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitVariable(VariableTree reference, Tree base) {
      Optional<VariableTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      checkForDiff(reference.getName().contentEquals(other.get().getName()),
          "Expected variable name to be <%s> but was <%s>.",
          reference.getName(), other.get().getName());

      scan(reference.getModifiers(), other.get().getModifiers());
      scan(reference.getType(), other.get().getType());
      scan(reference.getInitializer(), other.get().getInitializer());
      return null;
    }

    @Override
    public Void visitWhileLoop(WhileLoopTree reference, Tree base) {
      Optional<WhileLoopTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getCondition(), other.get().getCondition());
      scan(reference.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitWildcard(WildcardTree reference, Tree base) {
      Optional<WildcardTree> other = checkTypeAndCast(reference, base);
      if (!other.isPresent()) {
        addTypeMismatch(reference, base);
        return null;
      }

      scan(reference.getBound(), other.get().getBound());
      return null;
    }

    @Override
    public Void visitOther(Tree reference, Tree base) {
      throw new UnsupportedOperationException("cannot compare unknown trees");
    }

    private <T extends Tree> Optional<T> checkTypeAndCast(T reference, Tree base) {
      Kind referenceKind = checkNotNull(reference).getKind();
      Kind treeKind = checkNotNull(base).getKind();
      if (referenceKind == treeKind) {
        @SuppressWarnings("unchecked")  // checked by Kind
        T treeAsReferenceType = (T) base;
        return Optional.of(treeAsReferenceType);
      } else {
        return Optional.absent();
      }
    }
  }
}