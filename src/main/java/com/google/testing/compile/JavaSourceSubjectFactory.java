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

import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import javax.tools.JavaFileObject;

/**
 * A <a href="https://github.com/truth0/truth">Truth</a> {@link Subject.Factory} similar to
 * {@link JavaSourcesSubjectFactory}, but for working with single source files.
 *
 * @author Gregory Kick
 */
public final class JavaSourceSubjectFactory
    implements Subject.Factory<JavaSourcesSubject.SingleSourceAdapter, JavaFileObject> {
  public static JavaSourceSubjectFactory javaSource() {
    return new JavaSourceSubjectFactory();
  }

  private JavaSourceSubjectFactory() {}

  @Override
  public JavaSourcesSubject.SingleSourceAdapter createSubject(FailureMetadata failureMetadata,
      JavaFileObject subject) {
    return new JavaSourcesSubject.SingleSourceAdapter(failureMetadata, subject);
  }
}
