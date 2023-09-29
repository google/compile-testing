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

import static com.google.common.base.Preconditions.checkState;
import static com.google.testing.compile.JavaFileObjects.asByteSource;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.MANDATORY_WARNING;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.Diagnostic.Kind.WARNING;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collector;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;

/** The results of {@linkplain Compiler#compile compiling} source files. */
public final class Compilation {

  private final Compiler compiler;
  private final ImmutableList<JavaFileObject> sourceFiles;
  private final Status status;
  private final ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics;
  private final ImmutableList<JavaFileObject> generatedFiles;

  Compilation(
      Compiler compiler,
      Iterable<? extends JavaFileObject> sourceFiles,
      boolean successful,
      Iterable<Diagnostic<? extends JavaFileObject>> diagnostics,
      Iterable<JavaFileObject> generatedFiles) {
    this.compiler = compiler;
    this.sourceFiles = ImmutableList.copyOf(sourceFiles);
    this.status = successful ? Status.SUCCESS : Status.FAILURE;
    this.diagnostics = ImmutableList.copyOf(diagnostics);
    this.generatedFiles = ImmutableList.copyOf(generatedFiles);
  }

  /** The compiler. */
  public Compiler compiler() {
    return compiler;
  }

  /** The source files compiled. */
  public ImmutableList<JavaFileObject> sourceFiles() {
    return sourceFiles;
  }

  /** The status of the compilation. */
  public Status status() {
    return status;
  }

  /**
   * All diagnostics reported during compilation. The order of the returned list is unspecified.
   *
   * @see #errors()
   * @see #warnings()
   * @see #notes()
   */
  public ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics() {
    return diagnostics;
  }

  /** {@linkplain Diagnostic.Kind#ERROR Errors} reported during compilation. */
  public ImmutableList<Diagnostic<? extends JavaFileObject>> errors() {
    return diagnosticsOfKind(ERROR);
  }

  /**
   * {@linkplain Diagnostic.Kind#WARNING Warnings} (including {@linkplain
   * Diagnostic.Kind#MANDATORY_WARNING mandatory warnings}) reported during compilation.
   */
  public ImmutableList<Diagnostic<? extends JavaFileObject>> warnings() {
    return diagnosticsOfKind(WARNING, MANDATORY_WARNING);
  }

  /** {@linkplain Diagnostic.Kind#NOTE Notes} reported during compilation. */
  public ImmutableList<Diagnostic<? extends JavaFileObject>> notes() {
    return diagnosticsOfKind(NOTE);
  }

  ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsOfKind(Kind kind, Kind... more) {
    ImmutableSet<Kind> kinds = Sets.immutableEnumSet(kind, more);
    return diagnostics()
        .stream()
        .filter(diagnostic -> kinds.contains(diagnostic.getKind()))
        .collect(toImmutableList());
  }

  /**
   * Files generated during compilation.
   *
   * @throws IllegalStateException for {@linkplain #status() failed compilations}, since the state
   *     of the generated files is undefined in that case
   */
  public ImmutableList<JavaFileObject> generatedFiles() {
    checkState(
        status.equals(Status.SUCCESS),
        "compilation failed, so generated files are unavailable. %s",
        describeFailureDiagnostics());
    return generatedFiles;
  }

  /**
   * Source files generated during compilation.
   *
   * @throws IllegalStateException for {@linkplain #status() failed compilations}, since the state
   *     of the generated files is undefined in that case
   */
  public ImmutableList<JavaFileObject> generatedSourceFiles() {
    return generatedFiles()
        .stream()
        .filter(generatedFile -> generatedFile.getKind().equals(JavaFileObject.Kind.SOURCE))
        .collect(toImmutableList());
  }

  /**
   * Returns the file at {@code path} if one was generated.
   *
   * <p>For example:
   *
   * <pre>
   * {@code Optional<JavaFileObject>} fooClassFile =
   *     compilation.generatedFile(CLASS_OUTPUT, "com/google/myapp/Foo.class");
   * </pre>
   *
   * @throws IllegalStateException for {@linkplain #status() failed compilations}, since the state
   *     of the generated files is undefined in that case
   */
  public Optional<JavaFileObject> generatedFile(Location location, String path) {
    // We're relying on the implementation of location.getName() to be equivalent to the first
    // part of the path.
    String expectedFilename = String.format("%s/%s", location.getName(), path);
    return generatedFiles().stream()
        .filter(generated -> requireNonNull(generated.toUri().getPath()).endsWith(expectedFilename))
        .findFirst();
  }

