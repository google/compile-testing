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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Predicates.notNull;
import static com.google.common.collect.Iterables.size;
import static com.google.common.collect.Streams.mapWithIndex;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.Compilation.Status.FAILURE;
import static com.google.testing.compile.Compilation.Status.SUCCESS;
import static com.google.testing.compile.JavaFileObjectSubject.javaFileObjects;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.MANDATORY_WARNING;
import static javax.tools.Diagnostic.Kind.NOTE;
import static javax.tools.Diagnostic.Kind.WARNING;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.common.truth.Truth;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
      failureStrategy.fail(
          actual().describeFailureDiagnostics() + actual().describeGeneratedSourceFiles());
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
              kindPlural(kind),
              actualCount,
              kindPlural(kind)));
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

  /** Returns the phrase describing one diagnostic of a kind. */
  private static String kindSingular(Diagnostic.Kind kind) {
    switch (kind) {
      case ERROR:
        return "an error";

      case MANDATORY_WARNING:
      case WARNING:
        return "a warning";

      case NOTE:
        return "a note";

      case OTHER:
        return "a diagnostic message";

      default:
        throw new AssertionError(kind);
    }
  }

  /** Returns the phrase describing several diagnostics of a kind. */
  private static String kindPlural(Diagnostic.Kind kind) {
    switch (kind) {
      case ERROR:
        return "errors";

      case MANDATORY_WARNING:
      case WARNING:
        return "warnings";

      case NOTE:
        return "notes";

      case OTHER:
        return "diagnostic messages";

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
      String diagnosticMatchDescription,
      Pattern expectedPattern,
      Diagnostic.Kind kind,
      Diagnostic.Kind... more) {
    String expectedDiagnostic =
        String.format("%s %s", kindSingular(kind), diagnosticMatchDescription);
    return new DiagnosticInFile(
        expectedDiagnostic,
        findMatchingDiagnostics(expectedDiagnostic, expectedPattern, kind, more));
  }

  /**
   * Returns the diagnostics that match one of the kinds and a pattern. If none match, fails the
   * test.
   */
  private ImmutableList<Diagnostic<? extends JavaFileObject>> findMatchingDiagnostics(
      String expectedDiagnostic,
      Pattern expectedPattern,
      Diagnostic.Kind kind,
      Diagnostic.Kind... more) {
    ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsOfKind =
        actual().diagnosticsOfKind(kind, more);
    ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsWithMessage =
        diagnosticsOfKind
            .stream()
            .filter(diagnostic -> expectedPattern.matcher(diagnostic.getMessage(null)).find())
            .collect(toImmutableList());
    if (diagnosticsWithMessage.isEmpty()) {
      failureStrategy.fail(
          messageListing(diagnosticsOfKind, "Expected %s, but only found:", expectedDiagnostic));
    }
    return diagnosticsWithMessage;
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

  private static final JavaFileObject ALREADY_FAILED =
      JavaFileObjects.forSourceLines(
          "compile.Failure", "package compile;", "", "final class Failure {}");

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
      return ignoreCheck().about(javaFileObjects()).that(ALREADY_FAILED);
    }
    return check().about(javaFileObjects()).that(generatedFile.get());
  }

  private static <T> Collector<T, ?, ImmutableList<T>> toImmutableList() {
    return collectingAndThen(toList(), ImmutableList::copyOf);
  }

  private static <T> Collector<T, ?, ImmutableSet<T>> toImmutableSet() {
    return collectingAndThen(toList(), ImmutableSet::copyOf);
  }

  private class DiagnosticAssertions {
    private final String expectedDiagnostic;
    private final ImmutableList<Diagnostic<? extends JavaFileObject>> diagnostics;

    DiagnosticAssertions(
        String expectedDiagnostic,
        Iterable<Diagnostic<? extends JavaFileObject>> matchingDiagnostics) {
      this.expectedDiagnostic = expectedDiagnostic;
      this.diagnostics = ImmutableList.copyOf(matchingDiagnostics);
    }

    DiagnosticAssertions(
        DiagnosticAssertions previous,
        Iterable<Diagnostic<? extends JavaFileObject>> matchingDiagnostics) {
      this(previous.expectedDiagnostic, matchingDiagnostics);
    }

    ImmutableList<Diagnostic<? extends JavaFileObject>> filterDiagnostics(
        Predicate<? super Diagnostic<? extends JavaFileObject>> predicate) {
      return diagnostics.stream().filter(predicate).collect(toImmutableList());
    }

    <T> Stream<T> mapDiagnostics(Function<? super Diagnostic<? extends JavaFileObject>, T> mapper) {
      return diagnostics.stream().map(mapper);
    }

    protected void failExpectingMatchingDiagnostic(String format, Object... args) {
      failureStrategy.fail(
          new StringBuilder("Expected ")
              .append(expectedDiagnostic)
              .append(String.format(format, args))
              .toString());
    }
  }

  /** Assertions that a note, warning, or error was found in a given file. */
  public final class DiagnosticInFile extends DiagnosticAssertions {

    private DiagnosticInFile(
        String expectedDiagnostic,
        Iterable<Diagnostic<? extends JavaFileObject>> diagnosticsWithMessage) {
      super(expectedDiagnostic, diagnosticsWithMessage);
    }

    /** Asserts that the note, warning, or error was found in a given file. */
    @CanIgnoreReturnValue
    public DiagnosticOnLine inFile(JavaFileObject expectedFile) {
      return new DiagnosticOnLine(this, expectedFile, findDiagnosticsInFile(expectedFile));
    }

    /** Returns the diagnostics that are in the given file. Fails the test if none are found. */
    private ImmutableList<Diagnostic<? extends JavaFileObject>> findDiagnosticsInFile(
        JavaFileObject expectedFile) {
      String expectedFilePath = expectedFile.toUri().getPath();
      ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsInFile =
          filterDiagnostics(
              diagnostic -> {
                JavaFileObject source = diagnostic.getSource();
                return source != null && source.toUri().getPath().equals(expectedFilePath);
              });
      if (diagnosticsInFile.isEmpty()) {
        failExpectingMatchingDiagnostic(
            " in %s, but found it in %s", expectedFile.getName(), sourceFilesWithDiagnostics());
      }
      return diagnosticsInFile;
    }

    private ImmutableSet<String> sourceFilesWithDiagnostics() {
      return mapDiagnostics(
              diagnostic ->
                  diagnostic.getSource() == null
                      ? "(no associated file)"
                      : diagnostic.getSource().getName())
          .collect(toImmutableSet());
    }
  }

  /** An object that can list the lines in a file. */
  static final class LinesInFile {
    private final JavaFileObject file;
    private ImmutableList<String> lines;

    LinesInFile(JavaFileObject file) {
      this.file = file;
    }

    String fileName() {
      return file.getName();
    }

    /** Returns the lines in the file. */
    ImmutableList<String> linesInFile() {
      if (lines == null) {
        try {
          lines = JavaFileObjects.asByteSource(file).asCharSource(UTF_8).readLines();
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
      return lines;
    }

    /**
     * Returns a {@link Collector} that lists the file lines numbered by the input stream (1-based).
     */
    Collector<Long, ?, String> toLineList() {
      return Collectors.mapping(this::listLine, joining("\n"));
    }

    /** Lists the line at a line number (1-based). */
    String listLine(long lineNumber) {
      return lineNumber == Diagnostic.NOPOS
          ? "(no associated line)"
          : String.format("%4d: %s", lineNumber, linesInFile().get((int) (lineNumber - 1)));
    }
  }

  /** Assertions that a note, warning, or error was found on a given line. */
  public final class DiagnosticOnLine extends DiagnosticAssertions {

    private final LinesInFile linesInFile;

    private DiagnosticOnLine(
        DiagnosticAssertions previous,
        JavaFileObject file,
        ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsInFile) {
      super(previous, diagnosticsInFile);
      this.linesInFile = new LinesInFile(file);
    }

    /** Asserts that the note, warning, or error was found on a given line. */
    @CanIgnoreReturnValue
    public DiagnosticAtColumn onLine(long expectedLine) {
      return new DiagnosticAtColumn(
          this, linesInFile, expectedLine, findMatchingDiagnosticsOnLine(expectedLine));
    }

    /**
     * Asserts that the note, warning, or error was found on the single line that contains a
     * substring.
     */
    public void onLineContaining(String expectedLineSubstring) {
      findMatchingDiagnosticsOnLine(findLineContainingSubstring(expectedLineSubstring));
    }

    /**
     * Returns the single line number that contains an expected substring.
     *
     * @throws IllegalArgumentException unless exactly one line in the file contains {@code
     *     expectedLineSubstring}
     */
    private long findLineContainingSubstring(String expectedLineSubstring) {
      ImmutableSet<Long> matchingLines =
          mapWithIndex(
                  linesInFile.linesInFile().stream(),
                  (line, index) -> line.contains(expectedLineSubstring) ? index : null)
              .filter(notNull())
              .map(index -> index + 1) // to 1-based line numbers
              .collect(toImmutableSet());
      checkArgument(
          !matchingLines.isEmpty(),
          "No line in %s contained \"%s\"",
          linesInFile.fileName(),
          expectedLineSubstring);
      checkArgument(
          matchingLines.size() == 1,
          "More than one line in %s contained \"%s\":\n%s",
          linesInFile.fileName(),
          expectedLineSubstring,
          matchingLines.stream().collect(linesInFile.toLineList()));
      return Iterables.getOnlyElement(matchingLines);
    }

    /**
     * Returns the matching diagnostics found on a specific line of the file. Fails the test if none
     * are found.
     *
     * @param expectedLine the expected line number
     */
    @CanIgnoreReturnValue
    private ImmutableList<Diagnostic<? extends JavaFileObject>> findMatchingDiagnosticsOnLine(
        long expectedLine) {
      ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsOnLine =
          filterDiagnostics(diagnostic -> diagnostic.getLineNumber() == expectedLine);
      if (diagnosticsOnLine.isEmpty()) {
        failExpectingMatchingDiagnostic(
            " in %s on line:\n%s\nbut found it on line(s):\n%s",
            linesInFile.fileName(),
            linesInFile.listLine(expectedLine),
            mapDiagnostics(Diagnostic::getLineNumber).collect(linesInFile.toLineList()));
      }
      return diagnosticsOnLine;
    }
  }

  /** Assertions that a note, warning, or error was found at a given column. */
  public final class DiagnosticAtColumn extends DiagnosticAssertions {

    private final LinesInFile linesInFile;
    private final long line;

    private DiagnosticAtColumn(
        DiagnosticAssertions previous,
        LinesInFile linesInFile,
        long line,
        ImmutableList<Diagnostic<? extends JavaFileObject>> diagnosticsOnLine) {
      super(previous, diagnosticsOnLine);
      this.linesInFile = linesInFile;
      this.line = line;
    }

    /** Asserts that the note, warning, or error was found at a given column. */
    public void atColumn(final long expectedColumn) {
      if (filterDiagnostics(diagnostic -> diagnostic.getColumnNumber() == expectedColumn)
          .isEmpty()) {
        failExpectingMatchingDiagnostic(
            " in %s at column %d of line %d, but found it at column(s) %s:\n%s",
            linesInFile.fileName(),
            expectedColumn,
            line,
            columnsWithDiagnostics(),
            linesInFile.listLine(line));
      }
    }

    private ImmutableSet<String> columnsWithDiagnostics() {
      return mapDiagnostics(
              diagnostic ->
                  diagnostic.getColumnNumber() == Diagnostic.NOPOS
                      ? "(no associated position)"
                      : Long.toString(diagnostic.getColumnNumber()))
          .collect(toImmutableSet());
    }
  }
}
