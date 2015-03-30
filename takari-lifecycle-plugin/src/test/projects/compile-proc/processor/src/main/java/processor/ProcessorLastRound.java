package processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("processor.Annotation")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ProcessorLastRound extends AbstractProcessor {

  private TreeSet<String> types = new TreeSet<>();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      TypeElement cls = (TypeElement) element;
      types.add(cls.getQualifiedName().toString());
    }

    if (roundEnv.processingOver()) {
      try {
        FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "types.lst");
        BufferedWriter w = new BufferedWriter(resource.openWriter());
        try {
          for (String type : types) {
            w.append(type);
            w.newLine();
          }
        } finally {
          w.close();
        }
      } catch (IOException e) {
        processingEnv.getMessager().printMessage(Kind.ERROR, "Could not create output " + e.getMessage());
      }
    }

    return false; // not "claimed" so multiple processors can be tested
  }

}
