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