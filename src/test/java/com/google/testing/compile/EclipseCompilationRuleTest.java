package com.google.testing.compile;

import javax.tools.JavaCompiler;

import org.eclipse.jdt.internal.compiler.tool.EclipseCompiler;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.common.base.Supplier;

@RunWith(JUnit4.class)
public class EclipseCompilationRuleTest extends CompilationRuleTest {
  {
    compilationRule = new CompilationRule(new Supplier<JavaCompiler>() {
      @Override
      public JavaCompiler get() {
        return new EclipseCompiler();
      }
    });
  }
}
