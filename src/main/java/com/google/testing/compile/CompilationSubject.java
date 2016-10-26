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

import static com.google.common.collect.Iterables.size;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.Compilation.Status.FAILURE;
import static com.google.testing.compile.Compilation.Status.SUCCESS;
import static com.google.testing.compile.JavaFileObjectSubject.javaFileObjects;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.MANDATORY_WARNING;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.Diagnostic.Kind.WARNING;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import javax.tools.Diagnostic;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

/** A {@link Truth} subject for a {@link Compilation}. */
public final class CompilationSubject extends Subject<CompilationSubject, Compilation> {

  private static final SubjectFactory<CompilationSubject, Compilation> FACTORY =
      new CompilationSubjectFactory();

  /** Returns a {@link SubjectFactory} for a {@link Compilation}. */
  public static SubjectFactory<CompilationSubject, Compilation> compilations() {
    return FACTORY;
  }

  /** Starts making assertions about a {@link Compilation}. */
  public static CompilationSubject assertThat(Compilation actual) {
    return assertAbout(compilations()).that(actual);
  }

  CompilationSubject(FailureStrategy failureStrategy, Compilation actual) {
    super(failureStrategy, actual);
  }

  /** Asserts that the compilation succeeded. */
  public void succeeded() {
    if (actual().status().equals(FAILURE)) {
      failureStrategy.fail(actual().describeErrors() + actual().describeGeneratedSourceFiles());
    }
  }

  /** Asserts that the compilation succeeded without warnings. */
  public void succeededWithoutWarnings() {
    succeeded();
    hadWarningCount(0);
  }

  /** Asserts that the compilation failed. */
  public void failed() {
    if (actual().status().equals(SUCCESS)) {
      failureStrategy.fail(
          "Compilation was expected to fail, but contained no errors.\n\n"
              + actual().describeGeneratedSourceFiles());
    }
  }

  /** Asserts that the compilation had exactly {@code expectedCount} errors. */
  public void hadErrorCount(int expectedCount) {
    checkDiagnosticCount(expectedCount, ERROR);
  }

  /** Asserts that there was at least one error containing {@code expectedSubstring}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadErrorContaining(String expectedSubstring) {
    return hadDiagnosticContaining(expectedSubstring, ERROR);
  }

  /** Asserts that there was at least one error containing a match for {@code expectedPattern}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadErrorContainingMatch(String expectedPattern) {
    return hadDiagnosticContainingMatch(expectedPattern, ERROR);
  }

  /** Asserts that there was at least one error containing a match for {@code expectedPattern}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadErrorContainingMatch(Pattern expectedPattern) {
    return hadDiagnosticContainingMatch(expectedPattern, ERROR);
  }

  /** Asserts that the compilation had exactly {@code expectedCount} warnings. */
  public void hadWarningCount(int expectedCount) {
    checkDiagnosticCount(expectedCount, WARNING, MANDATORY_WARNING);
  }

  /** Asserts that there was at least one warning containing {@code expectedSubstring}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadWarningContaining(String expectedSubstring) {
    return hadDiagnosticContaining(expectedSubstring, WARNING, MANDATORY_WARNING);
  }

  /** Asserts that there was at least one warning containing a match for {@code expectedPattern}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadWarningContainingMatch(String expectedPattern) {
    return hadDiagnosticContainingMatch(expectedPattern, WARNING, MANDATORY_WARNING);
  }

  /** Asserts that there was at least one warning containing a match for {@code expectedPattern}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadWarningContainingMatch(Pattern expectedPattern) {
    return hadDiagnosticContainingMatch(expectedPattern, WARNING, MANDATORY_WARNING);
  }

  /** Asserts that the compilation had exactly {@code expectedCount} notes. */
  public void hadNoteCount(int expectedCount) {
    checkDiagnosticCount(expectedCount, NOTE);
  }

  /** Asserts that there was at least one note containing {@code expectedSubstring}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadNoteContaining(String expectedSubstring) {
    return hadDiagnosticContaining(expectedSubstring, NOTE);
  }

  /** Asserts that there was at least one note containing a match for {@code expectedPattern}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadNoteContainingMatch(String expectedPattern) {
    return hadDiagnosticContainingMatch(expectedPattern, NOTE);
  }

  /** Asserts that there was at least one note containing a match for {@code expectedPattern}. */
  @CanIgnoreReturnValue
  public DiagnosticInFile hadNoteContainingMatch(Pattern expectedPattern) {
    return hadDiagnosticContainingMatch(expectedPattern, NOTE);
  }

  private void checkDiagnosticCount(
      int expectedCount, Diagnostic.Kind kind, Diagnostic.Kind... more) {
    Iterable<Diagnostic<? extends JavaFileObject>> diagnostics =
        actual().diagnosticsOfKind(kind, more);
    int actualCount = size(diagnostics);
    if (actualCount != expectedCount) {
      failureStrategy.fail(
          messageListing(
              diagnostics,
              "Expected %d %s, but found the following %d %s:",
              expectedCount,
              kindToString(kind, true),
              actualCount,
              kindToString(kind, true)));
    }
  }

