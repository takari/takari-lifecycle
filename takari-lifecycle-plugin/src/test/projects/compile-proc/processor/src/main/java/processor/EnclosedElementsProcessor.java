package processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.tools.Diagnostic;
import javax.tools.FileObject;

@SupportedAnnotationTypes("processor.Annotation")
public class EnclosedElementsProcessor extends ProcessorImpl {

  public EnclosedElementsProcessor() {
    super("Generated");
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      TypeElement cls = (TypeElement) element;

      // Explore enclosed field types of this class.
      for (Element enclosedElement : cls.getEnclosedElements()) {
          if (enclosedElement.asType() instanceof DeclaredType) {
            ((DeclaredType)enclosedElement.asType()).asElement().getEnclosedElements();
          }
      }
    }

    return super.process(annotations, roundEnv);
  }
}
