package processor;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.TypeElement;

@SupportedAnnotationTypes("processor.Annotation")
public class ProcessorWithOptions extends AbstractProcessor {

  private Map<String, String> options;

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

    if (!"valueA".equals(options.get("optionA")) || !"valueB".equals(options.get("optionB"))) {
      throw new RuntimeException(options.toString());
    }

    return false; // not "claimed" so multiple processors can be tested
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    this.options = new HashMap<String, String>(processingEnv.getOptions());
  }
}
