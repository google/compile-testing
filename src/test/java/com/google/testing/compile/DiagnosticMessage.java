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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import com.google.common.collect.ImmutableSet;
import java.lang.annotation.Retention;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;

/**
 * Annotated elements will have a diagnostic message whose {@linkplain Kind kind} is determined by a
 * parameter on {@link DiagnosticMessageProcessor}.
 */
@Retention(SOURCE)
public @interface DiagnosticMessage {
  /**
   * Adds diagnostic messages of a specified {@linkplain Kind kind} to elements annotated with
   * {@link DiagnosticMessage}.
   */
  class Processor extends AbstractProcessor {

    private final Diagnostic.Kind kind;

    Processor(Diagnostic.Kind kind) {
      this.kind = kind;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
      return SourceVersion.latest();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
      return ImmutableSet.of(DiagnosticMessage.class.getCanonicalName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      for (Element element : roundEnv.getElementsAnnotatedWith(DiagnosticMessage.class)) {
        processingEnv.getMessager().printMessage(kind, "this is a message", element);
      }
      return true;
    }
  }
}
