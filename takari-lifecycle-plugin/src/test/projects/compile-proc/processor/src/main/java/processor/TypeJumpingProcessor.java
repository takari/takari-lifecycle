package processor;

import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("processor.Annotation")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class TypeJumpingProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    final Elements elements = processingEnv.getElementUtils();
    Filer filer = processingEnv.getFiler();
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      try {
        TypeElement step = (TypeElement) element;
        TypeElement cls = elements.getTypeElement(step.getQualifiedName() + "Jump");
        if (cls != null) {
          String resName = cls.getQualifiedName().toString();
          Element[] origins = new Element[] { cls }; // purposely underspecified originating elements
          FileObject res = filer.createResource(StandardLocation.CLASS_OUTPUT, "", resName, origins);
          res.openWriter().close();
        }
      } catch (IOException e) {
        e.printStackTrace();
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage(), element);
      }
    }
    return false;
  }

}
