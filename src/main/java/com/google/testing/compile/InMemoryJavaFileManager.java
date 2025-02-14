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

import static com.google.common.collect.MoreCollectors.toOptional;
import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.jspecify.annotations.Nullable;

/**
 * A file manager implementation that stores all output in memory.
 *
 * @author Gregory Kick
 */
// TODO(gak): under java 1.7 this could all be done with a PathFileManager
final class InMemoryJavaFileManager extends ForwardingStandardJavaFileManager {
  private final LoadingCache<URI, JavaFileObject> inMemoryOutputs =
      CacheBuilder.newBuilder()
          .build(
              new CacheLoader<URI, JavaFileObject>() {
                @Override
                public JavaFileObject load(URI key) {
                  return new InMemoryJavaFileObject(key);
                }
              });

  private final Map<URI, JavaFileObject> inMemoryInputs = new HashMap<>();

  InMemoryJavaFileManager(StandardJavaFileManager fileManager) {
    super(fileManager);
  }

  private static URI uriForFileObject(Location location, String packageName, String relativeName) {
    StringBuilder uri = new StringBuilder("mem:///").append(location.getName()).append('/');
    if (!packageName.isEmpty()) {
      uri.append(packageName.replace('.', '/')).append('/');
    }
    uri.append(relativeName);
    return URI.create(uri.toString());
  }

  private static URI uriForJavaFileObject(Location location, String className, Kind kind) {
    return URI.create(
        "mem:///" + location.getName() + '/' + className.replace('.', '/') + kind.extension);
  }

  @Override
  public boolean isSameFile(FileObject a, FileObject b) {
    /* This check is less strict than what is typically done by the normal compiler file managers
     * (e.g. JavacFileManager), but is actually the moral equivalent of what most of the
     * implementations do anyway. We use this check rather than just delegating to the compiler's
     * file manager because file objects for tests generally cause IllegalArgumentExceptions. */
    return a.toUri().equals(b.toUri());
  }

  @Override
  public @Nullable FileObject getFileForInput(
      Location location, String packageName, String relativeName) throws IOException {
    if (location.isOutputLocation()) {
      return inMemoryOutputs.getIfPresent(uriForFileObject(location, packageName, relativeName));
    }
    Optional<JavaFileObject> inMemoryInput = findInMemoryInput(packageName, relativeName);
    if (inMemoryInput.isPresent()) {
      return inMemoryInput.get();
    }
    return super.getFileForInput(location, packageName, relativeName);
  }

  @Override
  public @Nullable JavaFileObject getJavaFileForInput(
      Location location, String className, Kind kind) throws IOException {
    if (location.isOutputLocation()) {
      return inMemoryOutputs.getIfPresent(uriForJavaFileObject(location, className, kind));
    }
    Optional<JavaFileObject> inMemoryInput = findInMemoryInput(className);
    if (inMemoryInput.isPresent()) {
      return inMemoryInput.get();
    }
    return super.getJavaFileForInput(location, className, kind);
  }

  private Optional<JavaFileObject> findInMemoryInput(String className) {
    int lastDot = className.lastIndexOf('.');
    return findInMemoryInput(
        lastDot == -1 ? "" : className.substring(0, lastDot - 1),
        className.substring(lastDot + 1) + ".java");
  }

  private Optional<JavaFileObject> findInMemoryInput(String packageName, String relativeName) {
    // Assume each input file's URI ends with the package/relative name. It might have other parts
    // to the left.
    String suffix =
        packageName.isEmpty() ? relativeName : packageName.replace('.', '/') + "/" + relativeName;
    return inMemoryInputs.entrySet().stream()
        .filter(entry -> requireNonNull(entry.getKey().getPath()).endsWith(suffix))
        .map(Map.Entry::getValue)
        .collect(toOptional()); // Might have problems if more than one input file matches.
  }

  @Override
  public FileObject getFileForOutput(Location location, String packageName,
      String relativeName, FileObject sibling) throws IOException {
    URI uri = uriForFileObject(location, packageName, relativeName);
    return inMemoryOutputs.getUnchecked(uri);
  }

  @Override
  public JavaFileObject getJavaFileForOutput(Location location, String className, final Kind kind,
      FileObject sibling) throws IOException {
    URI uri = uriForJavaFileObject(location, className, kind);
    return inMemoryOutputs.getUnchecked(uri);
  }

  ImmutableList<JavaFileObject> getGeneratedSources() {
    ImmutableList.Builder<JavaFileObject> result = ImmutableList.builder();
    for (Map.Entry<URI, JavaFileObject> entry : inMemoryOutputs.asMap().entrySet()) {
      if (requireNonNull(entry.getKey().getPath())
              .startsWith("/" + StandardLocation.SOURCE_OUTPUT.name())
          && (entry.getValue().getKind() == Kind.SOURCE)) {
        result.add(entry.getValue());
      }
    }
    return result.build();
  }

  ImmutableList<JavaFileObject> getOutputFiles() {
    return ImmutableList.copyOf(inMemoryOutputs.asMap().values());
  }

  /** Adds files that should be available in the source path. */
  void addSourceFiles(Iterable<? extends JavaFileObject> files) {
    for (JavaFileObject file : files) {
      inMemoryInputs.put(file.toUri(), file);
    }
  }

  private static final class InMemoryJavaFileObject extends SimpleJavaFileObject
      implements JavaFileObject {
    private long lastModified = 0L;
    private Optional<ByteSource> data = Optional.empty();

    InMemoryJavaFileObject(URI uri) {
      super(uri, JavaFileObjects.deduceKind(uri));
    }

    @Override
    public InputStream openInputStream() throws IOException {
      if (data.isPresent()) {
        return data.get().openStream();
      } else {
        throw new FileNotFoundException();
      }
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
      return new ByteArrayOutputStream() {
        @Override
        public void close() throws IOException {
          super.close();
          data = Optional.of(ByteSource.wrap(toByteArray()));
          lastModified = System.currentTimeMillis();
        }
      };
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
      if (data.isPresent()) {
        return data.get().asCharSource(Charset.defaultCharset()).openStream();
      } else {
        throw new FileNotFoundException();
      }
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors)
        throws IOException {
      if (data.isPresent()) {
        return data.get().asCharSource(Charset.defaultCharset()).read();
      } else {
        throw new FileNotFoundException();
      }
    }

    @Override
    public Writer openWriter() throws IOException {
      return new StringWriter() {
        @Override
        public void close() throws IOException {
          super.close();
          data =
              Optional.of(ByteSource.wrap(toString().getBytes(Charset.defaultCharset())));
          lastModified = System.currentTimeMillis();
        }
      };
    }

    @Override
    public long getLastModified() {
      return lastModified;
    }

    @Override
    public boolean delete() {
      this.data = Optional.empty();
      this.lastModified = 0L;
      return true;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("uri", toUri())
          .add("kind", kind)
          .toString();
    }
  }
}
