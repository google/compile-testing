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
import static java.util.Objects.requireNonNull;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.CaseFormat;
import com.google.common.base.Equivalence;
import com.google.common.base.Joiner;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.FormatMethod;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TreeVisitor;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.Name;
import javax.lang.model.type.TypeMirror;
import javax.tools.JavaFileObject;
import org.checkerframework.checker.nullness.qual.Nullable;

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
  static TreeDifference diffSubtrees(TreePath pathToExpected, TreePath pathToActual) {
    TreeDifference.Builder diffBuilder = new TreeDifference.Builder();
    DiffVisitor diffVisitor =
        new DiffVisitor(diffBuilder, TreeFilter.KEEP_ALL, pathToExpected, pathToActual);
    diffVisitor.scan(pathToExpected.getLeaf(), pathToActual.getLeaf());
    return diffBuilder.build();
  }

  /**
   * A {@code SimpleTreeVisitor} that traverses a {@link Tree} and an argument {@link Tree},
   * verifying equality along the way. Appends each diff it finds to a {@link
   * TreeDifference.Builder}.
   */
  static final class DiffVisitor extends SimpleTreeVisitor<@Nullable Void, Tree> {
    private @Nullable TreePath expectedPath;
    private @Nullable TreePath actualPath;

    private final TreeDifference.Builder diffBuilder;
    private final TreeFilter filter;

    DiffVisitor(TreeDifference.Builder diffBuilder, TreeFilter filter) {
      this(diffBuilder, filter, null, null);
    }

    /** Constructs a DiffVisitor whose {@code TreePath}s are initialized with the paths provided. */
    private DiffVisitor(
        TreeDifference.Builder diffBuilder,
        TreeFilter filter,
        @Nullable TreePath pathToExpected,
        @Nullable TreePath pathToActual) {
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
     * Adds a {@code TwoWayDiff} that is parameterized by the {@code Tree}s and message format
     * provided.
     */
    @FormatMethod
    private void reportDiff(String message, Object... formatArgs) {
      diffBuilder.addDifferingNodes(
          requireNonNull(expectedPath),
          requireNonNull(actualPath),
          String.format(message, formatArgs));
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
     * Pushes the {@code expected} and {@code actual} {@link Tree}s onto their respective {@link
     * TreePath}s and recurses with {@code expected.accept(this, actual)}, popping the stack when
     * the call completes.
     *
     * <p>This should be the ONLY place where either {@link TreePath} is mutated.
     */
    private @Nullable Void pushPathAndAccept(Tree expected, Tree actual) {
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

    /**
     * {@inheritDoc}
     *
     * <p>The exact set of {@code visitFoo} methods depends on the compiler version. For example, if
     * the compiler is for a version of the language that has the {@code yield} statement, then
     * there will be a {@code visitYield(YieldTree)}. But if it's for an earlier version, then not
     * only will there not be that method, there will also not be a {@code YieldTree} type at all.
     * That means it is impossible for this class to have a complete set of visit methods and also
     * compile on earlier versions.
     *
     * <p>Instead, we override {@link SimpleTreeVisitor#defaultAction} and inspect the visited tree
     * with reflection. We can use {@link Tree.Kind#getInterface()} to get the specific interface,
     * such as {@code YieldTree}, and within that interface we just look for {@code getFoo()}
     * methods. The {@code actual} tree must have the same {@link Tree.Kind} and then we can compare
     * the results of calling the corresponding {@code getFoo()} methods on both trees. The
     * comparison depends on the return type of the method:
     *
     * <ul>
     *   <li>For a method returning {@link Tree} or a subtype, we call {@link #scan(Tree, Tree)},
     *       which will visit the subtrees recursively.
     *   <li>For a method returning a type that is assignable to {@code Iterable<? extends Tree>},
     *       we call {@link #parallelScan(Iterable, Iterable)}.
     *   <li>For a method returning {@link Name}, we compare with {@link Name#contentEquals}.
     *   <li>Otherwise we just compare with {@link Objects#equals(Object, Object)}.
     *   <li>Methods returning certain types are ignored: {@link LineMap}, because we don't care if
     *       the line numbers don't match between the two trees; {@link JavaFileObject}, because the
     *       value for two distinct trees will never compare equal.
     * </ul>
     *
     * <p>This technique depends on the specific way the tree interfaces are defined. In practice it
     * works well. Besides solving the {@code YieldTree} issue, it also ensures we don't overlook
     * properties of any given tree type, include properties that may be added in later versions.
     * For example, in versions that have sealed interfaces, the {@code permits} clause is
     * represented by a method {@code ClassTree.getPermitsClause()}. Earlier versions obviously
     * don't have that method.
     */
    @Override
    public @Nullable Void defaultAction(Tree expected, Tree actual) {
      if (expected.getKind() != actual.getKind()) {
        addTypeMismatch(expected, actual);
        return null;
      }
      Class<? extends Tree> treeInterface = expected.getKind().asInterface();
      for (Method method : treeInterface.getMethods()) {
        if (method.getName().startsWith("get") && method.getParameterTypes().length == 0) {
          Object expectedValue;
          Object actualValue;
          try {
            expectedValue = method.invoke(expected);
            actualValue = method.invoke(actual);
          } catch (ReflectiveOperationException e) {
            throw new VerifyException(e);
          }
          defaultCompare(method, expected.getKind(), expectedValue, actualValue);
        }
      }
      return null;
    }

    private void defaultCompare(Method method, Tree.Kind kind, Object expected, Object actual) {
      Type type = method.getGenericReturnType();
      if (isIterableOfTree(type)) {
        @SuppressWarnings("unchecked")
        Iterable<? extends Tree> expectedList = (Iterable<? extends Tree>) expected;
        @SuppressWarnings("unchecked")
        Iterable<? extends Tree> actualList = (Iterable<? extends Tree>) actual;
        actualList = filterActual(method, kind, expectedList, actualList);
        parallelScan(expectedList, actualList);
      } else if (type instanceof Class<?> && Tree.class.isAssignableFrom((Class<?>) type)) {
        scan((Tree) expected, (Tree) actual);
      } else if (expected instanceof LineMap && actual instanceof LineMap) {
        return; // we don't require lines to match exactly
      } else if (expected instanceof JavaFileObject && actual instanceof JavaFileObject) {
        return; // these will never be equal unless the inputs are identical
      } else {
        boolean eq =
            (expected instanceof Name)
                ? namesEqual((Name) expected, (Name) actual)
                : Objects.equals(expected, actual);
        if (!eq) {
          // If MemberSelectTree.getIdentifier() doesn't match, we will say
          //    "Expected member-select identifier to be <foo> but was <bar>."
          String treeKind =
              CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, kind.name());
          String property =
              CaseFormat.UPPER_CAMEL
                  .to(CaseFormat.LOWER_UNDERSCORE, method.getName().substring("get".length()))
                  .replace('_', ' ');
          reportDiff(
              "Expected %s %s to be <%s> but was <%s>.",
              treeKind,
              property,
              expected,
              actual);
        }
      }
    }

    /**
     * Applies {@link #filter} to the list of subtrees from the actual tree. If it is a
     * {@code CompilationUnitTree} then we filter its imports. If it is a {@code ClassTree} then we
     * filter its members.
     */
    private Iterable<? extends Tree> filterActual(
        Method method,
        Tree.Kind kind,
        Iterable<? extends Tree> expected,
        Iterable<? extends Tree> actual) {
      switch (kind) {
        case COMPILATION_UNIT:
          if (method.getName().equals("getImports")) {
            @SuppressWarnings("unchecked")
            Iterable<ImportTree> expectedImports = (Iterable<ImportTree>) expected;
            @SuppressWarnings("unchecked")
            Iterable<ImportTree> actualImports = (Iterable<ImportTree>) actual;
            return filter.filterImports(
                ImmutableList.copyOf(expectedImports), ImmutableList.copyOf(actualImports));
          }
          break;
        case CLASS:
          if (method.getName().equals("getMembers")) {
            return filter.filterActualMembers(
                ImmutableList.copyOf(expected), ImmutableList.copyOf(actual));
          }
          break;
        default:
      }
      return actual;
    }

    private static boolean isIterableOfTree(Type type) {
      if (!(type instanceof ParameterizedType)) {
        return false;
      }
      ParameterizedType parameterizedType = (ParameterizedType) type;
      if (!Iterable.class.isAssignableFrom((Class<?>) parameterizedType.getRawType())
          || parameterizedType.getActualTypeArguments().length != 1) {
        return false;
      }
      Type argType = parameterizedType.getActualTypeArguments()[0];
      if (argType instanceof Class<?>) {
        return Tree.class.isAssignableFrom((Class<?>) argType);
      } else if (argType instanceof WildcardType) {
        WildcardType wildcardType = (WildcardType) argType;
        return wildcardType.getUpperBounds().length == 1
            && wildcardType.getUpperBounds()[0] instanceof Class<?>
            && Tree.class.isAssignableFrom((Class<?>) wildcardType.getUpperBounds()[0]);
      } else {
        return false;
      }
    }

    @Override
    public @Nullable Void visitOther(Tree expected, Tree actual) {
      throw new UnsupportedOperationException("cannot compare unknown trees");
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
            new SimpleTreeVisitor<@Nullable Void, @Nullable Void>() {
              @Override
              public @Nullable Void visitVariable(VariableTree variable, @Nullable Void p) {
                patternVariableNames.add(variable.getName().toString());
                return null;
              }

              @Override
              public @Nullable Void visitMethod(MethodTree method, @Nullable Void p) {
                patternMethods.add(MethodSignature.create(pattern, method, patternTrees));
                return null;
              }

              @Override
              public @Nullable Void visitClass(ClassTree clazz, @Nullable Void p) {
                patternNestedTypeNames.add(clazz.getSimpleName().toString());
                return null;
              }
            },
            null);
      }

      ImmutableList.Builder<Tree> filteredActualTrees = ImmutableList.builder();
      for (Tree actualTree : actualMembers) {
        actualTree.accept(
            new SimpleTreeVisitor<@Nullable Void, @Nullable Void>() {
              @Override
              public @Nullable Void visitVariable(VariableTree variable, @Nullable Void p) {
                if (patternVariableNames.contains(variable.getName().toString())) {
                  filteredActualTrees.add(actualTree);
                }
                return null;
              }

              @Override
              public @Nullable Void visitMethod(MethodTree method, @Nullable Void p) {
                if (patternMethods.contains(MethodSignature.create(actual, method, actualTrees))) {
                  filteredActualTrees.add(method);
                }
                return null;
              }

              @Override
              public @Nullable Void visitClass(ClassTree clazz, @Nullable Void p) {
                if (patternNestedTypeNames.contains(clazz.getSimpleName().toString())) {
                  filteredActualTrees.add(clazz);
                }
                return null;
              }

              @Override
              protected @Nullable Void defaultAction(Tree tree, @Nullable Void p) {
                filteredActualTrees.add(tree);
                return null;
              }
            },
            null);
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

  private static final TreeVisitor<@Nullable Void, ImmutableList.Builder<Name>>
      IMPORT_NAMES_ACCUMULATOR =
          new SimpleTreeVisitor<@Nullable Void, ImmutableList.Builder<Name>>() {
            @Override
            public @Nullable Void visitMemberSelect(
                MemberSelectTree memberSelectTree, ImmutableList.Builder<Name> names) {
              names.add(memberSelectTree.getIdentifier());
              return memberSelectTree.getExpression().accept(this, names);
            }

            @Override
            public @Nullable Void visitIdentifier(
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
