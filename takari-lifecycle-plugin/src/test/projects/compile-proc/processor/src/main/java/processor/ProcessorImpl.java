package processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;

abstract class ProcessorImpl extends AbstractProcessor {

  private final String prefix;

  protected ProcessorImpl(String prefix) {
    this.prefix = prefix;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      try {
        TypeElement cls = (TypeElement) element;
        PackageElement pkg = (PackageElement) cls.getEnclosingElement();
        String clsSimpleName = getGeneratedClassName(cls, prefix);
        String pkgName = pkg.getQualifiedName().toString();
        FileObject sourceFile = createFile(pkgName, clsSimpleName, element);
        BufferedWriter w = new BufferedWriter(sourceFile.openWriter());
        try {
          w.append("package ").append(pkgName).append(";");
          w.newLine();
          appendClassAnnotations(w);
          w.append("public class ").append(clsSimpleName);
          appendBody(pkgName, clsSimpleName, w);
        } finally {
          w.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), element);
      }
    }
    return false; // not "claimed" so multiple processors can be tested
  }

  protected void appendClassAnnotations(BufferedWriter w) throws IOException {}

  protected String getGeneratedClassName(TypeElement cls, String prefix) {
    return prefix + cls.getSimpleName();
  }

  protected FileObject createFile(String pkgName, String clsSimpleName, Element element)
      throws IOException {
    String clsQualifiedName = pkgName + "." + clsSimpleName;
    return processingEnv.getFiler().createSourceFile(clsQualifiedName, element);
  }

  protected void appendBody(String pkgName, String clsName, BufferedWriter w) throws IOException {
    w.append(" { }");
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_7;
  }
}
