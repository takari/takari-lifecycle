package processor;

import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;

import static javax.lang.model.util.ElementFilter.*;

/**
 * Writes annotated element type's nested type members to a file. (very convoluted, I know)
 * 
 * For example, writes {@code nestedClassField FIELD} for the following two example classes
 * 
 * <pre>
 * class Dependency {
 *   class Nested {
 *     String nestedClassField;
 *   }
 * }
 * 
 * 
 * class Annotated {
 *   &#64;Annotation
 *   Dependency annottedField;
 * }
 * </pre>
 * 
 * 
 */
@SupportedAnnotationTypes("processor.Annotation")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class ElementsTypeMemberListProcessor extends AbstractElementsListProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      Element type = ((DeclaredType) element.asType()).asElement();
      for (TypeElement nestedType : typesIn(type.getEnclosedElements())) {
        for (Element nestedTypeMember : nestedType.getEnclosedElements()) {
          elements.put(nestedTypeMember.getSimpleName().toString(), nestedTypeMember);
        }
      }
    }

    writeElements(roundEnv);

    return false;
  }
}
