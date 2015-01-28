package processor;

import javax.annotation.processing.SupportedAnnotationTypes;

@SupportedAnnotationTypes("processor.Annotation")
public class AnotherProcessor extends ProcessorImpl {

  public AnotherProcessor() {
    super("AnotherGenerated");
  }

}
