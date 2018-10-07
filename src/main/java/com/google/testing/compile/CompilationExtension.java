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
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
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
public class CompilationExtension
    implements BeforeAllCallback, AfterAllCallback, ParameterResolver {
  private static final JavaFileObject DUMMY =
      JavaFileObjects.forSourceLines("Dummy", "final class Dummy {}");
  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(CompilationExtension.class);
  private static final Function<ProcessingEnvironment, ?> UNKNOWN_PARAMETER = ignored -> {
    throw new IllegalArgumentException("Unknown parameter type");
  };

  private static final StoreAccessor<Phaser> PHASER_KEY = new StoreAccessor<>(Phaser.class);
  private static final StoreAccessor<AtomicReference<ProcessingEnvironment>> PROCESSINGENV_KEY =
      new StoreAccessor<>(ProcessingEnvironment.class);
  private static final StoreAccessor<CompletionStage<Compilation>> RESULT_KEY =
      new StoreAccessor<>(Compilation.class);

  private static final ExecutorService COMPILER_EXECUTOR = Executors.newCachedThreadPool(
      new ThreadFactoryBuilder().setDaemon(true).setNameFormat("async-compiler-%d").build()
  );

  private static final Map<Class<?>, Function<ProcessingEnvironment, ?>> SUPPORTED_PARAMETERS;

  static {
    SUPPORTED_PARAMETERS = ImmutableMap.<Class<?>, Function<ProcessingEnvironment, ?>>builder()
        .put(Elements.class, ProcessingEnvironment::getElementUtils)
        .put(Types.class, ProcessingEnvironment::getTypeUtils)
        .build();
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    final Phaser sharedBarrier = new Phaser(2) {
      @Override
      protected boolean onAdvance(int phase, int parties) {
        // Terminate the phaser once all parties have deregistered
        return parties == 0;
      }
    };

    final AtomicReference<ProcessingEnvironment> sharedState
        = new AtomicReference<>(null);

    final CompletionStage<Compilation> futureResult = CompletableFuture.supplyAsync(() ->
            Compiler.javac()
                .withProcessors(new EvaluatingProcessor(sharedBarrier, sharedState))
                .compile(DUMMY),
        COMPILER_EXECUTOR);

    final ExtensionContext.Store store = context.getStore(NAMESPACE);
    PHASER_KEY.put(store, sharedBarrier);
    PROCESSINGENV_KEY.put(store, sharedState);
    RESULT_KEY.put(store, futureResult);

    // Wait until the processor is ready for testing
    sharedBarrier.arriveAndAwaitAdvance();

    checkState(!sharedBarrier.isTerminated(), "Phaser terminated early");
  }

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    final ExtensionContext.Store store = context.getStore(NAMESPACE);
    final Phaser sharedPhaser = PHASER_KEY.get(store);

    // Allow the processor to finish
    sharedPhaser.arriveAndDeregister();

    // Perform status checks, since processing is 'over' almost instantly
    final Compilation compilation = RESULT_KEY.get(store)
        .toCompletableFuture().get(1, TimeUnit.SECONDS);
    checkState(compilation.status().equals(SUCCESS), compilation);

    // Check postcondition
    checkState(sharedPhaser.isTerminated(), "Phaser not terminated");
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
    final ExtensionContext.Store store = extensionContext.getStore(NAMESPACE);
    final AtomicReference<ProcessingEnvironment> processingEnvironment
        = PROCESSINGENV_KEY.get(store);

    return SUPPORTED_PARAMETERS.getOrDefault(
        parameterContext.getParameter().getType(),
        UNKNOWN_PARAMETER
    ).apply(checkNotNull(
        processingEnvironment.get(),
        "ProcessingEnvironment not available: %s",
        RESULT_KEY.get(store)
    ));
  }

  /**
   * Utility class to safely access {@link ExtensionContext.Store} when dealing with
   * parameterized types.
   */
  static final class StoreAccessor<R> {
    private final Object key;

    StoreAccessor(Object key) {
      this.key = key;
    }

    @SuppressWarnings("unchecked")
    R get(ExtensionContext.Store store) {
      return (R) store.get(key);
    }

    void put(ExtensionContext.Store store, R value) {
      store.put(key, value);
    }
  }

  static final class EvaluatingProcessor extends AbstractProcessor {
    private final Phaser barrier;
    private final AtomicReference<ProcessingEnvironment> sharedState;

    EvaluatingProcessor(
        Phaser barrier,
        AtomicReference<ProcessingEnvironment> sharedState
    ) {
      this.barrier = barrier;
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
      if (!sharedState.compareAndSet(null, processingEnvironment)) {
        // Invalid state, init() run twice
        barrier.forceTermination();
        throw new IllegalStateException("Processor initialized twice");
      }
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      if (roundEnv.processingOver()) {
        // Synchronize on the beginning of the test run
        barrier.arriveAndAwaitAdvance();

        // Now wait until testing is over
        barrier.awaitAdvance(barrier.arriveAndDeregister());

        // Clean up the shared state
        sharedState.getAndSet(null);
      }
      return false;
    }
  }
}
