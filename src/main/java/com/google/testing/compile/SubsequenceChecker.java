package com.google.testing.compile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** A class for determining whether one list of strings is a subsequence of the other. */
class SubsequenceChecker {

  /**
   * If the {@code subsequence} is not a subsequence of the {@code actual}, returns a {@code
   * SubsequenceReport} describing the sequences and where exactly the subsequence test has failed.
   *
   * <p>Otherwise returns an empty {@code Optional}.
   */
  static Optional<SubsequenceReport> checkSubsequence(
      List<String> actual, List<String> subsequence) {
    final int subsequenceSize = subsequence.size();
    final int actualSize = actual.size();

    Map<Integer, Integer> inverseMatches = new HashMap<>(subsequenceSize);
    Map<Integer, Integer> matches = new HashMap<>(subsequenceSize);
    int actualIndex = 0;
    outer:
    for (int index = 0; index < subsequenceSize; index++) {
      String subsequenceToken = subsequence.get(index);
      for (int i = actualIndex; i < actualSize; i++) {
        if (Objects.equals(subsequenceToken, actual.get(i))) {
          matches.put(index, i);
          inverseMatches.put(i, index);
          actualIndex = i + 1;
          continue outer;
        }
      }
      int lastMatchIndex = actualIndex - 1;
      return Optional.of(
          SubsequenceReport.create(
              actual, subsequence, index, matches, inverseMatches, lastMatchIndex));
    }
    return Optional.empty();
  }
}