  private static String messageListing(
      Iterable<? extends Diagnostic<?>> diagnostics, String headingFormat, Object... formatArgs) {
    StringBuilder listing =
        new StringBuilder(String.format(headingFormat, formatArgs)).append('\n');
    for (Diagnostic<?> diagnostic : diagnostics) {
      listing.append(diagnostic.getMessage(null)).append('\n');
    }
    return listing.toString();
  }

  /**
   * Returns a string representation of a diagnostic kind.
   *
   * @param expectingSpecificCount {@code true} if being used after a count, as in "Expected 5
   *     errors"; {@code false} if being used to describe one message, as in "Expected a warning
   *     containing…".
   */
  private static String kindToString(Diagnostic.Kind kind, boolean expectingSpecificCount) {
    switch (kind) {
      case ERROR:
        return expectingSpecificCount ? "errors" : "an error";

      case MANDATORY_WARNING:
      case WARNING:
        return expectingSpecificCount ? "warnings" : "a warning";

      case NOTE:
        return expectingSpecificCount ? "notes" : "a note";

      case OTHER:
        return expectingSpecificCount ? "diagnostic messages" : "a diagnostic message";

      default:
        throw new AssertionError(kind);
    }
  }

  private DiagnosticInFile hadDiagnosticContaining(
      String expectedSubstring, Diagnostic.Kind kind, Diagnostic.Kind... more) {
    return hadDiagnosticContainingMatch(
        String.format("containing \"%s\"", expectedSubstring),
        Pattern.compile(Pattern.quote(expectedSubstring)),
        kind,
        more);
  }

  private DiagnosticInFile hadDiagnosticContainingMatch(
      String expectedPattern, Diagnostic.Kind kind, Diagnostic.Kind... more) {
    return hadDiagnosticContainingMatch(Pattern.compile(expectedPattern), kind, more);
  }

  private DiagnosticInFile hadDiagnosticContainingMatch(
      Pattern expectedPattern, Diagnostic.Kind kind, Diagnostic.Kind... more) {
    return hadDiagnosticContainingMatch(
        String.format("containing match for /%s/", expectedPattern), expectedPattern, kind, more);
  }

  private DiagnosticInFile hadDiagnosticContainingMatch(
      String verb, final Pattern expectedPattern, Diagnostic.Kind kind, Diagnostic.Kind... more) {
    ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsOfKind =
        actual().diagnosticsOfKind(kind, more);
    ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsWithMessage =
        diagnosticsOfKind
            .stream()
            .filter(diagnostic -> expectedPattern.matcher(diagnostic.getMessage(null)).find())
            .collect(toImmutableList());
    if (diagnosticsWithMessage.isEmpty()) {
      failureStrategy.fail(
          messageListing(
              diagnosticsOfKind,
              "Expected %s %s, but only found:",
              kindToString(kind, false),
              verb));
    }
    return new DiagnosticInFile(kind, diagnosticsWithMessage);
  }

  /**
   * Asserts that compilation generated a file named {@code fileName} in package {@code
   * packageName}.
   */
  @CanIgnoreReturnValue
  public JavaFileObjectSubject generatedFile(
      Location location, String packageName, String fileName) {
    return checkGeneratedFile(
        actual().generatedFile(location, packageName, fileName),
        location,
        "named \"%s\" in %s",
        fileName,
        packageName.isEmpty()
            ? "the default package"
            : String.format("package \"%s\"", packageName));
  }

  /** Asserts that compilation generated a file at {@code path}. */
  @CanIgnoreReturnValue
  public JavaFileObjectSubject generatedFile(Location location, String path) {
    return checkGeneratedFile(actual().generatedFile(location, path), location, path);
  }

  /** Asserts that compilation generated a source file for a type with a given qualified name. */
  @CanIgnoreReturnValue
  public JavaFileObjectSubject generatedSourceFile(String qualifiedName) {
    return generatedFile(
        StandardLocation.SOURCE_OUTPUT, qualifiedName.replaceAll("\\.", "/") + ".java");
  }

  private JavaFileObjectSubject checkGeneratedFile(
      Optional<JavaFileObject> generatedFile, Location location, String format, Object... args) {
    if (!generatedFile.isPresent()) {
      StringBuilder builder = new StringBuilder("generated the file ");
      builder.append(args.length == 0 ? format : String.format(format, args));
      builder.append("; it generated:\n");
      for (JavaFileObject generated : actual().generatedFiles()) {
        if (generated.toUri().getPath().contains(location.getName())) {
          builder.append("  ").append(generated.toUri().getPath()).append('\n');
        }
      }
      fail(builder.toString());
    }
    return check().about(javaFileObjects()).that(generatedFile.get());
  }

