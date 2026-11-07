package io.takari.lifecycle.uts.plugindescriptor;

import java.util.List;
import java.util.Map;

import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

public class glean_RequirementTypes extends glean_Generic<String> {

  public static class Nested {}

  @Parameter
  private Nested nestedParameter;

  @Component(role = Nested.class)
  private Object nestedRoleRequirement;


  @Component
  private Map<String, String> mapRequirement;

  @Component
  private List<String> listRequirement;

  @Parameter
  private List<String> listParameter;

  @Parameter
  private boolean booleanParameter;

  @Parameter
  private String[] arrayParameter;
}
