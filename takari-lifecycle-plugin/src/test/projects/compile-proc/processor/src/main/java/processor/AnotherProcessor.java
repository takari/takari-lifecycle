package processor;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

@SupportedAnnotationTypes("processor.Annotation")
public class AnotherProcessor extends ProcessorImpl {

  public AnotherProcessor() {
    super("AnotherGenerated");
  }

}