  private static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return collectingAndThen(toList(), ImmutableList::copyOf);
  }

  private static class DiagnosticsAssertions {
    private final ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics;

    DiagnosticsAssertions(Iterable<Diagnostic<? extends JavaFileObject>> diagnostics) {
      this.diagnostics = ImmutableList.copyOf(diagnostics);
    }

    ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsMatching(
        Predicate<? super Diagnostic<? extends JavaFileObject>> predicate) {
      return diagnostics.stream().filter(predicate).collect(toImmutableList());
    }

    ImmutableSet<String> mapDiagnostics(
        Function<? super Diagnostic<? extends JavaFileObject>, ? extends String> mapper) {
      return diagnostics
          .stream()
          .map(mapper)
          .collect(collectingAndThen(toList(), ImmutableSet::copyOf));
    }
  }

  /** Assertions that a note, warning, or error was found in a given file. */
  public final class DiagnosticInFile extends DiagnosticsAssertions {

    private final Diagnostic.Kind kind;

    private DiagnosticInFile(
        Diagnostic.Kind kind,
        Iterable<Diagnostic<? extends JavaFileObject>> diagnosticsWithMessage) {
      super(diagnosticsWithMessage);
      this.kind = kind;
    }

    /** Asserts that the note, warning, or error was found in a given file. */
    @CanIgnoreReturnValue
    public DiagnosticOnLine inFile(final JavaFileObject expectedFile) {
      ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsInFile =
          diagnosticsInFile(expectedFile);
      if (diagnosticsInFile.isEmpty()) {
        failureStrategy.fail(
            String.format(
                "Expected %s in %s, but only found them in %s",
                kindToString(kind, false), expectedFile.getName(), sourceFilesWithDiagnostics()));
      }
      return new DiagnosticOnLine(kind, expectedFile, diagnosticsInFile);
    }

    private ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsInFile(
        JavaFileObject expectedFile) {
      String expectedFilePath = expectedFile.toUri().getPath();
      return diagnosticsMatching(
          diagnostic -> {
            JavaFileObject source = diagnostic.getSource();
            return source != null && source.toUri().getPath().equals(expectedFilePath);
          });
    }

    private ImmutableSet<String> sourceFilesWithDiagnostics() {
      return mapDiagnostics(
          diagnostic ->
              diagnostic.getSource() == null
                  ? "(no associated file)"
                  : diagnostic.getSource().getName());
    }
  }

  /** Assertions that a note, warning, or error was found on a given line. */
  public final class DiagnosticOnLine extends DiagnosticsAssertions {

    private final Diagnostic.Kind kind;
    private final JavaFileObject file;

    private DiagnosticOnLine(
        Diagnostic.Kind kind,
        JavaFileObject file,
        ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics) {
      super(diagnostics);
      this.kind = kind;
      this.file = file;
    }

    /** Asserts that the note, warning, or error was found on a given line. */
    @CanIgnoreReturnValue
    public DiagnosticAtColumn onLine(final long expectedLine) {
      ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsOnLine =
          diagnosticsMatching(diagnostic -> diagnostic.getLineNumber() == expectedLine);
      if (diagnosticsOnLine.isEmpty()) {
        failureStrategy.fail(
            String.format(
                "Expected %s on line %d of %s, but only found them on line(s) %s",
                kindToString(kind, false), expectedLine, file.getName(), linesWithDiagnostics()));
      }
      return new DiagnosticAtColumn(kind, file, expectedLine, diagnosticsOnLine);
    }

    private ImmutableSet<String> linesWithDiagnostics() {
      return mapDiagnostics(
          diagnostic ->
              diagnostic.getLineNumber() == Diagnostic.NOPOS
                  ? "(no associated position)"
                  : Long.toString(diagnostic.getLineNumber()));
    }
  }

  /** Assertions that a note, warning, or error was found at a given column. */
  public final class DiagnosticAtColumn extends DiagnosticsAssertions {

    private final Diagnostic.Kind kind;
    private final JavaFileObject file;
    private final long line;

    private DiagnosticAtColumn(
        Diagnostic.Kind kind,
        JavaFileObject file,
        long line,
        ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics) {
      super(diagnostics);
      this.kind = kind;
      this.file = file;
      this.line = line;
    }

    /** Asserts that the note, warning, or error was found at a given column. */
    public void atColumn(final long expectedColumn) {
      if (diagnosticsMatching(diagnostic -> diagnostic.getColumnNumber() == expectedColumn)
          .isEmpty()) {
        failureStrategy.fail(
            String.format(
                "Expected %s at %d:%d of %s, but only found them at column(s) %s",
                kindToString(kind, false),
                line,
                expectedColumn,
                file.getName(),
                columnsWithDiagnostics()));
      }
    }

    private ImmutableSet<String> columnsWithDiagnostics() {
      return mapDiagnostics(
          diagnostic ->
              diagnostic.getColumnNumber() == Diagnostic.NOPOS
                  ? "(no associated position)"
                  : Long.toString(diagnostic.getColumnNumber()));
    }
  }
}
