/*
 * Copyright (C) 2020 Google, Inc.
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

import com.google.common.collect.ImmutableSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

final class CopyingProcessor extends AbstractProcessor {
  private static final String POSTFIX = "_Copy";

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Filer filer = processingEnv.getFiler();
    Messager messager = processingEnv.getMessager();
    for (Element element : roundEnv.getRootElements()) {
      if (!element.getKind().isClass() && !element.getKind().isInterface()) continue;
      TypeElement typeElement = (TypeElement) element;
      String simpleName = typeElement.getSimpleName().toString();
      if (simpleName.endsWith(POSTFIX)) continue; // skip generated files
      try (Writer writer = filer.createSourceFile(typeElement.getQualifiedName().toString() + POSTFIX).openWriter()) {
        FileObject fileObject = filer.getResource(StandardLocation.SOURCE_PATH,
            getPackageName(typeElement.getQualifiedName().toString()), simpleName + ".java");
        String content = fileObject.getCharContent(false).toString();
        writer.write(content.replaceAll(simpleName, simpleName + POSTFIX));
      } catch (IOException e) {
        messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage(), typeElement);
        break;
      }
    }
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

  private String getPackageName(String qualifiedName) {
    if (qualifiedName.contains(".")) {
      return qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
    } else {
      return "";
    }
  }
}
