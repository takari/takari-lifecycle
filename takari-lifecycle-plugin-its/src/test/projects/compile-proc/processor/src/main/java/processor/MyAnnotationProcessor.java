/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package processor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("processor.MyAnnotation")
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class MyAnnotationProcessor extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(MyAnnotation.class)) {
      try {
        TypeElement cls = (TypeElement) element;
        PackageElement pkg = (PackageElement) cls.getEnclosingElement();
        String clsSimpleName = "My" + cls.getSimpleName();
        String clsQualifiedName = pkg.getQualifiedName() + "." + clsSimpleName;
        JavaFileObject sourceFile =
            processingEnv.getFiler().createSourceFile(clsQualifiedName, element);
        BufferedWriter w = new BufferedWriter(sourceFile.openWriter());
        try {
          w.append("package ").append(pkg.getQualifiedName()).append(";");
          w.newLine();
          w.append("public class ").append(clsSimpleName).append(" { }");
        } finally {
          w.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    return true;
  }

}
