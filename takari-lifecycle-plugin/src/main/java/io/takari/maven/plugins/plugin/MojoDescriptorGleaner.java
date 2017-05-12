package io.takari.maven.plugins.plugin;

import static io.takari.maven.plugins.plugin.PluginDescriptorMojo.PATH_MOJOS_XML;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import io.takari.maven.plugins.plugin.model.MojoDescriptor;
import io.takari.maven.plugins.plugin.model.MojoParameter;
import io.takari.maven.plugins.plugin.model.MojoRequirement;
import io.takari.maven.plugins.plugin.model.PluginDescriptor;
import io.takari.maven.plugins.plugin.model.io.xpp3.PluginDescriptorXpp3Writer;

/**
 * @TODO access javadoc tag text, like @deprecated and @since
 */
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class MojoDescriptorGleaner extends AbstractProcessor {

  private final TreeMap<String, MojoDescriptor> descriptors = new TreeMap<>();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (TypeElement element : getAnnotatedTypes(roundEnv)) {
      MojoDescriptor descriptor = processType(element);
      descriptors.put(descriptor.getImplementation(), descriptor);
    }

    if (roundEnv.processingOver() && !descriptors.isEmpty()) {
      try {
        writeMojosXml(descriptors);
      } catch (IOException e) {
        // TODO it'd be nice to capture IOException somewhere too
        processingEnv.getMessager().printMessage(Kind.ERROR, "Could not create aggregate output " + e.getMessage());
      }
    }

    return false; // don't claim the annotations, other processors are welcome to use them
  }

  private Set<TypeElement> getAnnotatedTypes(RoundEnvironment roundEnv) {
    Set<TypeElement> types = new HashSet<>();
    roundEnv.getElementsAnnotatedWith(Mojo.class).forEach(type -> types.add((TypeElement) type));
    addAnnotatedMembers(types, roundEnv, Parameter.class);
    addAnnotatedMembers(types, roundEnv, Component.class);
    return types;
  }

  private void addAnnotatedMembers(Set<TypeElement> types, RoundEnvironment roundEnv, Class<? extends Annotation> annotation) {
    for (Element member : roundEnv.getElementsAnnotatedWith(annotation)) {
      Element type = member.getEnclosingElement();
      if (type.getEnclosingElement().getKind() == ElementKind.PACKAGE) {
        types.add((TypeElement) type);
      } else {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, annotation + " only applicable to top-level class members", member);
      }
    }
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    Set<String> types = new HashSet<>();
    types.add(Mojo.class.getName());
    types.add(Parameter.class.getName());
    types.add(Component.class.getName());
    return types;
  }

  MojoDescriptor processType(TypeElement type) {
    MojoDescriptor descriptor = new MojoDescriptor();

    descriptor.setImplementation(type.getQualifiedName().toString());
    descriptor.setSuperclasses(getSuperclasses(type, new ArrayList<String>()));

    Mojo mojo = type.getAnnotation(Mojo.class);
    if (mojo != null) {
      // mojo is null for classes that have @Parameter/@Component element annotations

      descriptor.setLanguage("java");
      descriptor.setGoal(mojo.name());
      descriptor.setExecutionStrategy(mojo.executionStrategy());
      descriptor.setRequiresProject(mojo.requiresProject());
      descriptor.setRequiresReports(mojo.requiresReports());
      descriptor.setAggregator(mojo.aggregator());
      descriptor.setRequiresDirectInvocation(mojo.requiresDirectInvocation());
      descriptor.setRequiresOnline(mojo.requiresOnline());
      descriptor.setInheritedByDefault(mojo.inheritByDefault());
      if (!isEmpty(mojo.configurator())) {
        descriptor.setConfigurator(mojo.configurator());
      }
      descriptor.setThreadSafe(mojo.threadSafe());
      descriptor.setPhase(mojo.defaultPhase().id());
      descriptor.setRequiresDependencyResolution(mojo.requiresDependencyResolution().id());
      descriptor.setRequiresDependencyCollection(mojo.requiresDependencyCollection().id());
      descriptor.setInstantiationStrategy(mojo.instantiationStrategy().id());

      if (getElementUtils().isDeprecated(type)) {
        descriptor.setDeprecated("No reason given"); // TODO parse javadoc
      }
      // This is not ideal
      // the proper way would be to add support for processorPath to compiler plugin
      // which would allow us to do this as part of takari-builder annotation processing
      // without adding takari-builder-apt to the compile classpath
      // we intend to do this properly... soon
      if (isTakariBuilderMojo(type)) {
        descriptor.setTakariBuilder(true);
      }
      descriptor.setDescription(getDescription(type));
    }

    processTypeFields(type, descriptor);

    Sorting.sortParameters(descriptor.getParameters());
    Sorting.sortRequirements(descriptor.getRequirements());

    return descriptor;
  }

  private boolean isTakariBuilderMojo(TypeElement type) {
    TypeElement abstractIncrementalMojoType = getElementUtils().getTypeElement("io.takari.builder.internal.maven.AbstractIncrementalMojo");
    return abstractIncrementalMojoType != null && getTypeUtils().isSubtype(type.asType(), abstractIncrementalMojoType.asType());
  }

  private void processTypeFields(TypeElement type, MojoDescriptor descriptor) {
    // non-static fields
    for (Element member : type.getEnclosedElements()) {
      if (member instanceof VariableElement) {
        Parameter parameter = member.getAnnotation(Parameter.class);
        Component component = member.getAnnotation(Component.class);
        if (parameter != null && component != null) {
          // TODO error marker
        }
        if (parameter != null) {
          descriptor.addParameter(toParameterDescriptor((VariableElement) member, parameter));
        } else if (component != null) {
          descriptor.addRequirement(toComponentDescriptor((VariableElement) member, component));
        }
      }
    }
  }

  private MojoRequirement toComponentDescriptor(VariableElement field, Component component) {
    MojoRequirement result = new MojoRequirement();
    result.setFieldName(field.getSimpleName().toString());
    result.setRole(getComponentRole(field, component));
    result.setRoleHint(component.hint());
    return result;
  }

  private String getComponentRole(VariableElement field, Component component) {
    String role;
    try {
      role = component.role().getName();
    } catch (MirroredTypeException e) {
      role = e.getTypeMirror().toString();
    }
    if (!Object.class.getName().equals(role)) {
      return role;
    }
    return getTypeString(field.asType());
  }

  private String getTypeString(TypeMirror type) {
    TypeElement typeElement = (TypeElement) getTypeUtils().asElement(type);
    if (typeElement != null) {
      // this returns raw parameterized types
      return typeElement.getQualifiedName().toString();
    }
    // this deals with primitive and array types
    return type.toString();
  }

  private MojoParameter toParameterDescriptor(VariableElement field, Parameter parameter) {
    MojoParameter result = new MojoParameter();
    result.setName(field.getSimpleName().toString());
    if (!isEmpty(parameter.alias())) {
      result.setAlias(parameter.alias());
    }
    if (!isEmpty(parameter.defaultValue())) {
      result.setDefaultValue(parameter.defaultValue());
    }
    if (!isEmpty(parameter.property())) {
      result.setExpression("${" + parameter.property() + "}");
    }
    result.setEditable(!parameter.readonly());
    result.setRequired(parameter.required());
    result.setType(getTypeString(field.asType()));
    result.setDescription(getDescription(field));

    return result;
  }

  private List<String> getSuperclasses(TypeElement type, List<String> superclasses) {
    TypeElement superclass = type;
    while ((superclass = (TypeElement) getTypeUtils().asElement(superclass.getSuperclass())) != null) {
      String qualifiedName = superclass.getQualifiedName().toString();
      if ("java.lang.Object".equals(qualifiedName)) {
        break;
      }
      superclasses.add(qualifiedName);
    }
    return superclasses;
  }

  private Types getTypeUtils() {
    return processingEnv.getTypeUtils();
  }

  private String getDescription(Element element) {
    String description = getElementUtils().getDocComment(element);
    if (description != null) {
      description = description.trim();
    }
    return !isEmpty(description) ? description : null;
  }

  private Elements getElementUtils() {
    return processingEnv.getElementUtils();
  }

  private static boolean isEmpty(String str) {
    return str == null || str.isEmpty();
  }

  void writeMojosXml(TreeMap<String, MojoDescriptor> descriptors) throws IOException {
    PluginDescriptor mojos = new PluginDescriptor();
    for (MojoDescriptor descriptor : descriptors.values()) {
      mojos.addMojo(descriptor);
    }
    FileObject output = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", PATH_MOJOS_XML);
    try (OutputStream out = output.openOutputStream()) {
      new PluginDescriptorXpp3Writer().write(out, mojos);
    }
  }

}
