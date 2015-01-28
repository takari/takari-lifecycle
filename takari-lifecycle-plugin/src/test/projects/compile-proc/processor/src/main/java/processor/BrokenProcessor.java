package processor;

import java.io.BufferedWriter;
import java.io.IOException;

import javax.annotation.processing.SupportedAnnotationTypes;

@SupportedAnnotationTypes("processor.Annotation")
public class BrokenProcessor extends ProcessorImpl {

  public BrokenProcessor() {
    super("Broken");
  }

  @Override
  protected void appendBody(String pkgName, String clsName, BufferedWriter w) throws IOException {
    w.append(" { Missing missing; }");
  }

}
