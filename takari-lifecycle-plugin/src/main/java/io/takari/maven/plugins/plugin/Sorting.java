package io.takari.maven.plugins.plugin;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.takari.maven.plugins.plugin.model.MojoDescriptor;
import io.takari.maven.plugins.plugin.model.MojoParameter;
import io.takari.maven.plugins.plugin.model.MojoRequirement;

class Sorting {

  public static void sortDescriptors(List<MojoDescriptor> descriptors) {
    Collections.sort(descriptors, new Comparator<MojoDescriptor>() {
      @Override
      public int compare(MojoDescriptor p1, MojoDescriptor p2) {
        return p1.getImplementation().compareTo(p2.getImplementation());
      }
    });
  }

  public static void sortParameters(List<MojoParameter> parameters) {
    Collections.sort(parameters, new Comparator<MojoParameter>() {
      @Override
      public int compare(MojoParameter p1, MojoParameter p2) {
        return p1.getName().compareTo(p2.getName());
      }
    });
  }

  public static void sortRequirements(List<MojoRequirement> requirements) {
    Collections.sort(requirements, new Comparator<MojoRequirement>() {
      @Override
      public int compare(MojoRequirement p1, MojoRequirement p2) {
        return p1.getFieldName().compareTo(p2.getFieldName());
      }
    });
  }
}
