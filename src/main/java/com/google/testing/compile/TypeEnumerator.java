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

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.Function;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreeScanner;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Provides information about the set of types that are declared by a {@code CompilationUnitTree}.
 *
 * @author Stephen Pratt
 */
final class TypeEnumerator {
  private static final TypeScanner nameVisitor = new TypeScanner();
  private TypeEnumerator() {}

  /**
   * Returns a set of strings containing the fully qualified names of all
   * the types that are declared by the given CompilationUnitTree
   */
  static ImmutableSet<String> getTopLevelTypes(CompilationUnitTree t) {
    return ImmutableSet.copyOf(nameVisitor.scan(t, null));
  }

  /** A {@link TreeScanner} for determining type declarations */
  @SuppressWarnings("restriction") // Sun APIs usage intended
  static final class TypeScanner extends TreeScanner<Set<String>, @Nullable Void> {
    @Override
    public Set<String> scan(Tree node, @Nullable Void v) {
      return firstNonNull(super.scan(node, v), ImmutableSet.<String>of());
    }

    @Override
    public Set<String> reduce(Set<String> r1, Set<String> r2) {
      return Sets.union(r1, r2);
    }

    @Override
    public Set<String> visitClass(ClassTree reference, @Nullable Void v) {
      return ImmutableSet.of(reference.getSimpleName().toString());
    }

    @Override
    public Set<String> visitExpressionStatement(
        ExpressionStatementTree reference, @Nullable Void v) {
      return scan(reference.getExpression(), v);
    }

    @Override
    public Set<String> visitIdentifier(IdentifierTree reference, @Nullable Void v) {
      return ImmutableSet.of(reference.getName().toString());
    }

    @Override
    public Set<String> visitMemberSelect(MemberSelectTree reference, @Nullable Void v) {
      Set<String> expressionSet = scan(reference.getExpression(), v);
      if (expressionSet.size() != 1) {
        throw new AssertionError("Internal error in NameFinder. Expected to find exactly one "
            + "identifier in the expression set. Found " + expressionSet);
      }
      String expressionStr = expressionSet.iterator().next();
      return ImmutableSet.of(String.format("%s.%s", expressionStr, reference.getIdentifier()));
    }

    @Override
    public Set<String> visitCompilationUnit(CompilationUnitTree reference, @Nullable Void v) {
      Set<String> packageSet = reference.getPackageName() == null ?
          ImmutableSet.of("") : scan(reference.getPackageName(), v);
      if (packageSet.size() != 1) {
        throw new AssertionError("Internal error in NameFinder. Expected to find at most one " +
            "package identifier. Found " + packageSet);
      }
      final String packageName = packageSet.isEmpty() ? "" : packageSet.iterator().next();
      Set<String> typeDeclSet = firstNonNull(scan(reference.getTypeDecls(), v), ImmutableSet.of());
      return FluentIterable.from(typeDeclSet)
          .transform(new Function<String, String>() {
            @Override public String apply(String typeName) {
              return packageName.isEmpty() ? typeName :
                  String.format("%s.%s", packageName, typeName);
            }
          }).toSet();
    }
  }
}