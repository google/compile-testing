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
  static final TreeDifference diffCompilationUnits(@Nullable CompilationUnitTree expected,
      @Nullable CompilationUnitTree actual) {
    TreeDifference.Builder diffBuilder = new TreeDifference.Builder();
    DiffVisitor diffVisitor = new DiffVisitor(diffBuilder);
    diffVisitor.scan(expected, actual);
    return diffBuilder.build();
  }

  /**
   * Returns a {@code TreeDifference} describing the difference between the two
   * sub-{@code Tree}s of the {@code CompilationUnitTree}s provided.
   *
   * @throws IllegalArgumentException if the subtrees given are not members of their respective
   *   compilation units.
   */
  static final TreeDifference diffSubtrees(CompilationUnitTree expectedCompilationUnit,
      @Nullable Tree expectedSubtree, CompilationUnitTree actualCompilationUnit,
      @Nullable Tree actualSubtree) {
    TreeDifference.Builder diffBuilder = new TreeDifference.Builder();
    DiffVisitor diffVisitor = new DiffVisitor(diffBuilder,
        expectedCompilationUnit, expectedSubtree, actualCompilationUnit, actualSubtree);
    diffVisitor.scan(expectedSubtree, actualSubtree);
    return diffBuilder.build();
  }

  /**
   * A {@code SimpleTreeVisitor} that traverses a {@link Tree} and an argument {@link Tree},
   * verifying equality along the way. Appends each diff it finds to a
   * {@link TreeDifference.Builder}.
   */
  static final class DiffVisitor extends SimpleTreeVisitor<Void, Tree> {
    private TreePath expectedPath;
    private TreePath actualPath;

    private final TreeDifference.Builder diffBuilder;

    public DiffVisitor(TreeDifference.Builder diffBuilder) {
      this.diffBuilder = diffBuilder;
      expectedPath = null;
      actualPath = null;
    }

    /**
     * Constructs a DiffVisitor whose {@code TreePath}s are initialized with paths constructed
     * from the trees provided
     *
     * @throws IllegalArgumentException if the subtrees given are not members of their respective
     *   compilation units.
     */
    public DiffVisitor(TreeDifference.Builder diffBuilder,
        CompilationUnitTree expectedCompilationUnit, Tree expectedSubtree,
        CompilationUnitTree actualCompilationUnit, Tree actualSubtree) {
      this.diffBuilder = diffBuilder;
      expectedPath = TreePath.getPath(expectedCompilationUnit, expectedSubtree);
      actualPath = TreePath.getPath(actualCompilationUnit, actualSubtree);
      checkArgument(expectedPath != null, "Couldn't initialize differ because no "
          + "path could be found connecting the node and compilation root given.");
      checkArgument(actualPath != null, "Couldn't initialize differ because no "
          + "path could be found connecting the node and compilation root given.");
    }

    /**
     * Adds a {@code TwoWayDiff} that results from two {@code Tree}s having different
     * {@code Tree.Kind}s.
     */
    public void addTypeMismatch(Tree expected, Tree actual) {
      diffBuilder.addDifferingNodes(expectedPathPlus(expected), actualPathPlus(actual),
          String.format("Expected node kind to be <%s> but was <%s>.",
              expected.getKind(), actual.getKind()));
    }

    /**
     * Adds a {@code TwoWayDiff} if the predicate given evaluates to false. The {@code TwoWayDiff}
     * is parameterized by the {@code Tree}s and message format provided.
     */
    private void checkForDiff(boolean p, String message, Object... formatArgs) {
      if (!p) {
        diffBuilder.addDifferingNodes(expectedPath, actualPath, String.format(message, formatArgs));
      }
    }

    private TreePath actualPathPlus(Tree actual) {
      checkNotNull(actual, "Tried to push null actual tree onto path.");
      return new TreePath(actualPath, actual);
    }

    private TreePath expectedPathPlus(Tree expected) {
      checkNotNull(expected, "Tried to push null expected tree onto path.");
      return new TreePath(expectedPath, expected);
    }

    /**
     * Pushes the {@code expected} and {@code actual} {@link Tree}s onto their respective
     * {@link TreePath}s and recurses with {@code expected.accept(this, actual)}, popping the
     * stack when the call completes.
     *
     * <p>This should be the ONLY place where either {@link TreePath} is mutated.
     */
    private Void pushPathAndAccept(Tree expected, Tree actual) {
      TreePath prevExpectedPath = expectedPath;
      TreePath prevActualPath = actualPath;
      expectedPath = expectedPathPlus(expected);
      actualPath = actualPathPlus(actual);
      try {
        return expected.accept(this, actual);
      } finally {
        expectedPath = prevExpectedPath;
        actualPath = prevActualPath;
      }
    }

    public Void scan(@Nullable Tree expected, @Nullable Tree actual) {
      if (expected == null && actual != null) {
        diffBuilder.addExtraActualNode(actualPathPlus(actual));
      } else if (expected != null && actual == null) {
        diffBuilder.addExtraExpectedNode(expectedPathPlus(expected));
      } else if (actual != null && expected != null) {
        pushPathAndAccept(expected, actual);
      }
      return null;
    }

    private Void parallelScan(Iterable<? extends Tree> expecteds,
        Iterable<? extends Tree> actuals) {
      if (expecteds != null && actuals != null) {
        Iterator<? extends Tree> expectedsIterator = expecteds.iterator();
        Iterator<? extends Tree> actualsIterator = actuals.iterator();
        while (expectedsIterator.hasNext() && actualsIterator.hasNext()) {
          pushPathAndAccept(expectedsIterator.next(), actualsIterator.next());
        }
        if (!expectedsIterator.hasNext() && actualsIterator.hasNext()) {
          diffBuilder.addExtraActualNode(actualPathPlus(actualsIterator.next()));
        } else if (expectedsIterator.hasNext() && !actualsIterator.hasNext()) {
          diffBuilder.addExtraExpectedNode(expectedPathPlus(expectedsIterator.next()));
        }
      } else if (expecteds == null && actuals.iterator().hasNext()) {
        diffBuilder.addExtraActualNode(actualPathPlus(actuals.iterator().next()));
      } else if (actuals == null && expecteds.iterator().hasNext()) {
        diffBuilder.addExtraExpectedNode(expectedPathPlus(expecteds.iterator().next()));
      }
      return null;
    }

    @Override
    public Void visitAnnotation(AnnotationTree expected, Tree actual) {
      Optional<AnnotationTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getAnnotationType(), other.get().getAnnotationType());
      parallelScan(expected.getArguments(), other.get().getArguments());
      return null;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree expected, Tree actual) {
      Optional<MethodInvocationTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      parallelScan(expected.getTypeArguments(), other.get().getTypeArguments());
      scan(expected.getMethodSelect(), other.get().getMethodSelect());
      parallelScan(expected.getArguments(), other.get().getArguments());
      return null;
    }

    @Override
    public Void visitAssert(AssertTree expected, Tree actual) {
      Optional<AssertTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getCondition(), other.get().getCondition());
      scan(expected.getDetail(), other.get().getDetail());
      return null;
    }

    @Override
    public Void visitAssignment(AssignmentTree expected, Tree actual) {
      Optional<AssignmentTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getVariable(), other.get().getVariable());
      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitCompoundAssignment(CompoundAssignmentTree expected, Tree actual) {
      Optional<CompoundAssignmentTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getVariable(), other.get().getVariable());
      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitBinary(BinaryTree expected, Tree actual) {
      Optional<BinaryTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getLeftOperand(), other.get().getLeftOperand());
      scan(expected.getRightOperand(), other.get().getRightOperand());
      return null;
    }

    @Override
    public Void visitBlock(BlockTree expected, Tree actual) {
      Optional<BlockTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.isStatic() == other.get().isStatic(),
          "Expected block to be <%s> but was <%s>.", expected.isStatic() ? "static" : "non-static",
          other.get().isStatic() ? "static" : "non-static");

      parallelScan(expected.getStatements(), other.get().getStatements());
      return null;
    }

    @Override
    public Void visitBreak(BreakTree expected, Tree actual) {
      Optional<BreakTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getLabel().contentEquals(other.get().getLabel()),
          "Expected label on break statement to be <%s> but was <%s>.",
          expected.getLabel(), other.get().getLabel());
      return null;
    }

    @Override
    public Void visitCase(CaseTree expected, Tree actual) {
      Optional<CaseTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      parallelScan(expected.getStatements(), other.get().getStatements());
      return null;
    }

    @Override
    public Void visitCatch(CatchTree expected, Tree actual) {
      Optional<CatchTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getParameter(), other.get().getParameter());
      scan(expected.getBlock(), other.get().getBlock());
      return null;
    }

    @Override
    public Void visitClass(ClassTree expected, Tree actual) {
      Optional<ClassTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getSimpleName().contentEquals(other.get().getSimpleName()),
          "Expected name of type to be <%s> but was <%s>.",
          expected.getSimpleName(), other.get().getSimpleName());

      scan(expected.getModifiers(), other.get().getModifiers());
      parallelScan(expected.getTypeParameters(), other.get().getTypeParameters());
      scan(expected.getExtendsClause(), other.get().getExtendsClause());
      parallelScan(expected.getImplementsClause(), other.get().getImplementsClause());
      parallelScan(expected.getMembers(), other.get().getMembers());
      return null;
    }

    @Override
    public Void visitConditionalExpression(ConditionalExpressionTree expected, Tree actual) {
      Optional<ConditionalExpressionTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getCondition(), other.get().getCondition());
      scan(expected.getTrueExpression(), other.get().getTrueExpression());
      scan(expected.getFalseExpression(), other.get().getFalseExpression());
      return null;
    }

    @Override
    public Void visitContinue(ContinueTree expected, Tree actual) {
      Optional<ContinueTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getLabel().contentEquals(other.get().getLabel()),
          "Expected label on continue statement to be <%s> but was <%s>.",
          expected.getLabel(), other.get().getLabel());
      return null;
    }

    @Override
    public Void visitDoWhileLoop(DoWhileLoopTree expected, Tree actual) {
      Optional<DoWhileLoopTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getCondition(), other.get().getCondition());
      scan(expected.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitErroneous(ErroneousTree expected, Tree actual) {
      Optional<ErroneousTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      parallelScan(expected.getErrorTrees(), other.get().getErrorTrees());
      return null;
    }

    @Override
    public Void visitExpressionStatement(ExpressionStatementTree expected, Tree actual) {
      Optional<ExpressionStatementTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree expected, Tree actual) {
      Optional<EnhancedForLoopTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getVariable(), other.get().getVariable());
      scan(expected.getExpression(), other.get().getExpression());
      scan(expected.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitForLoop(ForLoopTree expected, Tree actual) {
      Optional<ForLoopTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      parallelScan(expected.getInitializer(), other.get().getInitializer());
      scan(expected.getCondition(), other.get().getCondition());
      parallelScan(expected.getUpdate(), other.get().getUpdate());
      scan(expected.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitIdentifier(IdentifierTree expected, Tree actual) {
      Optional<IdentifierTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getName().contentEquals(other.get().getName()),
          "Expected identifier to be <%s> but was <%s>.",
          expected.getName(), other.get().getName());
      return null;
    }

    @Override
    public Void visitIf(IfTree expected, Tree actual) {
      Optional<IfTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getCondition(), other.get().getCondition());
      scan(expected.getThenStatement(), other.get().getThenStatement());
      scan(expected.getElseStatement(), other.get().getElseStatement());
      return null;
    }

    @Override
    public Void visitImport(ImportTree expected, Tree actual) {
      Optional<ImportTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.isStatic() == other.get().isStatic(),
          "Expected import to be <%s> but was <%s>.",
          expected.isStatic() ? "static" : "non-static",
          other.get().isStatic() ? "static" : "non-static");

      scan(expected.getQualifiedIdentifier(), other.get().getQualifiedIdentifier());
      return null;
    }

    @Override
    public Void visitArrayAccess(ArrayAccessTree expected, Tree actual) {
      Optional<ArrayAccessTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      scan(expected.getIndex(), other.get().getIndex());
      return null;
    }

    @Override
    public Void visitLabeledStatement(LabeledStatementTree expected, Tree actual) {
      Optional<LabeledStatementTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getLabel().contentEquals(other.get().getLabel()),
          "Expected statement label to be <%s> but was <%s>.",
          expected.getLabel(), other.get().getLabel());

      scan(expected.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitLiteral(LiteralTree expected, Tree actual) {
      Optional<LiteralTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(Objects.equal(expected.getValue(), other.get().getValue()),
          "Expected literal value to be <%s> but was <%s>.",
          expected.getValue(), other.get().getValue());
      return null;
    }

    @Override
    public Void visitMethod(MethodTree expected, Tree actual) {
      Optional<MethodTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getName().contentEquals(other.get().getName()),
          "Expected method name to be <%s> but was <%s>.",
          expected.getName(), other.get().getName());

      scan(expected.getModifiers(), other.get().getModifiers());
      scan(expected.getReturnType(), other.get().getReturnType());
      parallelScan(expected.getTypeParameters(), other.get().getTypeParameters());
      parallelScan(expected.getParameters(), other.get().getParameters());
      parallelScan(expected.getThrows(), other.get().getThrows());
      scan(expected.getBody(), other.get().getBody());
      scan(expected.getDefaultValue(), other.get().getDefaultValue());
      return null;
    }

    @Override
    public Void visitModifiers(ModifiersTree expected, Tree actual) {
      Optional<ModifiersTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getFlags().equals(other.get().getFlags()),
          "Expected modifier set to be <%s> but was <%s>.",
          expected.getFlags(), other.get().getFlags());

      parallelScan(expected.getAnnotations(), other.get().getAnnotations());
      return null;
    }

    @Override
    public Void visitNewArray(NewArrayTree expected, Tree actual) {
      Optional<NewArrayTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getType(), other.get().getType());
      parallelScan(expected.getDimensions(), other.get().getDimensions());
      parallelScan(expected.getInitializers(), other.get().getInitializers());
      return null;
    }

    @Override
    public Void visitNewClass(NewClassTree expected, Tree actual) {
      Optional<NewClassTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getEnclosingExpression(), other.get().getEnclosingExpression());
      parallelScan(expected.getTypeArguments(), other.get().getTypeArguments());
      scan(expected.getIdentifier(), other.get().getIdentifier());
      parallelScan(expected.getArguments(), other.get().getArguments());
      scan(expected.getClassBody(), other.get().getClassBody());
      return null;
    }

    @Override
    public Void visitParenthesized(ParenthesizedTree expected, Tree actual) {
      Optional<ParenthesizedTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitReturn(ReturnTree expected, Tree actual) {
      Optional<ReturnTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree expected, Tree actual) {
      Optional<MemberSelectTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getIdentifier().contentEquals(other.get().getIdentifier()),
          "Expected member identifier to be <%s> but was <%s>.",
          expected.getIdentifier(), other.get().getIdentifier());

      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitEmptyStatement(EmptyStatementTree expected, Tree actual) {
      if (!checkTypeAndCast(expected, actual).isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }
      return null;
    }

    @Override
    public Void visitSwitch(SwitchTree expected, Tree actual) {
      Optional<SwitchTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      parallelScan(expected.getCases(), other.get().getCases());
      return null;
    }

    @Override
    public Void visitSynchronized(SynchronizedTree expected, Tree actual) {
      Optional<SynchronizedTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      scan(expected.getBlock(), other.get().getBlock());
      return null;
    }

    @Override
    public Void visitThrow(ThrowTree expected, Tree actual) {
      Optional<ThrowTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitCompilationUnit(CompilationUnitTree expected, Tree actual) {
      Optional<CompilationUnitTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      parallelScan(expected.getPackageAnnotations(), other.get().getPackageAnnotations());
      scan(expected.getPackageName(), other.get().getPackageName());
      parallelScan(expected.getImports(), other.get().getImports());
      parallelScan(expected.getTypeDecls(), other.get().getTypeDecls());
      return null;
    }

    @Override
    public Void visitTry(TryTree expected, Tree actual) {
      Optional<TryTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getBlock(), other.get().getBlock());
      parallelScan(expected.getCatches(), other.get().getCatches());
      scan(expected.getFinallyBlock(), other.get().getFinallyBlock());
      return null;
    }

    @Override
    public Void visitParameterizedType(ParameterizedTypeTree expected, Tree actual) {
      Optional<ParameterizedTypeTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getType(), other.get().getType());
      parallelScan(expected.getTypeArguments(), other.get().getTypeArguments());
      return null;
    }

    @Override
    public Void visitArrayType(ArrayTypeTree expected, Tree actual) {
      Optional<ArrayTypeTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getType(), other.get().getType());
      return null;
    }

    @Override
    public Void visitTypeCast(TypeCastTree expected, Tree actual) {
      Optional<TypeCastTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getType(), other.get().getType());
      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitPrimitiveType(PrimitiveTypeTree expected, Tree actual) {
      Optional<PrimitiveTypeTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getPrimitiveTypeKind() == other.get().getPrimitiveTypeKind(),
          "Expected primitive type kind to be <%s> but was <%s>.",
          expected.getPrimitiveTypeKind(), other.get().getPrimitiveTypeKind());
      return null;
    }

    @Override
    public Void visitTypeParameter(TypeParameterTree expected, Tree actual) {
      Optional<TypeParameterTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getName().contentEquals(other.get().getName()),
          "Expected type parameter name to be <%s> but was <%s>.",
          expected.getName(), other.get().getName());

      parallelScan(expected.getBounds(), other.get().getBounds());
      return null;
    }

    @Override
    public Void visitInstanceOf(InstanceOfTree expected, Tree actual) {
      Optional<InstanceOfTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      scan(expected.getType(), other.get().getType());
      return null;
    }

    @Override
    public Void visitUnary(UnaryTree expected, Tree actual) {
      Optional<UnaryTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getExpression(), other.get().getExpression());
      return null;
    }

    @Override
    public Void visitVariable(VariableTree expected, Tree actual) {
      Optional<VariableTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      checkForDiff(expected.getName().contentEquals(other.get().getName()),
          "Expected variable name to be <%s> but was <%s>.",
          expected.getName(), other.get().getName());

      scan(expected.getModifiers(), other.get().getModifiers());
      scan(expected.getType(), other.get().getType());
      scan(expected.getInitializer(), other.get().getInitializer());
      return null;
    }

    @Override
    public Void visitWhileLoop(WhileLoopTree expected, Tree actual) {
      Optional<WhileLoopTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getCondition(), other.get().getCondition());
      scan(expected.getStatement(), other.get().getStatement());
      return null;
    }

    @Override
    public Void visitWildcard(WildcardTree expected, Tree actual) {
      Optional<WildcardTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getBound(), other.get().getBound());
      return null;
    }

    @Override
    public Void visitOther(Tree expected, Tree actual) {
      throw new UnsupportedOperationException("cannot compare unknown trees");
    }

    private <T extends Tree> Optional<T> checkTypeAndCast(T expected, Tree actual) {
      Kind expectedKind = checkNotNull(expected).getKind();
      Kind treeKind = checkNotNull(actual).getKind();
      if (expectedKind == treeKind) {
        @SuppressWarnings("unchecked")  // checked by Kind
        T treeAsExpectedType = (T) actual;
        return Optional.of(treeAsExpectedType);
      } else {
        return Optional.absent();
      }
    }
  }
}