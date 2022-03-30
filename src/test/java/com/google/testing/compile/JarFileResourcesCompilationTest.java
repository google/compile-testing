/*
 * Copyright (C) 2013 Google, Inc.
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

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

import com.google.common.io.Resources;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * An integration test to ensure that testing works when resources are in jar files.
 *
 * @author Gregory Kick
 * @author Christian Gruber
 */
@RunWith(JUnit4.class)
public class JarFileResourcesCompilationTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();
  private File jarFile;

  @Before
  public void createJarFile() throws IOException {
    this.jarFile = folder.newFile("test.jar");
    JarOutputStream out = new JarOutputStream(new FileOutputStream(jarFile));
    JarEntry helloWorldEntry = new JarEntry("test/HelloWorld.java");
    out.putNextEntry(helloWorldEntry);
    out.write(Resources.toByteArray(Resources.getResource("test/HelloWorld.java")));
    out.close();
  }

  @Test
  public void compilesResourcesInJarFiles() throws IOException {
    assert_().about(javaSource())
      .that(JavaFileObjects.forResource(
          new URL("jar:" + jarFile.toURI() + "!/test/HelloWorld.java")))
      .compilesWithoutError();
  }
}
