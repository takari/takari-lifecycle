package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.ForwardingFileObject;
import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;

import com.google.common.collect.ImmutableSet;

import io.takari.incrementalbuild.Output;
import io.takari.maven.plugins.compile.CompilerBuildContext;

class FilerImpl implements Filer {

  private final CompilerBuildContext context;
  private final StandardJavaFileManager fileManager;
  private final ProcessingEnvImpl processingEnv;
  private final CompilerJdt incrementalCompiler;

  private final Set<URI> createdResources = new HashSet<>();
  private final Set<File> writtenFiles = new HashSet<>();

  private class FileObjectDelegate {
    public OutputStream openOutputStream(URI uri) throws IOException {
      File outputFile = new File(uri);
      writtenFiles.add(outputFile);
      final Output<File> output = context.processOutput(outputFile);
      return new FilterOutputStream(output.newOutputStream()) {
        @Override
        public void close() throws IOException {
          super.close();
          onClose(output);
        }
      };
    }

    public Writer openWriter(URI uri) throws IOException {
      return new OutputStreamWriter(openOutputStream(uri)); // XXX encoding
    }

    protected void onClose(Output<File> output) {}

  }

  // TODO EclipseFileObject implementation is quite inappropriate for our needs, consider rewrite
  private static class JavaFileObjectImpl extends ForwardingJavaFileObject<JavaFileObject> {
    private final FileObjectDelegate delegate;

    public JavaFileObjectImpl(JavaFileObject fileObject, FileObjectDelegate delegate) {
      super(fileObject);
      this.delegate = delegate;
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
      return delegate.openOutputStream(toUri());
    }

    @Override
    public Writer openWriter() throws IOException {
      return delegate.openWriter(toUri());
    }
  }

  private static class FileObjectImpl extends ForwardingFileObject<FileObject> {

    private final FileObjectDelegate delegate;

    protected FileObjectImpl(FileObject fileObject, FileObjectDelegate delegate) {
      super(fileObject);
      this.delegate = delegate;
    }

    @Override
    public OutputStream openOutputStream() throws IOException {
      return delegate.openOutputStream(toUri());
    }

    @Override
    public Writer openWriter() throws IOException {
      return delegate.openWriter(toUri());
    }

  }

  public FilerImpl(CompilerBuildContext context, StandardJavaFileManager fileManager, CompilerJdt incrementalCompiler, ProcessingEnvImpl processingEnv) {
    this.context = context;
    this.fileManager = fileManager;
    this.incrementalCompiler = incrementalCompiler;
    this.processingEnv = processingEnv;
  }

  @Override
  public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
    JavaFileObject sourceFile = fileManager.getJavaFileForOutput(StandardLocation.SOURCE_OUTPUT, name.toString(), JavaFileObject.Kind.SOURCE, null);
    if (!createdResources.add(sourceFile.toUri())) {
      throw new FilerException("Attempt to recreate file for type " + name);
    }
    return new JavaFileObjectImpl(sourceFile, new FileObjectDelegate() {

      private boolean closed = false;

      @Override
      protected void onClose(Output<File> generatedSource) {
        if (!closed) {
          closed = true;
          // TODO optimize if the regenerated sources didn't change compared the previous build
          CompilationUnit unit = new CompilationUnit(null, generatedSource.getResource().getAbsolutePath(), null /* encoding */);
          processingEnv.addNewUnit(unit);
          incrementalCompiler.addGeneratedSource(generatedSource);
        }
      }
    });
  }

  @Override
  public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
    JavaFileObject classFile = fileManager.getJavaFileForOutput(StandardLocation.CLASS_OUTPUT, name.toString(), JavaFileObject.Kind.CLASS, null);
    if (!createdResources.add(classFile.toUri())) {
      throw new FilerException("Attempt to recreate file for class " + name);
    }
    return new JavaFileObjectImpl(classFile, new FileObjectDelegate() {
      @Override
      protected void onClose(Output<File> generatedClass) {
        // TODO processingEnv.addNewClassFile
        throw new UnsupportedOperationException();
      }
    });
  }

  @Override
  public FileObject createResource(Location location, CharSequence pkg, CharSequence relativeName, Element... originatingElements) throws IOException {
    FileObject file = fileManager.getFileForOutput(location, pkg.toString(), relativeName.toString(), null);
    if (!createdResources.add(file.toUri())) {
      throw new FilerException("Attempt to recreate file for resource " + pkg + "." + relativeName);
    }
    return new FileObjectImpl(file, new FileObjectDelegate());
  }

  @Override
  public FileObject getResource(Location location, CharSequence pkg, CharSequence relativeName) throws IOException {
    FileObject file = fileManager.getFileForInput(location, pkg.toString(), relativeName.toString());
    if (file == null) {
      throw new FileNotFoundException("Resource does not exist " + location + '/' + pkg + '/' + relativeName);
    }
    if (createdResources.contains(file.toUri())) {
      throw new FilerException("Resource already created " + pkg + "." + relativeName);
    }
    return file;
  }

  public void incrementalIterationReset() {
    this.createdResources.clear();
  }

  public Set<File> getWrittenFiles() {
    return ImmutableSet.copyOf(writtenFiles);
  }
}
