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

@SupportedAnnotationTypes("processor.Annotation")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class ProcessorLastRound_typeIndex extends AbstractProcessor {

  private TreeSet<String> types = new TreeSet<>();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(Annotation.class)) {
      TypeElement cls = (TypeElement) element;
      types.add(cls.getSimpleName().toString());
    }

    if (roundEnv.processingOver()) {
      try {
        FileObject resource = processingEnv.getFiler().createSourceFile("generated.TypeIndex");
        try (BufferedWriter w = new BufferedWriter(resource.openWriter())) {
          w.append("package generated;");
          w.newLine();
          w.append("public interface TypeIndex {");
          w.newLine();
          for (String type : types) {
            w.append("public static final String ").append(type.toUpperCase()).append(" = null;");
            w.newLine();
          }
          w.append("}");
          w.newLine();
        }

        FileObject resource2 = processingEnv.getFiler().createSourceFile("generated.TypeIndex2");
        try (BufferedWriter w = new BufferedWriter(resource2.openWriter())) {
          w.append("package generated;");
          w.newLine();
          w.append("public interface TypeIndex2 extends generated.TypeIndex {}");
          w.newLine();
        }
      } catch (IOException e) {
        processingEnv.getMessager().printMessage(Kind.ERROR,
            "Could not create output " + e.getMessage());
      }
    }

    return false; // not "claimed" so multiple processors can be tested
  }

}
