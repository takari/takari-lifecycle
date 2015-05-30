package processor;

import java.io.IOException;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

// processor uses Filer#createResource to create Kind.SOURCE files
@SupportedAnnotationTypes("processor.Annotation")
public class ProcessorCreateResource extends Processor {

  @Override
  protected FileObject createFile(String pkgName, String clsSimpleName, Element element)
      throws IOException {
    return processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, pkgName,
        clsSimpleName + JavaFileObject.Kind.SOURCE.extension, element);
  }
}
