/*
 * Copyright (C) 2026 Google, Inc.
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

import static com.google.common.truth.Truth.assertThat;

import java.nio.charset.StandardCharsets;
import javax.tools.FileObject;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link InMemoryJavaFileManager}. */
@RunWith(JUnit4.class)
public final class InMemoryJavaFileManagerTest {

  @Test
  public void getJavaFileForOutput_moduleLocationName() throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    try (StandardJavaFileManager standardFileManager =
            compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(standardFileManager)) {
      // Module locations returned by StandardJavaFileManager.getLocationForModule() have brackets
      // in their name (e.g. "CLASS_OUTPUT[foo]"), which are illegal characters in a URI path. This
      // used to throw IllegalArgumentException.
      JavaFileObject output =
          fileManager.getJavaFileForOutput(moduleLocation(), "com.example.Foo", Kind.CLASS, null);
      assertThat(output).isNotNull();
    }
  }

  @Test
  public void getFileForOutput_moduleLocationName() throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    try (StandardJavaFileManager standardFileManager =
            compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8);
        InMemoryJavaFileManager fileManager = new InMemoryJavaFileManager(standardFileManager)) {
      // Module locations returned by StandardJavaFileManager.getLocationForModule() have brackets
      // in their name (e.g. "CLASS_OUTPUT[foo]"), which are illegal characters in a URI path. This
      // used to throw IllegalArgumentException.
      FileObject output =
          fileManager.getFileForOutput(moduleLocation(), "com.example", "Foo.txt", null);
      assertThat(output).isNotNull();
    }
  }

  private static Location moduleLocation() {
    return new Location() {
      @Override
      public String getName() {
        return "CLASS_OUTPUT[foo]";
      }

      @Override
      public boolean isOutputLocation() {
        return true;
      }
    };
  }
}
