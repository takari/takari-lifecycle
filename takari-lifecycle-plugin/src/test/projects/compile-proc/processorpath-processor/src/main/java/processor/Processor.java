package processor;

import javax.annotation.processing.SupportedAnnotationTypes;

@SupportedAnnotationTypes("processor.Annotation")
public class Processor extends ProcessorImpl {

  public Processor() {
    super("Generated");
  }

}
