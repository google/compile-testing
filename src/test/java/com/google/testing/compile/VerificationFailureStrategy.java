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

import com.google.common.truth.AbstractFailureStrategy;
import com.google.common.truth.TestVerb;

/** @deprecated prefer {@link com.google.common.truth.ExpectFailure} for testing Truth failures. */
@Deprecated
final class VerificationFailureStrategy extends AbstractFailureStrategy {
  static final class VerificationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    VerificationException(String message) {
      super(message);
    }
  }

  /**
   * A {@link TestVerb} that throws something other than {@link AssertionError}.
   *
   * @deprecated prefer {@link com.google.common.truth.ExpectFailure} for testing Truth failures.
   */
  @Deprecated static final TestVerb VERIFY = new TestVerb(new VerificationFailureStrategy());

  @Override
  public void fail(String message, Throwable unused) {
    throw new VerificationFailureStrategy.VerificationException(message);
  }
}
