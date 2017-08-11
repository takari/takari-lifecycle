package processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;

/**
 * like processor.Processor, but generates durring the last round only.
 */
@SupportedAnnotationTypes("processor.Annotation")
public class ProcessorImplLastRound extends AbstractProcessor {

  private final List<TypeElement> classes = new ArrayList<>();

  private final String prefix;
  
  public ProcessorImplLastRound() {
    this.prefix = "Generated";
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
        classes.add((TypeElement) element);
    }
    if (roundEnv.processingOver()) {
      for (TypeElement cls : classes ) {
        try {
          PackageElement pkg = (PackageElement) cls.getEnclosingElement();
          String clsSimpleName = getGeneratedClassName(cls, prefix);
          String pkgName = pkg.getQualifiedName().toString();
          FileObject sourceFile = createFile(pkgName, clsSimpleName, cls);
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
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), cls);
        }
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
