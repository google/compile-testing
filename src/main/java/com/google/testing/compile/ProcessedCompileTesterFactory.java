/*
 * Copyright (C) 2013 Google, Inc.
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

import java.io.File;
import javax.annotation.CheckReturnValue;
import javax.annotation.processing.Processor;

/**
 * Creates {@link CompileTester} instances that test compilation with provided {@link Processor}
 * instances.
 *
 * @author Gregory Kick
 */
@CheckReturnValue
public interface ProcessedCompileTesterFactory {

  /**
   * Adds options that will be passed to the compiler. {@code -Xlint} is the first option, by
   * default.
   */
  ProcessedCompileTesterFactory withCompilerOptions(Iterable<String> options);

  /**
   * Adds options that will be passed to the compiler. {@code -Xlint} is the first option, by
   * default.
   */
  ProcessedCompileTesterFactory withCompilerOptions(String... options);

  /**
   * Attempts to extract the classpath from the classpath of the Classloader argument, including all
   * its parents up to (and including) the System Classloader.
   *
   * <p>If not specified, we will use the System classpath for compilation.
   *
   * @deprecated prefer {@link #withClasspath(Iterable)}. This method only supports {@link
   *     java.net.URLClassLoader} and the default system classloader, and {@link File}s are usually
   *     a more natural way to expression compilation classpaths than class loaders.
   */
  @Deprecated
  ProcessedCompileTesterFactory withClasspathFrom(ClassLoader classloader);

  /**
   * Sets the compilation classpath.
   *
   * <p>If not specified, we will use the System classpath for compilation.
   */
  ProcessedCompileTesterFactory withClasspath(Iterable<File> classPath);

  /** Adds {@linkplain Processor annotation processors} to the compilation being tested. */
  CompileTester processedWith(Processor first, Processor... rest);

  /** Adds {@linkplain Processor annotation processors} to the compilation being tested. */
  CompileTester processedWith(Iterable<? extends Processor> processors);
}
