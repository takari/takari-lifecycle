package processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

abstract class AbstractElementsListProcessor extends AbstractProcessor {
  protected final TreeMap<String, Element> elements = new TreeMap<>();

  protected void writeElements(RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      Filer filer = processingEnv.getFiler();
      try (Writer w = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "elements.lst").openWriter()) {
        for (Map.Entry<String, Element> entry : elements.entrySet()) {
          w.write(entry.getKey() + " " + entry.getValue().getKind() + "\n");
        }
      } catch (IOException e) {
        e.printStackTrace();
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
      }
    }
  }
}
