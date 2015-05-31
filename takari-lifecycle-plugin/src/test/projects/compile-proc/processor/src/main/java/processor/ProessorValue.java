package processor;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("processor.Annotation")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ProessorValue extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      TypeElement type = (TypeElement) element;
      Object value = null;
      for (Element child : type.getEnclosedElements()) {
        if (child instanceof VariableElement && "VALUE".equals(child.getSimpleName().toString())) {
          value = ((VariableElement) child).getConstantValue();
          break;
        }
      }
      if (value != null) {
        String pkg = ((PackageElement) type.getEnclosingElement()).getQualifiedName().toString();
        String name = type.getSimpleName().toString() + ".value";
        try {
          FileObject resource = processingEnv.getFiler()
              .createResource(StandardLocation.CLASS_OUTPUT, pkg, name, type);
          try (Writer writer = resource.openWriter()) {
            writer.append(value.toString());
          }
        } catch (IOException e) {
          processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
      }
    }

    return false; // not "claimed" so multiple processors can be tested
  }

}
