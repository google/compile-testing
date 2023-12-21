package com.google.testing.compile;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ParserTest {

  private static final JavaFileObject HELLO_WORLD_BROKEN =
      JavaFileObjects.forSourceLines(
          "test.HelloWorld", "package test;", "", "public class HelloWorld {", "}}");

  @Test
  public void failsToParse() {
    IllegalStateException expected =
        assertThrows(
            IllegalStateException.class,
            () -> Parser.parse(ImmutableList.of(HELLO_WORLD_BROKEN), "hello world"));
    assertThat(expected).hasMessageThat().contains("HelloWorld.java:4: error");
  }
}
