package io.tesla.maven.plugins.compiler.jdt;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class DependencyTracker implements Serializable {
  private static final long serialVersionUID = -4281576243920771467L;

  public static final String KEY = DependencyTracker.class.getName();

  private final Map<File, String> providedTypes = new HashMap<File, String>();

  private final Map<File, String> providedSimpleNames = new HashMap<File, String>();

  private final Map<String, Set<File>> typeReferences = new HashMap<String, Set<File>>();

  private final Map<String, Set<File>> simpleNameReferences = new HashMap<String, Set<File>>();

  private final Map<File, Set<File>> inputOutputs = new HashMap<File, Set<File>>();

  /**
   * Removes specified output file from the tracker state. Returns collection of input files that
   * depend on the type provided by the output.
   */
  public Collection<File> removeOutput(File output) {
    Set<File> affectedInputs = new HashSet<File>();
    String providedType = providedTypes.remove(output);
    if (providedType != null) {
      addAll(affectedInputs, typeReferences.get(providedType));
    }

    String providedSimpleType = providedSimpleNames.get(output);
    if (providedSimpleType != null) {
      addAll(affectedInputs, simpleNameReferences.get(providedSimpleType));
    }

    // cleanup type references
    // XXX this will almost certainly require optimization
    File removedInput = null;
    final Iterator<Map.Entry<File, Set<File>>> inputIterator = inputOutputs.entrySet().iterator();
    while (inputIterator.hasNext()) {
      final Map.Entry<File, Set<File>> inputOutputs = inputIterator.next();
      if (inputOutputs.getValue().remove(output)) {
        if (inputOutputs.getValue().isEmpty()) {
          removedInput = inputOutputs.getKey();
          inputIterator.remove();
          break;
        }
      }
    }
    if (removedInput != null) {
      final Iterator<Map.Entry<String, Set<File>>> typeReferencesIterator =
          typeReferences.entrySet().iterator();
      while (typeReferencesIterator.hasNext()) {
        final Map.Entry<String, Set<File>> typeReferences = typeReferencesIterator.next();
        if (typeReferences.getValue().remove(removedInput) && typeReferences.getValue().isEmpty()) {
          typeReferencesIterator.remove();
        }
      }
    }

    return affectedInputs != null ? affectedInputs : Collections.<File>emptySet();
  }

  /**
   * Adds new or modified output file and corresponding java type to the tracker state. Returns
   * possibly empty collection of input files that reference the java type.
   */
  public Collection<File> addOutput(File input, File output, String providedType,
      String providedSimpleName) {
    Set<File> outputs = inputOutputs.get(input);
    if (outputs == null) {
      outputs = new HashSet<File>();
      inputOutputs.put(input, outputs);
    }
    outputs.add(output);

    providedTypes.put(output, providedType);
    providedSimpleNames.put(output, providedSimpleName);

    Set<File> affectedInputs = new HashSet<File>();
    addAll(affectedInputs, this.typeReferences.get(providedType));
    addAll(affectedInputs, this.simpleNameReferences.get(providedSimpleName));
    return affectedInputs;
  }

  private static void addAll(Set<File> result, Set<File> set) {
    if (set != null) {
      result.addAll(set);
    }
  }

  public void addReferencedType(File intput, String referencedType) {
    Set<File> references = typeReferences.get(referencedType);
    if (references == null) {
      references = new HashSet<File>();
      typeReferences.put(referencedType, references);
    }
    references.add(intput);
  }

  public void addReferencedSimpleName(File intput, String referencedSimpleName) {
    Set<File> references = simpleNameReferences.get(referencedSimpleName);
    if (references == null) {
      references = new HashSet<File>();
      simpleNameReferences.put(referencedSimpleName, references);
    }
    references.add(intput);
  }

}
