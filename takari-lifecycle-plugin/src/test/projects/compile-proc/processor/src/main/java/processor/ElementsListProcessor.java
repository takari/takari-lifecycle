package processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

// writes annotated elements path and kind to a file
@SupportedAnnotationTypes("processor.Annotation")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class ElementsListProcessor extends AbstractProcessor {

  final TreeMap<String, Element> elements = new TreeMap<>();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      elements.put(path(element, new StringBuilder()).toString(), element);
    }

    Filer filer = processingEnv.getFiler();
    if (roundEnv.processingOver()) {
      try {
        FileObject res = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "elements.lst");
        try (Writer w = res.openWriter()) {
          for (Map.Entry<String, Element> entry : elements.entrySet()) {
            w.write(entry.getKey() + " " + entry.getValue().getKind() + "\n");
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
      }
    }
    return false;
  }

  private StringBuilder path(Element element, StringBuilder sb) {
    Element enclosing = element.getEnclosingElement();
    if (enclosing != null) {
      path(enclosing, sb);
    }
    sb.append("/").append(element.getSimpleName().toString());
    return sb;
  }
}
