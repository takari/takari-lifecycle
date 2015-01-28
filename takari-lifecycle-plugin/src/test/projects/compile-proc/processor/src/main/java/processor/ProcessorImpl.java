package processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

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
        String clsSimpleName = prefix + cls.getSimpleName();
        String pkgName = pkg.getQualifiedName().toString();
        String clsQualifiedName = pkgName + "." + clsSimpleName;
        JavaFileObject sourceFile =
            processingEnv.getFiler().createSourceFile(clsQualifiedName, element);
        BufferedWriter w = new BufferedWriter(sourceFile.openWriter());
        try {
          w.append("package ").append(pkgName).append(";");
          w.newLine();
          w.append("public class ").append(clsSimpleName);
          appendBody(pkgName, clsSimpleName, w);
        } finally {
          w.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return false; // not "claimed" so multiple processors can be tested
  }

  protected void appendBody(String pkgName, String clsName, BufferedWriter w) throws IOException {
    w.append(" { }");
  }
}
