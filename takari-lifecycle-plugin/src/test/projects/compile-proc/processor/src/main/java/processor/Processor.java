package processor;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

@SupportedAnnotationTypes("processor.Annotation")
public class Processor extends ProcessorImpl {

  public Processor() {
    super("Generated");
  }

}
