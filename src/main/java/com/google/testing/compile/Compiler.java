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
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.ToolProvider.getSystemJavaCompiler;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.testing.compile.Compilation.Status;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.jspecify.annotations.Nullable;

/** An object that can {@link #compile} Java source files. */
@AutoValue
// clashes with java.lang.Compiler (which is deprecated for removal in 9)
@SuppressWarnings("JavaLangClash")
public abstract class Compiler {

  /** Returns the {@code javac} compiler. */
  public static Compiler javac() {
    return compiler(getSystemJavaCompiler());
  }

  /** Returns a {@link Compiler} that uses a given {@link JavaCompiler} instance. */
  public static Compiler compiler(JavaCompiler javaCompiler) {
    return new AutoValue_Compiler(
        javaCompiler, ImmutableList.of(), ImmutableList.of(), Optional.empty(), Optional.empty());
  }

  abstract JavaCompiler javaCompiler();

  /** The annotation processors applied during compilation. */
  public abstract ImmutableList<Processor> processors();

  /** The options passed to the compiler. */
  public abstract ImmutableList<String> options();

  /** The compilation class path. If not present, the system class path is used. */
  public abstract Optional<ImmutableList<File>> classPath();

  /**
   * The annotation processor path. If not present, the system annotation processor path is used.
   */
  public abstract Optional<ImmutableList<File>> annotationProcessorPath();

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
    return copy(
        ImmutableList.copyOf(processors), options(), classPath(), annotationProcessorPath());
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
  public final Compiler withOptions(Iterable<? extends Object> options) {
    return copy(
        processors(),
        FluentIterable.from(options).transform(toStringFunction()).toList(),
        classPath(),
        annotationProcessorPath());
  }

  /**
   * Uses the classpath from the passed on classloader (and its parents) for the compilation instead
   * of the system classpath.
   *
   * @throws IllegalArgumentException if the given classloader had classpaths which we could not
   *     determine or use for compilation.
   * @deprecated prefer {@link #withClasspath(Iterable)}. This method only supports {@link
   *     URLClassLoader} and the default system classloader, and {@link File}s are usually a more
   *     natural way to express compilation classpaths than class loaders.
   */
  @Deprecated
  public final Compiler withClasspathFrom(ClassLoader classloader) {
    return copy(
        processors(),
        options(),
        Optional.of(getClasspathFromClassloader(classloader)),
        annotationProcessorPath());
  }

  /** Uses the given classpath for the compilation instead of the system classpath. */
  public final Compiler withClasspath(Iterable<File> classPath) {
    return copy(
        processors(),
        options(),
        Optional.of(ImmutableList.copyOf(classPath)),
        annotationProcessorPath());
  }

  /**
   * Uses the given annotation processor path for the compilation instead of the system annotation
   * processor path.
   */
  public final Compiler withAnnotationProcessorPath(Iterable<File> annotationProcessorPath) {
    return copy(
        processors(),
        options(),
        classPath(),
        Optional.of(ImmutableList.copyOf(annotationProcessorPath)));
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
    try (StandardJavaFileManager standardFileManager = standardFileManager(diagnosticCollector);
        InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(standardFileManager)) {
      fileManager.addSourceFiles(files);
      classPath().ifPresent(path -> setLocation(fileManager, StandardLocation.CLASS_PATH, path));
      annotationProcessorPath()
          .ifPresent(
              path -> setLocation(fileManager, StandardLocation.ANNOTATION_PROCESSOR_PATH, path));

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
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private StandardJavaFileManager standardFileManager(
      DiagnosticCollector<JavaFileObject> diagnosticCollector) {
    return javaCompiler().getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8);
  }

  @VisibleForTesting
  static final @Nullable ClassLoader platformClassLoader = getPlatformClassLoader();

  private static @Nullable ClassLoader getPlatformClassLoader() {
    try {
      // JDK >= 9
      return (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
    } catch (ReflectiveOperationException e) {
      // Java <= 8
      return null;
    }
  }

  /**
   * Returns the current classpaths of the given classloader including its parents.
   *
   * @throws IllegalArgumentException if the given classloader had classpaths which we could not
   *     determine or use for compilation.
   */
  private static ImmutableList<File> getClasspathFromClassloader(ClassLoader classloader) {
    ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

    // Concatenate search paths from all classloaders in the hierarchy 'till the system classloader.
    Set<String> classpaths = new LinkedHashSet<>();
    for (ClassLoader currentClassloader = classloader;
        ;
        currentClassloader = currentClassloader.getParent()) {
      if (currentClassloader == systemClassLoader) {
        Iterables.addAll(
            classpaths,
            Splitter.on(StandardSystemProperty.PATH_SEPARATOR.value())
                .split(StandardSystemProperty.JAVA_CLASS_PATH.value()));
        break;
      }
      if (currentClassloader == platformClassLoader) {
        break;
      }
      if (currentClassloader instanceof URLClassLoader) {
        // We only know how to extract classpaths from URLClassloaders.
        for (URL url : ((URLClassLoader) currentClassloader).getURLs()) {
          if (url.getProtocol().equals("file")) {
            classpaths.add(url.getPath());
          } else {
            throw new IllegalArgumentException(
                "Given classloader consists of classpaths which are "
                    + "unsupported for compilation.");
          }
        }
      } else {
        throw new IllegalArgumentException(
            String.format(
                "Classpath for compilation could not be extracted "
                    + "since %s is not an instance of URLClassloader",
                currentClassloader));
      }
    }

    return classpaths.stream().map(File::new).collect(toImmutableList());
  }

  private static void setLocation(
      InMemoryJavaFileManager fileManager, StandardLocation location, ImmutableList<File> path) {
    try {
      fileManager.setLocation(location, path);
    } catch (IOException e) {
      // impossible by specification
      throw new UncheckedIOException(e);
    }
  }

  private Compiler copy(
      ImmutableList<Processor> processors,
      ImmutableList<String> options,
      Optional<ImmutableList<File>> classPath,
      Optional<ImmutableList<File>> annotationProcessorPath) {
    return new AutoValue_Compiler(
        javaCompiler(), processors, options, classPath, annotationProcessorPath);
  }
}
