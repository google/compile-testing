package com.google.testing.compile.junit;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.annotation.processing.Processor;

import com.google.testing.compile.Compilation;

/**
 * Indicate the method is a compiled test. The test class must extends
 * {@link CompileTestCase}.
 *
 * The annotated method must be public void and one argument
 * {@link Compilation}.
 *
 * @author Dean Xu (XDean@github.com)
 */
@Retention(RUNTIME)
@Target(METHOD)
@Documented
public @interface Compiled {
  /**
   * The source files to compile. Has same rule of
   * {@link Class#getResource(String)}.
   */
  String[] sources();

  /**
   * Processors to use in this compile.
   */
  Class<? extends Processor>[] processors() default {};

  /**
   * Options to use in this compile. Should starts with "-A".
   */
  String[] options() default {};
}
