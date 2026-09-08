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
package com.google.testing.compile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import javax.annotation.processing.RoundEnvironment;

import org.junit.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.RunWith;

import com.google.testing.compile.junit.Compile;
import com.google.testing.compile.junit.CompileTestCase;
import com.google.testing.compile.junit.CompileTestRunner;
import com.google.testing.compile.junit.Compiled;

public class JunitCompileTest {

  public static class CompileGolden extends CompileTestCase {
    @Test
    @Compile(sources = "/HelloWorld.java")
    public void test(RoundEnvironment env) throws Exception {
    }

    @Test
    @Compile(sources = "/HelloWorld.java", annotations = Compile.class)
    public void test2(RoundEnvironment env) throws Exception {
    }

    @Test
    public void normal() throws Exception {
    }
  }

  @Test
  public void testCompileGolden() throws Exception {
    success(CompileGolden.class);
  }

  public static class CompiledGolden extends CompileTestCase {
    @Test
    @Compiled(sources = "/HelloWorld.java", processors = PublicNoOpProcessor.class, options = "-Atest=true")
    public void test(Compilation c) throws Exception {
      NoOpProcessor noOpProcessor = (NoOpProcessor) c.compiler().processors().get(0);
      assertTrue(noOpProcessor.invoked);
      assertEquals(Collections.singletonMap("test", "true"), noOpProcessor.options);
    }
  }

  @Test
  public void testCompiledGolden() throws Exception {
    success(CompiledGolden.class);
  }

  public static class CompileNoArg extends CompileTestCase {
    @Test
    @Compile(sources = "/HelloWorld.java")
    public void test() throws Exception {
    }
  }

  @Test
  public void testCompileNoArg() throws Exception {
    fail(CompileNoArg.class, "RoundEnvironment");
  }

  public static class CompiledNoArg extends CompileTestCase {
    @Test
    @Compiled(sources = "/HelloWorld.java")
    public void test() throws Exception {
    }
  }

  @Test
  public void testCompiledNoArg() throws Exception {
    fail(CompiledNoArg.class, "Compilation");
  }

  public static class CompileWrongArg extends CompileTestCase {
    @Test
    @Compile(sources = "/HelloWorld.java")
    public void test(Compilation c) throws Exception {
    }
  }

  @Test
  public void testCompileWrongArg() throws Exception {
    fail(CompileWrongArg.class, "RoundEnvironment");
  }

  public static class CompiledWrongArg extends CompileTestCase {
    @Test
    @Compiled(sources = "/HelloWorld.java")
    public void test(RoundEnvironment env) throws Exception {
    }
  }

  @Test
  public void testCompiledWrongArg() throws Exception {
    fail(CompiledWrongArg.class, "Compilation");
  }

  public static class Both extends CompileTestCase {
    @Test
    @Compiled(sources = "/HelloWorld.java")
    @Compile(sources = "/HelloWorld.java")
    public void test(RoundEnvironment env) throws Exception {
    }
  }

  @Test
  public void testBoth() throws Exception {
    fail(Both.class, "both");
  }

  @RunWith(CompileTestRunner.class)
  public static class NotCompileTestCase {
    @Test
    @Compile(sources = "/HelloWorld.java")
    public void test(RoundEnvironment env) throws Exception {
    }
  }

  @Test
  public void testNotCompileTestCase() throws Exception {
    fail(NotCompileTestCase.class, "CompileTestCase");
  }

  public static class CompileError extends CompileTestCase {
    @Test
    @Compile(sources = "/HelloWorld.java")
    public void test(RoundEnvironment env) throws Exception {
      throw new Exception("CompileError");
    }
  }

  @Test
  public void testCompileError() throws Exception {
    fail(CompileError.class, "CompileError");
  }

  public static class ProcessorConstructError extends CompileTestCase {
    @Test
    @Compiled(sources = "/HelloWorld.java", processors = NoOpProcessor.class)
    public void test(Compilation c) throws Exception {
    }
  }

  @Test
  public void testProcessorConstructError() throws Exception {
    fail(ProcessorConstructError.class, "constructor");
  }

  private void success(Class<?> clz) {
    assertTrue(JUnitCore.runClasses(clz).getFailureCount() == 0);
  }

  private void fail(Class<?> clz, String message) {
    Result result = JUnitCore.runClasses(clz);
    assertTrue(result.getFailureCount() == 1);
    assertTrue(result.getFailures().get(0).getMessage().contains(message));
  }
}
