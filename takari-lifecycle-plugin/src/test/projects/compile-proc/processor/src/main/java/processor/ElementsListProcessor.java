package processor;

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

// writes annotated elements path and kind to a file
@SupportedAnnotationTypes("processor.Annotation")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class ElementsListProcessor extends AbstractElementsListProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      elements.put(path(element, new StringBuilder()).toString(), element);
    }

    writeElements(roundEnv);

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
