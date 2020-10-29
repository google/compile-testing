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

import javax.tools.JavaFileObject;

/**
 * Interface for in-memory {@link JavaFileObject}s.
 * Implementations of this interface can be added to the {@link InMemoryJavaFileManager} in some cases.
 * For example, if {@link Compiler#withInMemorySourcePath()} is used.
 */
public interface InMemJavaFileObject extends JavaFileObject {
}
