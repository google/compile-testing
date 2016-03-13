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

import javax.annotation.CheckReturnValue;
import javax.annotation.processing.Processor;
import javax.tools.JavaCompiler;

/**
 * Creates {@link CompileTester} instances that test compilation with provided {@link Processor}
 * instances.
 *
 * @author Gregory Kick
 */
public interface ProcessedCompileTesterFactory extends CompileTester{

  /** Specify compiler (Javac, Eclipse ECJ, ...) **/
  @CheckReturnValue
  ProcessedCompileTesterFactory withCompiler(JavaCompiler var1);

  /**
   * Adds options that will be passed to the compiler. {@code -Xlint} is the first option, by
   * default.
   */
  @CheckReturnValue ProcessedCompileTesterFactory withCompilerOptions(Iterable<String> options);
  
  /**
   * Adds options that will be passed to the compiler. {@code -Xlint} is the first option, by
   * default.
   */
  @CheckReturnValue ProcessedCompileTesterFactory withCompilerOptions(String... options);
  
  /** Adds {@linkplain Processor annotation processors} to the compilation being tested.  */
  @CheckReturnValue
  CompileTester processedWith(Processor first, Processor... rest);

  /** Adds {@linkplain Processor annotation processors} to the compilation being tested.  */
  @CheckReturnValue
  CompileTester processedWith(Iterable<? extends Processor> processors);
}
