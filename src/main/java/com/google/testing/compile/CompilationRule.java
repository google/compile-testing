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

import static com.google.common.base.Preconditions.checkState;
import static com.google.testing.compile.Compilation.Status.SUCCESS;
import static com.google.testing.compile.Compiler.javac;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.JUnit4;
import org.junit.runners.model.Statement;

/**
 * A {@link JUnit4} {@link Rule} that executes tests such that a instances of {@link Elements} and
 * {@link Types} are available during execution.
 *
 * <p>To use this rule in a test, just add the following field:
 *
 * <pre>{@code @Rule} public CompilationRule compilationRule = new CompilationRule();</pre>
 *
 * @author Gregory Kick
 */
public final class CompilationRule implements TestRule {
  private static final JavaFileObject DUMMY =
      JavaFileObjects.forSourceLines("Dummy", "final class Dummy {}");

  private Elements elements;
  private Types types;

  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        EvaluatingProcessor evaluatingProcessor = new EvaluatingProcessor(base);
        Compilation compilation = javac().withProcessors(evaluatingProcessor).compile(DUMMY);
        checkState(compilation.status().equals(SUCCESS), compilation);
        evaluatingProcessor.throwIfStatementThrew();
      }
    };
  }
  
  /**
   * Returns the {@link Elements} instance associated with the current execution of the rule.
   *
   * @throws IllegalStateException if this method is invoked outside the execution of the rule.
   */
  public Elements getElements() {
    checkState(elements != null, "Not running within the rule");
    return elements;
  }

  /**
   * Returns the {@link Types} instance associated with the current execution of the rule.
   *
   * @throws IllegalStateException if this method is invoked outside the execution of the rule.
   */
  public Types getTypes() {
    checkState(types != null, "Not running within the rule");
    return types;
  }

  final class EvaluatingProcessor extends AbstractProcessor {

    final Statement base;
    Throwable thrown;

    EvaluatingProcessor(Statement base) {
      this.base = base;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of("*");
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
      super.init(processingEnv);
      elements = processingEnv.getElementUtils();
      types = processingEnv.getTypeUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      // just run the test on the last round after compilation is over
      if (roundEnv.processingOver()) {
        try {
          base.evaluate();
        } catch (Throwable e) {
          thrown = e;
        }
      }
      return false;
    }

    /** Throws what the base {@link Statement} threw, if anything. */
    void throwIfStatementThrew() throws Throwable {
      if (thrown != null) {
        throw thrown;
      }
    }
  }
}
