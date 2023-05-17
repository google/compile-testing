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

import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

final class FailingGeneratingProcessor extends AbstractProcessor {
  static final String GENERATED_CLASS_NAME = GeneratingProcessor.GENERATED_CLASS_NAME;
  static final String GENERATED_SOURCE = GeneratingProcessor.GENERATED_SOURCE;
  static final String ERROR_MESSAGE = "expected error!";
  final GeneratingProcessor delegate = new GeneratingProcessor();
  Messager messager;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    delegate.init(processingEnv);
    this.messager = processingEnv.getMessager();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    delegate.process(annotations, roundEnv);
    messager.printMessage(Kind.ERROR, ERROR_MESSAGE);
    return false;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return delegate.getSupportedAnnotationTypes();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }
}