  /**
   * Returns the file with name {@code fileName} in package {@code packageName} if one was
   * generated.
   *
   * <p>For example:
   *
   * <pre>
   * {@code Optional<JavaFileObject>} fooClassFile =
   *     compilation.generatedFile(CLASS_OUTPUT, "com.google.myapp", "Foo.class");
   * </pre>
   *
   * @throws IllegalStateException for {@linkplain #status() failed compilations}, since the state
   *     of the generated files is undefined in that case
   */
  public Optional<JavaFileObject> generatedFile(
      Location location, String packageName, String fileName) {
    return generatedFile(
        location,
        packageName.isEmpty() ? fileName : packageName.replace('.', '/') + '/' + fileName);
  }

  /**
   * Returns the source file for the type with a given qualified name (no ".java" extension) if one
   * was generated.
   *
   * <p>For example:
   *
   * <pre>
   * {@code Optional<JavaFileObject>} fooSourceFile =
   *     compilation.generatedSourceFile("com.google.myapp.Foo");
   * </pre>
   *
   * @throws IllegalStateException for {@linkplain #status() failed compilations}, since the state
   *     of the generated files is undefined in that case
   */
  public Optional<JavaFileObject> generatedSourceFile(String qualifiedName) {
    int lastIndexOfDot = qualifiedName.lastIndexOf('.');
    String packageName = lastIndexOfDot == -1 ? "" : qualifiedName.substring(0, lastIndexOfDot);
    String fileName = qualifiedName.substring(lastIndexOfDot + 1) + ".java";
    return generatedFile(SOURCE_OUTPUT, packageName, fileName);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder
        .append("compilation of ")
        .append(sourceFiles.stream().map(JavaFileObject::getName).collect(toList()));
    if (!compiler.processors().isEmpty()) {
      builder.append(" using annotation processors ").append(compiler.processors());
    }
    if (!compiler.options().isEmpty()) {
      builder.append(" passing options ").append(compiler.options());
    }
    return builder.toString();
  }

  /** Returns a description of the why the compilation failed. */
  String describeFailureDiagnostics() {
    ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics = diagnostics();
    if (diagnostics.isEmpty()) {
      return "Compilation produced no diagnostics.\n";
    }
    StringBuilder message = new StringBuilder("Compilation produced the following diagnostics:\n");
    diagnostics.forEach(diagnostic -> message.append(diagnostic).append('\n'));
    return message.toString();
  }

  /** Returns a description of the source file generated by this compilation. */
  String describeGeneratedSourceFiles() {
    ImmutableList<JavaFileObject> generatedSourceFiles =
        generatedFiles
            .stream()
            .filter(generatedFile -> generatedFile.getKind().equals(JavaFileObject.Kind.SOURCE))
            .collect(toImmutableList());
    if (generatedSourceFiles.isEmpty()) {
      return "No files were generated.\n";
    } else {
      StringBuilder message = new StringBuilder("Generated Source Files\n======================\n");
      for (JavaFileObject generatedFile : generatedSourceFiles) {
        message.append(describeGeneratedFile(generatedFile));
      }
      return message.toString();
    }
  }

  /** Returns a description of the contents of a given generated file. */
  private String describeGeneratedFile(JavaFileObject generatedFile) {
    try {
      StringBuilder entry = new StringBuilder("\n").append(generatedFile.getName()).append(":\n");
      if (generatedFile.getKind().equals(CLASS)) {
        entry.append(
            String.format(
                "  [generated class file (%d bytes)]", asByteSource(generatedFile).size()));
      } else {
        entry.append(generatedFile.getCharContent(true));
      }
      return entry.append('\n').toString();
    } catch (IOException e) {
      throw new IllegalStateException(
          "Couldn't read from JavaFileObject when it was already in memory.", e);
    }
  }

  private static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return collectingAndThen(toList(), ImmutableList::copyOf);
  }

  /** The status of a compilation. */
  public enum Status {

    /** Compilation finished without errors. */
    SUCCESS,

    /** Compilation finished with errors. */
    FAILURE,
  }
}
