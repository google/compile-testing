/*
 * Copyright (C) 2018 Google, Inc.
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
import static com.google.common.base.Preconditions.checkState;
import static com.google.testing.compile.Compilation.Status.SUCCESS;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

/**
 * A Junit 5 {@link Extension} that extends a test suite such that an instance of {@link Elements}
 * and {@link Types} are available through parameter injection during execution.
 *
 * <p>To use this extension, request it with {@link ExtendWith} and add the required parameters:
 *
 * <pre>
 * {@code @ExtendWith}(CompilationExtension.class)
 * class CompilerTest {
 *   {@code @Test} void testElements({@link Elements} elements, {@link Types} types) {
 *     // Any methods of the supplied utility classes can now be accessed.
 *   }
 * }
 * </pre>
 *
 * @author David van Leusen
 */
public class CompilationExtension implements BeforeAllCallback, BeforeEachCallback,
    AfterAllCallback, AfterEachCallback, ParameterResolver {
  private static final JavaFileObject DUMMY =
      JavaFileObjects.forSourceLines("Dummy", "final class Dummy {}");
  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(CompilationExtension.class);

  private static final Executor DEFAULT_COMPILER_EXECUTOR = Executors.newCachedThreadPool(
      new ThreadFactoryBuilder().setDaemon(true).setNameFormat("async-compiler-%d").build()
  );

  private static final Map<Class<?>, Function<ProcessingEnvironment, ?>> SUPPORTED_PARAMETERS;

  static {
    SUPPORTED_PARAMETERS = ImmutableMap.<Class<?>, Function<ProcessingEnvironment, ?>>builder()
        .put(Elements.class, ProcessingEnvironment::getElementUtils)
        .put(Types.class, ProcessingEnvironment::getTypeUtils)
        .build();
  }

  private final Executor compilerExecutor;

  public CompilationExtension(Executor compilerExecutor) {
    this.compilerExecutor = compilerExecutor;
  }

  public CompilationExtension() {
    this(DEFAULT_COMPILER_EXECUTOR);
  }

  @Override
  public void beforeAll(ExtensionContext context) throws InterruptedException {
    final CompilerState state = context.getStore(NAMESPACE).getOrComputeIfAbsent(
        CompilerState.class,
        ignored -> new CompilerState(this.compilerExecutor, TestInstance.Lifecycle.PER_CLASS),
        CompilerState.class
    );

    checkState(state.prepareForTests(), state);
  }

  @Override
  public void beforeEach(ExtensionContext context) throws InterruptedException {
    final CompilerState state = context.getStore(NAMESPACE).getOrComputeIfAbsent(
        CompilerState.class,
        ignored -> new CompilerState(this.compilerExecutor, TestInstance.Lifecycle.PER_METHOD),
        CompilerState.class
    );

    checkState(state.prepareForTests(), state);
  }

  @Override
  public void afterEach(ExtensionContext context) throws Exception {
    final CompilerState state = checkNotNull(context.getStore(NAMESPACE).get(
        CompilerState.class,
        CompilerState.class
    ));

    if (state.getLifecycle() == TestInstance.Lifecycle.PER_METHOD) {
      // Created on a per-method basis, must clean up as a mirror action
      final Compilation compilation = state.allowTermination();
      checkState(compilation.status().equals(SUCCESS), compilation);
    }
  }

  @Override
  public void afterAll(ExtensionContext context) throws ExecutionException, InterruptedException {
    final CompilerState state = checkNotNull(context.getStore(NAMESPACE).get(
        CompilerState.class,
        CompilerState.class
    ));

    checkState(state.getLifecycle() == TestInstance.Lifecycle.PER_CLASS);

    final Compilation compilation = state.allowTermination();
    checkState(compilation.status().equals(SUCCESS), compilation);
  }

  @Override
  public boolean supportsParameter(
      ParameterContext parameterContext,
      ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    final Class<?> parameterType = parameterContext.getParameter().getType();
    return SUPPORTED_PARAMETERS.containsKey(parameterType);
  }

  @Override
  public Object resolveParameter(
      ParameterContext parameterContext,
      ExtensionContext extensionContext
  ) throws ParameterResolutionException {
    final CompilerState state = extensionContext.getStore(NAMESPACE).get(
        CompilerState.class,
        CompilerState.class
    );

    checkState(state != null, "CompilerState not initialized");

    return SUPPORTED_PARAMETERS.getOrDefault(
        parameterContext.getParameter().getType(),
        ignored -> {
          throw new ParameterResolutionException("Unknown parameter type");
        }
    ).apply(state.getProcessingEnvironment());
  }

  static final class CompilerState implements ExtensionContext.Store.CloseableResource {
    private final AtomicReference<ProcessingEnvironment> sharedState;
    private final Phaser syncBarrier;
    private final CompletableFuture<Compilation> result;
    private final TestInstance.Lifecycle lifecycle;

    CompilerState(Executor compilerExecutor, TestInstance.Lifecycle lifecycle) {
      this.lifecycle = lifecycle;
      this.sharedState = new AtomicReference<>(null);
      this.syncBarrier = new Phaser(2) {
        @Override
        protected boolean onAdvance(int phase, int parties) {
          // Terminate the phaser once all parties have deregistered
          return parties == 0;
        }
      };
      this.result = CompletableFuture.supplyAsync(
          new EvaluatingProcessor(syncBarrier, sharedState),
          compilerExecutor
      );
    }

    ProcessingEnvironment getProcessingEnvironment() throws ParameterResolutionException {
      // Only while the phaser is in phase 1 should the ProcessingEnvironment be valid.
      if (this.syncBarrier.getPhase() != 1) {
        throw new ParameterResolutionException(this.toString());
      }

      final ProcessingEnvironment processingEnvironment = this.sharedState.get();
      if (processingEnvironment != null) {
        return processingEnvironment;
      } else {
        throw new ParameterResolutionException(
            String.format("ProcessingEnvironment was not initialized: %s", this)
        );
      }
    }

    TestInstance.Lifecycle getLifecycle() {
      return this.lifecycle;
    }

    boolean prepareForTests() throws InterruptedException {
      switch (this.syncBarrier.getPhase()) {
        case 0: // Compiler has been started, but might not yet be initialized
          return checkNotTerminated(this.syncBarrier.arriveAndAwaitAdvance());
        case 1: // Compiler has been initialized, ready for tests
          return true;
        default:
          throw new IllegalStateException(this.toString());
      }
    }

    Compilation allowTermination() throws InterruptedException, ExecutionException {
      if (this.syncBarrier.getPhase() == 1) {
        checkState(this.syncBarrier.arriveAndDeregister() == 1, this);
      } else if (!this.syncBarrier.isTerminated()) {
        throw new IllegalStateException(this.toString());
      }

      try {
        final Compilation result = this.result.get(1, TimeUnit.SECONDS);
        checkState(this.syncBarrier.isTerminated(), this);
        return result;
      } catch (TimeoutException e) {
        // This really should never happen, since the 'syncBarrier' is the only thing the
        //   processor blocks on, deregistering at this point should allow the processor
        //   to run until it finishes.
        throw new AssertionError("Timed out waiting for the compiler to finish");
      }
    }

    private boolean checkNotTerminated(int phaseNumber) throws InterruptedException {
      if (phaseNumber < 0) {
        // Phaser has terminated unexpectedly, throw exception based on result.

        try {
          // 'Successful' result
          final Compilation result = this.result.get(5, TimeUnit.SECONDS);
          throw new IllegalStateException(
              String.format("Anomalous compilation result: %s", result)
          );
        } catch (ExecutionException e) {
          // Exception in the compiler
          throw new IllegalStateException("Exception during annotation processing", e.getCause());
        } catch (TimeoutException e) {
          // This really should never happen, since the 'syncBarrier' is the only thing the
          //   processor blocks on, termination should mean it runs until it finished,
          //   resolving 'result'
          throw new AssertionError("Timed out waiting for the cause of termination");
        }
      }

      return true;
    }

    @Override
    public void close() {
      // If the owning ExtensionContext.Store is closed, ensure the compilation terminates as well
      this.syncBarrier.forceTermination();
    }

    @Override
    public String toString() {
      return "CompilerState{" +
          "sharedState=" + sharedState +
          ", syncBarrier=" + syncBarrier +
          ", result=" + result +
          ", lifecycle=" + lifecycle +
          '}';
    }
  }

  static final class EvaluatingProcessor extends AbstractProcessor
      implements Supplier<Compilation> {
    private final Phaser syncBarrier;
    private final AtomicReference<ProcessingEnvironment> sharedState;

    EvaluatingProcessor(
        Phaser syncBarrier,
        AtomicReference<ProcessingEnvironment> sharedState
    ) {
      this.syncBarrier = syncBarrier;
      this.sharedState = sharedState;
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
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
      super.init(processingEnvironment);

      // Share the processing environment
      checkState(
          sharedState.compareAndSet(null, processingEnvironment),
          "Shared ProcessingEnvironment was already initialized"
      );
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver()) {
        // Synchronize on the beginning of the test run
        syncBarrier.arriveAndAwaitAdvance();

        // Now wait until testing is over
        syncBarrier.awaitAdvance(syncBarrier.arriveAndDeregister());

        // Clean up the shared state
        sharedState.lazySet(null);
      }
      return false;
    }

    @Override
    public Compilation get() {
      try {
        return Compiler.javac().withProcessors(this).compile(DUMMY);
      } finally {
        syncBarrier.forceTermination();
      }
    }
  }
}
