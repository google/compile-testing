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
package com.google.testing.compile.junit;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.annotation.processing.Processor;

import com.google.testing.compile.Compilation;

/**
 * Indicate the test method is a compiled test.
 * 
 * The test class must extends {@link CompileTestCase}. The method also need annotated {@code @Test}
 * and it must be public void and have only one argument with type {@link Compilation}.
 *
 * @author Dean Xu (XDean@github.com)
 */
@Retention(RUNTIME)
@Target(METHOD)
@Documented
public @interface Compiled {
  /**
   * The source files to compile. Has same rule of {@link Class#getResource(String)}.
   */
  String[] sources();

  /**
   * Processors to use in this compile. Every class must have public no-arg constructor.
   */
  Class<? extends Processor>[] processors() default {};

  /**
   * Options to use in this compile. Should be like "-Akey=value".
   */
  String[] options() default {};
}
