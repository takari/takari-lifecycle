package processor;

import java.io.BufferedWriter;
import java.io.IOException;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

@SupportedAnnotationTypes("processor.Annotation")
public class BrokenProcessor extends ProcessorImpl {

  public BrokenProcessor() {
    super("Broken");
  }

  protected void appendBody(BufferedWriter w) throws IOException {
    w.append(" { Missing missing; }");
  }

}
