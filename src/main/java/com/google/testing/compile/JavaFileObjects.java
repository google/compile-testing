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
import static com.google.common.base.Preconditions.checkNotNull;
import static javax.tools.JavaFileObject.Kind.SOURCE;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;

import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

/**
 * A utility class for creating {@link JavaFileObject} instances.
 *
 * @author Gregory Kick
 */
public final class JavaFileObjects {
  private JavaFileObjects() { }

  /**
   * Creates a {@link JavaFileObject} with a path corresponding to the {@code fullyQualifiedName}
   * containing the give {@code source}. The returned object will always be read-only and have the
   * {@link Kind#SOURCE} {@linkplain JavaFileObject#getKind() kind}.
   *
   * <p>Note that this method makes no attempt to verify that the name matches the contents of the
   * source and compilation errors may result if they do not match.
   */
  public static JavaFileObject forSourceString(String fullyQualifiedName, String source) {
    return new StringSourceJavaFileObject(checkNotNull(fullyQualifiedName), checkNotNull(source));
  }

  private static final Joiner LINE_JOINER = Joiner.on('\n');

  /**
   * Behaves exactly like {@link #forSourceString}, but joins lines so that multi-line source
   * strings may omit the newline characters.  For example: <pre>   {@code
   *
   *   JavaFileObjects.forSourceLines("example.HelloWorld",
   *       "package example;",
   *       "",
   *       "final class HelloWorld {",
   *       "  void sayHello() {",
   *       "    System.out.println(\"hello!\");",
   *       "  }",
   *       "}");
   *   }</pre>
   */
  public static JavaFileObject forSourceLines(String fullyQualifiedName, String... lines) {
    return forSourceLines(fullyQualifiedName, Arrays.asList(lines));
  }

  /** An overload of {@code #forSourceLines} that takes an {@code Iterable<String>}. */
  public static JavaFileObject forSourceLines(String fullyQualifiedName, Iterable<String> lines) {
    return forSourceString(fullyQualifiedName, LINE_JOINER.join(lines));
  }

  private static final class StringSourceJavaFileObject extends SimpleJavaFileObject {
    final String source;
    final long lastModified;

    StringSourceJavaFileObject(String fullyQualifiedName, String source) {
      super(createUri(fullyQualifiedName), SOURCE);
      // TODO(gak): check that fullyQualifiedName looks like a fully qualified class name
      this.source = source;
      this.lastModified = System.currentTimeMillis();
    }

    static URI createUri(String fullyQualifiedClassName) {
      return URI.create(CharMatcher.is('.').replaceFrom(fullyQualifiedClassName, '/')
          + SOURCE.extension);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }

    @Override
    public OutputStream openOutputStream() {
      throw new IllegalStateException();
    }

    @Override
    public InputStream openInputStream() {
      return new ByteArrayInputStream(source.getBytes(Charset.defaultCharset()));
    }

    @Override
    public Writer openWriter() {
      throw new IllegalStateException();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) {
      return new StringReader(source);
    }

    @Override
    public long getLastModified() {
      return lastModified;
    }
  }

  /**
   * Returns a {@link JavaFileObject} for the resource at the given {@link URL}. The returned object
   * will always be read-only and the {@linkplain JavaFileObject#getKind() kind} is inferred via
   * the {@link Kind#extension}.
   */
  public static JavaFileObject forResource(URL resourceUrl) {
    if ("jar".equals(resourceUrl.getProtocol())) {
      return new JarFileJavaFileObject(resourceUrl);
    } else {
      return new ResourceSourceJavaFileObject(resourceUrl);
    }
  }

  /**
   * Returns a {@link JavaFileObject} for the class path resource with the given
   * {@code resourceName}. This method is equivalent to invoking
   * {@code forResource(Resources.getResource(resourceName))}.
   */
  public static JavaFileObject forResource(String resourceName) {
    return forResource(Resources.getResource(resourceName));
  }

  static Kind deduceKind(URI uri) {
    String path = uri.getPath();
    for (Kind kind : Kind.values()) {
      if (path.endsWith(kind.extension)) {
        return kind;
      }
    }
    return Kind.OTHER;
  }

  static ByteSource asByteSource(final JavaFileObject javaFileObject) {
    return new ByteSource() {
      @Override public InputStream openStream() throws IOException {
        return javaFileObject.openInputStream();
      }
    };
  }

  private static final class JarFileJavaFileObject
      extends ForwardingJavaFileObject<ResourceSourceJavaFileObject> {
    final String name;

    JarFileJavaFileObject(URL jarUrl) {
      // this is a cheap way to give SimpleJavaFileObject a uri that satisfies the contract
      // then we just override the methods that we want to behave differently for jars
      super(new ResourceSourceJavaFileObject(jarUrl, getPathUri(jarUrl)));
      this.name = jarUrl.toString();
    }

    static final Splitter JAR_URL_SPLITTER = Splitter.on('!');

    static final URI getPathUri(URL jarUrl) {
      ImmutableList<String> parts = ImmutableList.copyOf(JAR_URL_SPLITTER.split(jarUrl.getPath()));
      checkArgument(parts.size() == 2,
          "The jar url separator (!) appeared more than once in the url: %s", jarUrl);
      String pathPart = parts.get(1);
      checkArgument(!pathPart.endsWith("/"), "cannot create a java file object for a directory: %s",
          pathPart);
      return URI.create(pathPart);
    }

    @Override
    public String getName() {
      return name;
    }
  }

  private static final class ResourceSourceJavaFileObject extends SimpleJavaFileObject {
    final ByteSource resourceByteSource;

    /** Only to avoid creating the URI twice. */
    ResourceSourceJavaFileObject(URL resourceUrl, URI resourceUri) {
      super(resourceUri, deduceKind(resourceUri));
      this.resourceByteSource = Resources.asByteSource(resourceUrl);
    }

    ResourceSourceJavaFileObject(URL resourceUrl) {
      this(resourceUrl, URI.create(resourceUrl.toString()));
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors)
        throws IOException {
      return resourceByteSource.asCharSource(Charset.defaultCharset()).read();
    }

    @Override
    public InputStream openInputStream() throws IOException {
      return resourceByteSource.openStream();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
      return resourceByteSource.asCharSource(Charset.defaultCharset()).openStream();
    }
  }
}
