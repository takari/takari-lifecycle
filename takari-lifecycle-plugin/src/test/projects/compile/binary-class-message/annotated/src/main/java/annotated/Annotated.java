package annotated;

import annotation.Annotation;

@Annotation(description = "description")
public class Annotated {

  @Annotation(description = "description")
  public static String method() {
    return null;
  }
}
