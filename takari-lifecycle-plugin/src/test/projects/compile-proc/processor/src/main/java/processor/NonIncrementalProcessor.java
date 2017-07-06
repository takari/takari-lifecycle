package processor;

import java.io.IOException;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.tools.FileObject;

@SupportedAnnotationTypes("processor.Annotation")
public class NonIncrementalProcessor extends ProcessorImpl {

  public NonIncrementalProcessor() {
    super("NonIncremental");
  }

  @Override
  protected FileObject createFile(String pkgName, String clsSimpleName, Element element)
      throws IOException {
    String clsQualifiedName = pkgName + "." + clsSimpleName;

    // note this does not pass originating element to Filer, hence "non-incremental"
    return processingEnv.getFiler().createSourceFile(clsQualifiedName);
  }

}
