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
package com.google.testing.compile.junit;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.model.FrameworkMethod;

/**
 * Extends this class and use {@code @Compile} and {@code @Compiled} to do test for compilation.
 * 
 * @see Compile
 * @see Compiled
 * @author Dean Xu (XDean@github.com)
 */
@Ignore
@RunWith(CompileTestRunner.class)
public class CompileTestCase extends AbstractProcessor {
  private FrameworkMethod method;
  private Optional<Compile> anno;
  private Throwable error;

  protected Types types;
  protected Elements elements;
  protected Messager messager;
  protected Filer filer;

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    messager = processingEnv.getMessager();
    types = processingEnv.getTypeUtils();
    elements = processingEnv.getElementUtils();
    filer = processingEnv.getFiler();
  }

  public void setMethod(FrameworkMethod method) {
    this.method = method;
    this.anno = Optional.ofNullable(method.getAnnotation(Compile.class));
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      return false;
    }
    try {
      method.invokeExplosively(this, roundEnv);
    } catch (Throwable e) {
      error = e;
    }
    return false;
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return anno.map(c -> c.version()).orElse(SourceVersion.RELEASE_8);
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return anno.map(co -> Arrays.stream(co.annotations())
        .map(c -> c.getName())
        .collect(Collectors.toSet()))
        .filter(s -> !s.isEmpty())
        .orElse(Collections.singleton("*"));
  }

  public Throwable getError() {
    return error;
  }
}