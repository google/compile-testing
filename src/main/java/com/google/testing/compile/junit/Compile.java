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
 * Indicate the method is a compile period test. The test class must extends
 * {@link CompileTestCase}.
 *
 * The annotated method must be public void and one argument
 * {@link RoundEnvironment} .
 *
 * @author Dean Xu (XDean@github.com)
 */
@Retention(RUNTIME)
@Target(METHOD)
@Documented
public @interface Compile {
  /**
   * The source files to compile. Has same rule of
   * {@link Class#getResource(String)}.
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
