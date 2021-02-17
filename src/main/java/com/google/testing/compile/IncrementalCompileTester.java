package com.google.testing.compile;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.JavaFileObject.Kind.SOURCE;
import static javax.tools.StandardLocation.CLASS_OUTPUT;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;

//many ideas come from http://atamur.blogspot.de/2009/10/using-built-in-javacompiler-with-custom.html
public class IncrementalCompileTester {

  InMemoryJavaFileManager fileManager;
  JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
  private DiagnosticCollector<JavaFileObject> diagnosticCollector;
  private Set<JavaFileObject> fullBuildSources;

  public IncrementalCompileTester() {
    reset();
  }

  public void reset() {
    this.fileManager = initFileManager();
  }

  public final Compilation compile(Iterable<? extends JavaFileObject> files, Iterable<? extends Processor> processors,
      ImmutableList<String> options) {
    JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnosticCollector, options, null, files);
    task.setProcessors(processors);
    boolean succeeded = task.call();
    Compiler internalCompiler = Compiler.compiler(compiler).withProcessors(processors);
    return new Compilation(internalCompiler, files, succeeded, diagnosticCollector.getDiagnostics(), getGeneratedFiles());
  }

  private ImmutableList<JavaFileObject> getGeneratedFiles() {
    return fileManager.getGeneratedSources();
  }

  public final Compilation fullBuild(Iterable<? extends JavaFileObject> files, Iterable<? extends Processor> processors,
      ImmutableList<String> options) {
    this.fullBuildSources = new HashSet<>();
    for (JavaFileObject file : files) {
      fullBuildSources.add(file);
    }

    Compilation compilation = compile(files, processors, options);
    return compilation;
  }

  public final Compilation incrementalBuild(Iterable<? extends JavaFileObject> files, Iterable<? extends Processor> processors,
      ImmutableList<String> options) {
    return compile(files, processors, options);
  }

  public InMemoryJavaFileManager.InMemoryJavaFileObject getGeneratedJavaFile(String FQNclassName) {
    try {
      int lastDotIndex = FQNclassName.lastIndexOf('.');
      String packageName = lastDotIndex == -1 ? "" : FQNclassName.substring(0, lastDotIndex);
      String className = FQNclassName.substring(lastDotIndex + 1);
      return (InMemoryJavaFileManager.InMemoryJavaFileObject) fileManager.getFileForOutput(SOURCE_OUTPUT, packageName, className + ".java", null);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  public InMemoryJavaFileManager.InMemoryJavaFileObject getClassFile(String FQNclassName) {
    try {
      int lastDotIndex = FQNclassName.lastIndexOf('.');
      String packageName = lastDotIndex == -1 ? "" : FQNclassName.substring(0, lastDotIndex);
      String className = FQNclassName.substring(lastDotIndex + 1);
      return (InMemoryJavaFileManager.InMemoryJavaFileObject) fileManager.getFileForOutput(CLASS_OUTPUT, packageName, className + ".class", null);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private InMemoryJavaFileManager initFileManager() {
    diagnosticCollector = new DiagnosticCollector<>();
    return new InMemoryJavaFileManager(compiler.getStandardFileManager(diagnosticCollector, Locale.getDefault(), UTF_8));
  }

  public boolean deleteGeneratedFiles(JavaFileObject... generatedJavaFiles) {
    boolean allDeleted = true;
    for (JavaFileObject generatedJavaFile : generatedJavaFiles) {
      String name = generatedJavaFile.getName().substring(0, generatedJavaFile.getName().lastIndexOf('.'));
      allDeleted &= fileManager.deleteJavaFileObject(SOURCE_OUTPUT, name, SOURCE);
      allDeleted &= fileManager.deleteJavaFileObject(CLASS_OUTPUT, name, CLASS);
    }
    return allDeleted;
  }
}
