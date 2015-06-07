package processor;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

@SupportedAnnotationTypes("processor.Annotation")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ErrorMessageProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Messager messager = processingEnv.getMessager();
    messager.printMessage(Kind.ERROR, "test #printMessage(Kind, String)");
    messager.printMessage(Kind.ERROR, "test #printMessage(Kind, String, Element)", null);
    messager.printMessage(Kind.ERROR, "test #printMessage(Kind, String, Element, AnnotationMirror)", null, null);
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      messager.printMessage(Kind.ERROR, "test error message", element);
    }
    return false; // not "claimed" so multiple processors can be tested
  }

}
