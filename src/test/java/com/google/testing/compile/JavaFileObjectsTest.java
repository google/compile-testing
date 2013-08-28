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

import static javax.tools.JavaFileObject.Kind.CLASS;
import static org.truth0.Truth.ASSERT;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.tools.JavaFileObject;

import org.junit.Test;

import com.google.common.io.Resources;

/**
 *  Tests {@link JavaFileObjects}.
 *
 *  @author Gregory Kick
 */
public class JavaFileObjectsTest {
  @Test public void forResource_inJarFile() throws URISyntaxException, IOException {
    JavaFileObject resourceInJar = JavaFileObjects.forResource("java/lang/Object.class");
    ASSERT.that(resourceInJar.getKind()).isEqualTo(CLASS);
    ASSERT.that(resourceInJar.toUri()).isEqualTo(URI.create("/java/lang/Object.class"));
    ASSERT.that(resourceInJar.getName())
        .isEqualTo(Resources.getResource("java/lang/Object.class").toString());
    ASSERT.that(resourceInJar.isNameCompatible("Object", CLASS));
  }
}
