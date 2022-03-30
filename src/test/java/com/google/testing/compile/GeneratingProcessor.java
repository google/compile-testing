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

import static javax.tools.StandardLocation.CLASS_OUTPUT;

import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.tools.FileObject;

final class GeneratingProcessor extends AbstractProcessor {
  static final String GENERATED_CLASS_NAME = "Blah";
  static final String GENERATED_SOURCE = "final class Blah {\n  String blah = \"blah\";\n}";

  static final String GENERATED_RESOURCE_NAME = "Foo";
  static final String GENERATED_RESOURCE = "Bar";
  
  private final String packageName;

  GeneratingProcessor() {
    this("");
  }
  
  GeneratingProcessor(String packageName) {
    this.packageName = packageName;
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    Filer filer = processingEnv.getFiler();
    try {
      write(filer.createSourceFile(generatedClassName()), GENERATED_SOURCE);
      write(
          filer.createResource(
              CLASS_OUTPUT, getClass().getPackage().getName(), GENERATED_RESOURCE_NAME),
          GENERATED_RESOURCE);

      if (!packageName.isEmpty()) {
        write(filer.createSourceFile(packageName + ".package-info"), generatedPackageInfoSource());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  String packageName() {
    return packageName;
  }

  String generatedClassName() {
    return packageName.isEmpty() ? GENERATED_CLASS_NAME : packageName + "." + GENERATED_CLASS_NAME;
  }

  String generatedPackageInfoSource() {
    return "package " + packageName + ";\n";
  }

  @CanIgnoreReturnValue
  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    return false;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of("*");
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  private static void write(FileObject file, String contents) throws IOException {
    try (Writer writer = file.openWriter()) {
      writer.write(contents);
    }
  }
}
