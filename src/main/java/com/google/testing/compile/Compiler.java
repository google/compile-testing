/*
 * Copyright (C) 2016 Google, Inc.
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

import static com.google.common.base.Functions.toStringFunction;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import com.google.auto.value.AutoValue;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.Compilation.Status;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;

/** An object that can {@link #compile} Java source files. */
@AutoValue
public abstract class Compiler {

  /** Returns the {@code javac} compiler. */
  public static Compiler javac() {
    return compiler(getSystemJavaCompiler());
  }

  /** Returns a {@link Compiler} that uses a given {@link JavaCompiler} instance. */
  public static Compiler compiler(JavaCompiler javaCompiler) {
    return new AutoValue_Compiler(javaCompiler, ImmutableList.of(), ImmutableList.of());
  }

  abstract JavaCompiler javaCompiler();

  /** The annotation processors applied during compilation. */
  public abstract ImmutableList<Processor> processors();

  /** The options passed to the compiler. */
  public abstract ImmutableList<String> options();

  /**
   * Uses annotation processors during compilation. These replace any previously specified.
   *
   * <p>Note that most annotation processors cannot be reused for more than one compilation.
   *
   * @return a new instance with the same options and the given processors
   */
  public final Compiler withProcessors(Processor... processors) {
    return withProcessors(ImmutableList.copyOf(processors));
  }

  /**
   * Uses annotation processors during compilation. These replace any previously specified.
   *
   * <p>Note that most annotation processors cannot be reused for more than one compilation.
   *
   * @return a new instance with the same options and the given processors
   */
  public final Compiler withProcessors(Iterable<? extends Processor> processors) {
    return copy(ImmutableList.copyOf(processors), options());
  }

  /**
   * Passes command-line options to the compiler. These replace any previously specified.
   *
   * @return a new instance with the same processors and the given options
   */
  public final Compiler withOptions(Object... options) {
    return withOptions(ImmutableList.copyOf(options));
  }

  /**
   * Passes command-line options to the compiler. These replace any previously specified.
   *
   * @return a new instance with the same processors and the given options
   */
  public final Compiler withOptions(Iterable<?> options) {
    return copy(processors(), FluentIterable.from(options).transform(toStringFunction()).toList());
  }

  /**
   * Uses the classpath from the passed on classloader (and its parents) for the compilation
   * instead of the system classpath.
   *
   * @throws IllegalArgumentException if the given classloader had classpaths which we could not
   *     determine or use for compilation.
   */
  public final Compiler withClasspathFrom(ClassLoader classloader) {
    String classpath = getClasspathFromClassloader(classloader);
    ImmutableList<String> options =
        ImmutableList.<String>builder().add("-classpath").add(classpath).addAll(options()).build();
    return copy(processors(), options);
  }

  /**
   * Compiles Java source files.
   *
   * @return the results of the compilation
   */
  public final Compilation compile(JavaFileObject... files) {
    return compile(ImmutableList.copyOf(files));
  }

  /**
   * Compiles Java source files.
   *
   * @return the results of the compilation
   */
  public final Compilation compile(Iterable<? extends JavaFileObject> files) {
    DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
    InMemoryJavaFileManager fileManager =
        new InMemoryJavaFileManager(
            javaCompiler().getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
    CompilationTask task =
        javaCompiler()
            .getTask(
                null, // use the default because old versions of javac log some output on stderr
                fileManager,
                diagnosticCollector,
                options(),
                ImmutableSet.<String>of(),
                files);
    task.setProcessors(processors());
    boolean succeeded = task.call();
    Compilation compilation =
        new Compilation(
            this,
            files,
            succeeded,
            diagnosticCollector.getDiagnostics(),
            fileManager.getOutputFiles());
    if (compilation.status().equals(Status.FAILURE) && compilation.errors().isEmpty()) {
      throw new CompilationFailureException(compilation);
    }
    return compilation;
  }

  /**
   * Returns the current classpaths of the given classloader including its parents.
   *
   * @throws IllegalArgumentException if the given classloader had classpaths which we could not
   *     determine or use for compilation.
   */
  private static String getClasspathFromClassloader(ClassLoader currentClassloader) {
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

    // Add all URLClassloaders in the hirearchy till the system classloader.
    List<URLClassLoader> classloaders = new ArrayList<>();
    while(true) {
      if (currentClassloader instanceof URLClassLoader) {
        // We only know how to extract classpaths from URLClassloaders.
        classloaders.add((URLClassLoader) currentClassloader);
      } else {
        throw new IllegalArgumentException("Classpath for compilation could not be extracted "
            + "since given classloader is not an instance of URLClassloader");
      }
      if (currentClassloader == systemClassLoader) {
        break;
      }
      currentClassloader = currentClassloader.getParent();
    }

    Set<String> classpaths = new LinkedHashSet<>();
    for (URLClassLoader classLoader : classloaders) {
      for (URL url : classLoader.getURLs()) {
        if (url.getProtocol().equals("file")) {
          classpaths.add(url.getPath());
        } else {
          throw new IllegalArgumentException("Given classloader consists of classpaths which are "
              + "unsupported for compilation.");
        }
      }
    }

    return Joiner.on(':').join(classpaths);
  }

  private Compiler copy(ImmutableList<Processor> processors, ImmutableList<String> options) {
    return new AutoValue_Compiler(javaCompiler(), processors, options);
  }
}
