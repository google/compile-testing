package com.google.testing.compile.junit;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.tools.JavaFileObject;

import org.junit.Test;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

public class CompileTestRunner extends BlockJUnit4ClassRunner {

  public CompileTestRunner(Class<?> klass) throws InitializationError {
    super(klass);
  }

  @Override
  protected void collectInitializationErrors(List<Throwable> errors) {
    super.collectInitializationErrors(errors);
    if (!CompileTestCase.class.isAssignableFrom(getTestClass().getJavaClass())) {
      errors.add(new Exception("CompileTestRunner must run with CompileTestCase"));
    }
  }

  @Override
  protected void validateTestMethods(List<Throwable> errors) {
    validatePublicVoidNoArgMethods(Test.class, false, errors);
  }

  @Override
  protected Statement methodInvoker(FrameworkMethod method, Object test) {
    CompileTestCase ct = (CompileTestCase) test;
    if (method.getMethod().isAnnotationPresent(Compile.class)) {
      ct.setMethod(method);
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          // Compile compile = AnnotationUtils.getAnnotation(method.getMethod(), Compile.class);
          Compile compile = method.getMethod().getAnnotation(Compile.class);
          Class<?> clz = getTestClass().getJavaClass();
          Compiler.javac()
              .withProcessors(ct)
              .compile(Arrays.stream(compile.sources())
                  .map(s -> clz.getResource(s))
                  .map(u -> JavaFileObjects.forResource(u))
                  .toArray(JavaFileObject[]::new));
          if (ct.getError() != null) {
            throw ct.getError();
          }
        }
      };
    } else if (method.getMethod().isAnnotationPresent(Compiled.class)) {
      return new Statement() {
        @Override
        public void evaluate() throws Throwable {
          // Compiled compiled = AnnotationUtils.getAnnotation(method.getMethod(), Compiled.class);
          Compiled compiled = method.getMethod().getAnnotation(Compiled.class);
          Class<?> clz = getTestClass().getJavaClass();
          Compilation compilation = Compiler.javac()
              .withProcessors(Arrays.stream(compiled.processors())
                  .map(c -> {
                    try {
                      return c.newInstance();
                    } catch (Exception e) {
                      throw new IllegalStateException("Annotation Processor must has no-arg public constructor");
                    }
                  })
                  .toArray(Processor[]::new))
              .withOptions(Arrays.stream(compiled.options()).toArray(Object[]::new))
              .compile(Arrays.stream(compiled.sources())
                  .map(s -> clz.getResource(s))
                  .map(u -> JavaFileObjects.forResource(u))
                  .toArray(JavaFileObject[]::new));
          method.invokeExplosively(ct, compilation);
        }
      };
    } else {
      return super.methodInvoker(method, test);
    }
  }

  @Override
  protected void validatePublicVoidNoArgMethods(Class<? extends Annotation> annotation, boolean isStatic,
      List<Throwable> errors) {
    List<FrameworkMethod> methods = getTestClass().getAnnotatedMethods(annotation);
    for (FrameworkMethod method : methods) {
      method.validatePublicVoid(isStatic, errors);
      boolean compile = method.getMethod().isAnnotationPresent(Compile.class);
      boolean compiled = method.getMethod().isAnnotationPresent(Compiled.class);
      if (compile && compiled) {
        errors.add(new Exception("Method " + method.getName() + " can't annotated both @Compile and @Compiled"));
      } else if (compile) {
        int count = method.getMethod().getParameterCount();
        if (count != 1 || !method.getMethod().getParameterTypes()[0].isAssignableFrom(RoundEnvironment.class)) {
          errors.add(new Exception(
              "Method " + method.getName() + " must have only one param with type RoundEnvironment"));
        }
      } else if (compiled) {
        int count = method.getMethod().getParameterCount();
        if (count != 1 || !method.getMethod().getParameterTypes()[0].isAssignableFrom(Compilation.class)) {
          errors.add(new Exception(
              "Method " + method.getName() + " must have only one param with type Compilation"));
        }
      } else {
        method.validatePublicVoidNoArg(isStatic, errors);
      }
    }
  }
}
