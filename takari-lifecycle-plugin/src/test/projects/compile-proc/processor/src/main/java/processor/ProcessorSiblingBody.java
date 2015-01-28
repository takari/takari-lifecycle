package processor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import javax.annotation.processing.SupportedAnnotationTypes;

@SupportedAnnotationTypes("processor.Annotation")
public class ProcessorSiblingBody extends ProcessorImpl {

  public ProcessorSiblingBody() {
    super("Generated");
  }

  @Override
  protected void appendBody(String pkgName, String clsName, BufferedWriter w) throws IOException {
    File basedir = new File(processingEnv.getOptions().get("basedir"));
    File body = new File(basedir, pkgName.replace('.', '/') + "/" + clsName + ".body");
    try (Reader r = new InputStreamReader(new FileInputStream(body), "UTF-8")) {
      int c;
      while ((c = r.read()) >= 0) {
        w.append((char) c);
      }
    }
  }
}
