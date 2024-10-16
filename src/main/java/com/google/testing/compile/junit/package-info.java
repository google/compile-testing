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

/**
 * Framework to quick build compile test in junit.
 * 
 * <ol>
 * <li>Compile period test. Use {@code @Compile} on your test method.
 * 
 * <pre>
 * &#64;Test
 * &#64;Compile(sources = "/HelloWorld.java")
 * public void test(RoundEnvironment env) {
 *   // Now you are in compile (Annotation Processor) context which is compiling your sources.
 * }
 * </pre>
 * 
 * </li>
 * 
 * <li>Compilation test. Use {@code @Compiled} on your test method.
 * 
 * <pre>
 * &#64;Test
 * &#64;Compiled(sources = "/HelloWorld.java")
 * public void test(Compilation c) {
 *   // Now your sources have been compiled.
 *   // Do assert on the Compilation.
 * }
 * </pre>
 * 
 * </li>
 * <ol>
 * 
 * @author Dean Xu (XDean@github.com)
 */
package com.google.testing.compile.junit;