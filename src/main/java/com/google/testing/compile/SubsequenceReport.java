package com.google.testing.compile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** A data structure describing the result of a failed subsequence check. */
class SubsequenceReport {

  private final String unmatchedToken; // first unmatched token in subsequence
  private final int index; // index in subsequence of first unmatched token
  private final int lastMatchIndex; // index in actual of last match
  private final Map<Integer, Integer> matches; // subsequence index -> actual index
  private final Map<Integer, Integer> inverseMatches; // actual index -> subsequence index

  private final List<String> subsequence;
  private final int subsequenceSize;

  private final List<String> actual;
  private final int actualSize;

  private SubsequenceReport(
      List<String> actual,
      List<String> subsequence,
      int index,
      int lastMatchIndex,
      Map<Integer, Integer> matches,
      Map<Integer, Integer> inverseMatches,
      String unmatchedToken,
      int subsequenceSize,
      int actualSize) {
    this.index = index;
    this.lastMatchIndex = lastMatchIndex;
    this.matches = matches;
    this.inverseMatches = inverseMatches;
    this.subsequence = Objects.requireNonNull(subsequence);
    this.actual = Objects.requireNonNull(actual);
    this.unmatchedToken = Objects.requireNonNull(unmatchedToken);
    this.subsequenceSize = subsequenceSize;
    this.actualSize = actualSize;
  }

  static SubsequenceReport create(
      List<String> actual,
      List<String> subsequence,
      int index,
      Map<Integer, Integer> matches,
      Map<Integer, Integer> inverseMatches,
      int lastMatchIndex) {
    int actualSize = actual.size();
    int subsequenceSize = subsequence.size();
    String unmatchedToken = subsequence.get(index);
    return new SubsequenceReport(
        actual,
        subsequence,
        index,
        lastMatchIndex,
        matches,
        inverseMatches,
        unmatchedToken,
        subsequenceSize,
        actualSize);
  }

  String getUnmatched() {
    return index + ": " + toStringLiteral(unmatchedToken);
  }

  String getActual() {
    return String.join("\n", getActualLines());
  }

  String getSubsequence() {
    return String.join("\n", getSubsequenceLines());
  }

  private List<String> getActualLines() {
    return IntStream.range(0, actualSize)
        .mapToObj(
            i -> {
              boolean isLastMatch = i == lastMatchIndex;
              String suffix = i == actualSize - 1 ? "" : ",";
              Integer subsequenceIndex = inverseMatches.get(i);
              if (subsequenceIndex != null) {
                suffix += " // " + subsequenceIndex;
              }
              if (isLastMatch) {
                suffix += ", last match";
              }
              return toStringLiteral(actual.get(i)) + suffix;
            })
        .collect(Collectors.toList());
  }

  private List<String> getSubsequenceLines() {
    return IntStream.range(0, subsequenceSize)
        .mapToObj(
            i -> {
              boolean isUnmatchedToken = i == index;
              String suffix = i == subsequenceSize - 1 ? "" : ",";
              Integer actualIndex = matches.get(i);
              if (actualIndex != null) {
                suffix += " // " + actualIndex;
              }
              if (isUnmatchedToken) {
                suffix += " // no match";
              }
              return toStringLiteral(subsequence.get(i)) + suffix;
            })
        .collect(Collectors.toList());
  }

  private String toStringLiteral(String token) {
    return "\"" + token.replace("\"", "\\\"") + "\"";
  }
}
