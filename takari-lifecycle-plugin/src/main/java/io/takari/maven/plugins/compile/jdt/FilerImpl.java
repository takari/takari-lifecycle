package io.takari.maven.plugins.compile.jdt;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

import org.eclipse.jdt.internal.compiler.apt.model.ElementImpl;
import org.eclipse.jdt.internal.compiler.batch.CompilationUnit;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.SourceTypeBinding;

import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import io.takari.maven.plugins.compile.CompilerBuildContext;

class FilerImpl implements Filer {

  private final CompilerBuildContext context;
  private final StandardJavaFileManager fileManager;
  private final ProcessingEnvImpl processingEnv;
  private final CompilerJdt incrementalCompiler;

  private final Set<URI> createdResources = new HashSet<>();

  private class FileObjectDelegate {
    private final Collection<Resource<File>> inputs;

    public FileObjectDelegate(Collection<Resource<File>> inputs) {
      this.inputs = inputs;
    }

    public OutputStream openOutputStream(URI uri) throws IOException {
      final Output<File> output = context.processOutput(new File(uri));
      for (Resource<File> input : inputs) {
        input.associateOutput(output);
      }
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
    return new JavaFileObjectImpl(sourceFile, new FileObjectDelegate(getInputs(originatingElements)) {
      @Override
      protected void onClose(Output<File> generatedSource) {
        // TODO optimize if the regenerated sources didn't change compared the previous build
        CompilationUnit unit = new CompilationUnit(null, generatedSource.getResource().getAbsolutePath(), null /* encoding */);
        processingEnv.addNewUnit(unit);
        incrementalCompiler.addGeneratedSource(generatedSource);
      }
    });
  }

  private Collection<Resource<File>> getInputs(Element[] elements) {
    Map<File, Resource<File>> inputs = new HashMap<>();
    for (Element element : elements) {
      if (!(element instanceof ElementImpl)) {
        throw new IllegalArgumentException();
      }
      Binding binding = ((ElementImpl) element)._binding;
      if (binding instanceof SourceTypeBinding) {
        File file = new File(new String(((SourceTypeBinding) binding).getFileName()));
        inputs.put(file, context.getProcessedSource(file));
      } else {
        throw new UnsupportedOperationException("not implemented");
      }
    }
    return inputs.values();
  }

  @Override
  public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
    JavaFileObject classFile = fileManager.getJavaFileForOutput(StandardLocation.CLASS_OUTPUT, name.toString(), JavaFileObject.Kind.CLASS, null);
    if (!createdResources.add(classFile.toUri())) {
      throw new FilerException("Attempt to recreate file for class " + name);
    }
    return new JavaFileObjectImpl(classFile, new FileObjectDelegate(getInputs(originatingElements)) {
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
    return new FileObjectImpl(file, new FileObjectDelegate(getInputs(originatingElements)));
  }

  @Override
  public FileObject getResource(Location location, CharSequence pkg, CharSequence relativeName) throws IOException {
    FileObject file = fileManager.getFileForInput(location, pkg.toString(), relativeName.toString());
    return file;
  }

  public void hardReset() {
    this.createdResources.clear();
  }
}
