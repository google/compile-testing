package com.google.testing.compile;

import java.io.IOException;
import javax.tools.JavaFileObject;
import org.junit.Test;

import static com.google.common.collect.ImmutableList.of;
import static com.google.testing.compile.Compilation.Status.SUCCESS;
import static com.google.testing.compile.JavaFileObjects.forSourceString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class IncrementalCompileTesterTest {

  @Test
  public void processor_creates_GeneratedFiles_incrementally() throws IOException {
    //GIVEN
    JavaFileObject source0 = forSourceString("test.Test0", "" //
        + "package test;\n" //
        + "public class Test0 {\n" //
        + "}");
    JavaFileObject source1 = forSourceString("test.Test1", "" //
        + "package test;\n" //
        + "public class Test1 extends Test0 {\n" //
        + "}");

    //WHEN

    //full build
    IncrementalCompileTester compileTester = new IncrementalCompileTester();
    Compilation compilation = compileTester.fullBuild(of(source0, source1), of(), of());

    //assert it works
    assertThat(compilation.status(), is(SUCCESS));

    //incremental rebuild of source1. Should fail if not incremental
    //as source1 uses source0
    compilation = compileTester.incrementalBuild(of(source1), of(), of());
    assertThat(compilation.status(), is(SUCCESS));
  }
}
