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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Iterables.isEmpty;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
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
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberReferenceTree;
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
import com.sun.source.tree.TreeVisitor;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import javax.lang.model.element.Name;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;

/**
 * A class for determining how two compilation {@code Tree}s differ from each other.
 *
 * <p>This class takes source ordering into account. That is, two isomorphic
 * {@code CompilationUnitTrees} will have {@code TreeDifference} entries if their child
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
   * Returns a {@code TreeDifference} describing the difference between the two {@code
   * CompilationUnitTree}s provided.
   */
  static TreeDifference diffCompilationUnits(
      CompilationUnitTree expected, CompilationUnitTree actual) {
    return createDiff(checkNotNull(expected), checkNotNull(actual), TreeFilter.KEEP_ALL);
  }

  /**
   * Returns a {@code TreeDifference} describing the difference between the actual {@code
   * CompilationUnitTree} provided and the pattern. See {@link
   * JavaFileObjectSubject#containsElementsIn(JavaFileObject)} for more details on how the pattern
   * is used.
   */
  static TreeDifference matchCompilationUnits(
      CompilationUnitTree pattern,
      Trees patternTrees,
      CompilationUnitTree actual,
      Trees actualTrees) {
    checkNotNull(pattern);
    checkNotNull(actual);
    return createDiff(
        pattern, actual, new MatchExpectedTreesFilter(pattern, patternTrees, actual, actualTrees));
  }

  private static TreeDifference createDiff(
      @Nullable CompilationUnitTree expected,
      @Nullable CompilationUnitTree actual,
      TreeFilter treeFilter) {
    TreeDifference.Builder diffBuilder = new TreeDifference.Builder();
    DiffVisitor diffVisitor = new DiffVisitor(diffBuilder, treeFilter);
    diffVisitor.scan(expected, actual);
    return diffBuilder.build();
  }

  /**
   * Returns a {@link TreeDifference} describing the difference between the two sub-{@code Tree}s.
   * The trees diffed are the leaves of the {@link TreePath}s provided.
   *
   * <p>Used for testing.
   */
  static TreeDifference diffSubtrees(
      @Nullable TreePath pathToExpected, @Nullable TreePath pathToActual) {
    TreeDifference.Builder diffBuilder = new TreeDifference.Builder();
    DiffVisitor diffVisitor =
        new DiffVisitor(diffBuilder, TreeFilter.KEEP_ALL, pathToExpected, pathToActual);
    diffVisitor.scan(pathToExpected.getLeaf(), pathToActual.getLeaf());
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
    private final TreeFilter filter;

    DiffVisitor(TreeDifference.Builder diffBuilder, TreeFilter filter) {
      this(diffBuilder, filter, null, null);
    }

    /**
     * Constructs a DiffVisitor whose {@code TreePath}s are initialized with the paths
     * provided.
     */
    private DiffVisitor(TreeDifference.Builder diffBuilder, TreeFilter filter,
        TreePath pathToExpected, TreePath pathToActual) {
      this.diffBuilder = diffBuilder;
      this.filter = filter;
      expectedPath = pathToExpected;
      actualPath = pathToActual;
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
    @FormatMethod
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

    private boolean namesEqual(@Nullable Name expected, @Nullable Name actual) {
      return (expected == null)
          ? actual == null
          : (actual != null && expected.contentEquals(actual));
    }

    private void scan(@Nullable Tree expected, @Nullable Tree actual) {
      if (expected == null && actual != null) {
        diffBuilder.addExtraActualNode(actualPathPlus(actual));
      } else if (expected != null && actual == null) {
        diffBuilder.addExtraExpectedNode(expectedPathPlus(expected));
      } else if (actual != null && expected != null) {
        pushPathAndAccept(expected, actual);
      }
    }

    private void parallelScan(
        Iterable<? extends Tree> expecteds, Iterable<? extends Tree> actuals) {
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
      } else if (expecteds == null && actuals != null && !isEmpty(actuals)) {
        diffBuilder.addExtraActualNode(actualPathPlus(actuals.iterator().next()));
      } else if (actuals == null && expecteds != null && !isEmpty(expecteds)) {
        diffBuilder.addExtraExpectedNode(expectedPathPlus(expecteds.iterator().next()));
      }
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
    public Void visitLambdaExpression(LambdaExpressionTree expected, Tree actual) {
      Optional<LambdaExpressionTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      parallelScan(expected.getParameters(), other.get().getParameters());
      scan(expected.getBody(), other.get().getBody());
      return null;
    }

    @Override
    public Void visitMemberReference(MemberReferenceTree expected, Tree actual) {
      Optional<MemberReferenceTree> other = checkTypeAndCast(expected, actual);
      if (!other.isPresent()) {
        addTypeMismatch(expected, actual);
        return null;
      }

      scan(expected.getQualifierExpression(), other.get().getQualifierExpression());
      parallelScan(expected.getTypeArguments(), other.get().getTypeArguments());
      checkForDiff(expected.getName().contentEquals(other.get().getName()),
          "Expected identifier to be <%s> but was <%s>.",
          expected.getName(), other.get().getName());
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

      checkForDiff(namesEqual(expected.getLabel(), other.get().getLabel()),
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
      parallelScan(
          expected.getMembers(),
          filter.filterActualMembers(
              ImmutableList.copyOf(expected.getMembers()),
              ImmutableList.copyOf(other.get().getMembers())));
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

      checkForDiff(namesEqual(expected.getLabel(), other.get().getLabel()),
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
      parallelScan(
          expected.getImports(),
          filter.filterImports(
              ImmutableList.copyOf(expected.getImports()),
              ImmutableList.copyOf(other.get().getImports())));
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

      parallelScan(expected.getResources(), other.get().getResources());
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

    // TODO(dpb,ronshapiro): rename this method and document which one is cast
    private <T extends Tree> Optional<T> checkTypeAndCast(T expected, Tree actual) {
      Kind expectedKind = checkNotNull(expected).getKind();
      Kind treeKind = checkNotNull(actual).getKind();
      if (expectedKind == treeKind) {
        @SuppressWarnings("unchecked")  // checked by Kind
        T treeAsExpectedType = (T) actual;
        return Optional.of(treeAsExpectedType);
      } else {
        return Optional.empty();
      }
    }
  }

  /** Strategy for determining which {link Tree}s should be diffed in {@link DiffVisitor}. */
  private interface TreeFilter {

    /** Returns the subset of {@code actualMembers} that should be diffed by {@link DiffVisitor}. */
    ImmutableList<Tree> filterActualMembers(
        ImmutableList<Tree> expectedMembers, ImmutableList<Tree> actualMembers);

    /** Returns the subset of {@code actualImports} that should be diffed by {@link DiffVisitor}. */
    ImmutableList<ImportTree> filterImports(
        ImmutableList<ImportTree> expectedImports, ImmutableList<ImportTree> actualImports);

    /** A {@link TreeFilter} that doesn't filter any subtrees out of the actual source AST. */
    TreeFilter KEEP_ALL =
        new TreeFilter() {
          @Override
          public ImmutableList<Tree> filterActualMembers(
              ImmutableList<Tree> expectedMembers, ImmutableList<Tree> actualMembers) {
            return actualMembers;
          }

          @Override
          public ImmutableList<ImportTree> filterImports(
              ImmutableList<ImportTree> expectedImports, ImmutableList<ImportTree> actualImports) {
            return actualImports;
          }
        };
  }

  /**
   * A {@link TreeFilter} that ignores all {@link Tree}s that don't have a matching {@link Tree} in
   * a pattern. For more information on what trees are filtered, see {@link
   * JavaFileObjectSubject#containsElementsIn(JavaFileObject)}.
   */
  private static class MatchExpectedTreesFilter implements TreeFilter {
    private final CompilationUnitTree pattern;
    private final Trees patternTrees;
    private final CompilationUnitTree actual;
    private final Trees actualTrees;

    MatchExpectedTreesFilter(
        CompilationUnitTree pattern,
        Trees patternTrees,
        CompilationUnitTree actual,
        Trees actualTrees) {
      this.pattern = pattern;
      this.patternTrees = patternTrees;
      this.actual = actual;
      this.actualTrees = actualTrees;
    }

    @Override
    public ImmutableList<Tree> filterActualMembers(
        ImmutableList<Tree> patternMembers, ImmutableList<Tree> actualMembers) {
      Set<String> patternVariableNames = new HashSet<>();
      Set<String> patternNestedTypeNames = new HashSet<>();
      Set<MethodSignature> patternMethods = new HashSet<>();
      for (Tree patternTree : patternMembers) {
        patternTree.accept(
            new SimpleTreeVisitor<Void, Void>() {
              @Override
              public Void visitVariable(VariableTree variable, Void p) {
                patternVariableNames.add(variable.getName().toString());
                return null;
              }

              @Override
              public Void visitMethod(MethodTree method, Void p) {
                patternMethods.add(MethodSignature.create(pattern, method, patternTrees));
                return null;
              }

              @Override
              public Void visitClass(ClassTree clazz, Void p) {
                patternNestedTypeNames.add(clazz.getSimpleName().toString());
                return null;
              }
            },
            null);
      }

      ImmutableList.Builder<Tree> filteredActualTrees = ImmutableList.builder();
      for (Tree actualTree : actualMembers) {
        actualTree.accept(new SimpleTreeVisitor<Void, Void>(){
          @Override
          public Void visitVariable(VariableTree variable, Void p) {
            if (patternVariableNames.contains(variable.getName().toString())) {
              filteredActualTrees.add(actualTree);
            }
            return null;
          }

          @Override
          public Void visitMethod(MethodTree method, Void p) {
            if (patternMethods.contains(MethodSignature.create(actual, method, actualTrees))) {
              filteredActualTrees.add(method);
            }
            return null;
          }

          @Override
          public Void visitClass(ClassTree clazz, Void p) {
            if (patternNestedTypeNames.contains(clazz.getSimpleName().toString())) {
              filteredActualTrees.add(clazz);
            }
            return null;
          }

          @Override
          protected Void defaultAction(Tree tree, Void p) {
            filteredActualTrees.add(tree);
            return null;
          }
        }, null);
      }
      return filteredActualTrees.build();
    }

    @Override
    public ImmutableList<ImportTree> filterImports(
        ImmutableList<ImportTree> patternImports, ImmutableList<ImportTree> actualImports) {
      ImmutableSet<String> patternImportsAsStrings =
          patternImports.stream().map(this::fullyQualifiedImport).collect(toImmutableSet());
      return actualImports
          .stream()
          .filter(importTree -> patternImportsAsStrings.contains(fullyQualifiedImport(importTree)))
          .collect(toImmutableList());
    }

    private String fullyQualifiedImport(ImportTree importTree) {
      ImmutableList.Builder<Name> names = ImmutableList.builder();
      importTree.getQualifiedIdentifier().accept(IMPORT_NAMES_ACCUMULATOR, names);
      return Joiner.on('.').join(names.build().reverse());
    }
  }

  private static final TreeVisitor<Void, ImmutableList.Builder<Name>> IMPORT_NAMES_ACCUMULATOR =
      new SimpleTreeVisitor<Void, ImmutableList.Builder<Name>>() {
        @Override
        public Void visitMemberSelect(
            MemberSelectTree memberSelectTree, ImmutableList.Builder<Name> names) {
          names.add(memberSelectTree.getIdentifier());
          return memberSelectTree.getExpression().accept(this, names);
        }

        @Override
        public Void visitIdentifier(
            IdentifierTree identifierTree, ImmutableList.Builder<Name> names) {
          names.add(identifierTree.getName());
          return null;
        }
      };

  @AutoValue
  abstract static class MethodSignature {
    abstract String name();
    abstract ImmutableList<Equivalence.Wrapper<TypeMirror>> parameterTypes();

    static MethodSignature create(
        CompilationUnitTree compilationUnitTree, MethodTree tree, Trees trees) {
      ImmutableList.Builder<Equivalence.Wrapper<TypeMirror>> parameterTypes =
          ImmutableList.builder();
      for (VariableTree parameter : tree.getParameters()) {
        parameterTypes.add(
            MoreTypes.equivalence()
                .wrap(trees.getTypeMirror(trees.getPath(compilationUnitTree, parameter))));
      }
      return new AutoValue_TreeDiffer_MethodSignature(
          tree.getName().toString(), parameterTypes.build());
    }
  }
}
