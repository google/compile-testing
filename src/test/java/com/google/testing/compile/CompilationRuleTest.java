/*
 * Copyright (C) 2014 Google, Inc.
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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Tests the {@link CompilationRule} by applying it to this test.
 */
@RunWith(JUnit4.class)
public class CompilationRuleTest {
  @Rule public CompilationRule compilationRule = new CompilationRule();

  private final AtomicInteger executions = new AtomicInteger();

  @Test public void testMethodsExecuteExactlyOnce() {
    assertThat(executions.getAndIncrement()).isEqualTo(0);
  }

  @Before /* we also make sure that getElements works in a @Before method */
  @Test public void getElements() {
    assertThat(compilationRule.getElements()).isNotNull();
  }

  @Before /* we also make sure that getTypes works in a @Before method */
  @Test public void getTypes() {
    assertThat(compilationRule.getTypes()).isNotNull();
  }

  /**
   * Do some non-trivial operation with {@link Element} instances because they stop working after
   * compilation stops.
   */
  @Test public void elementsAreValidAndWorking() {
    Elements elements = compilationRule.getElements();
    TypeElement stringElement = elements.getTypeElement(String.class.getName());
    assertThat(stringElement.getEnclosingElement())
        .isEqualTo(elements.getPackageElement("java.lang"));
  }

  /**
   * Do some non-trivial operation with {@link TypeMirror} instances because they stop working after
   * compilation stops.
   */
  @Test public void typeMirrorsAreValidAndWorking() {
    Elements elements = compilationRule.getElements();
    Types types = compilationRule.getTypes();
    DeclaredType arrayListOfString = types.getDeclaredType(
        elements.getTypeElement(ArrayList.class.getName()),
        elements.getTypeElement(String.class.getName()).asType());
    DeclaredType listOfExtendsObjectType = types.getDeclaredType(
        elements.getTypeElement(List.class.getName()),
        types.getWildcardType(elements.getTypeElement(Object.class.getName()).asType(), null));
    assertThat(types.isAssignable(arrayListOfString, listOfExtendsObjectType)).isTrue();
  }
}
