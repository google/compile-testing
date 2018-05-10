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

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;

/**
 * Indicate the test method is a compile period test.
 * 
 * The test class must extends {@link CompileTestCase}. The method also need annotated {@code @Test}
 * and it must be public void and have one argument with type {@link RoundEnvironment}.
 *
 * @author Dean Xu (XDean@github.com)
 */
@Retention(RUNTIME)
@Target(METHOD)
@Documented
public @interface Compile {
  /**
   * The source files to compile. Has same rule of {@link Class#getResource(String)}.
   */
  String[] sources();

  /**
   * Supported annotations. Empty means '*'.
   */
  Class<? extends Annotation>[] annotations() default {};

  /**
   * Supported source version.
   */
  SourceVersion version() default SourceVersion.RELEASE_8;
}
