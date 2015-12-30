package processor;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.StandardLocation;

@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class Processor_dummyOutput extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    final Filer filer = processingEnv.getFiler();
    final Messager messager = processingEnv.getMessager();

    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      try {
        filer.createResource(StandardLocation.SOURCE_OUTPUT, "",
            "dummy" + System.currentTimeMillis(), element);
      } catch (IOException e) {
        messager.printMessage(Kind.ERROR, e.getMessage(), element);
      }
    }

    return false;
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(Annotation.class.getName());
  }
}
