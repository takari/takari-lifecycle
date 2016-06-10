/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.jdt;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.classfmt.FieldInfo;
import org.eclipse.jdt.internal.compiler.classfmt.MethodInfo;
import org.eclipse.jdt.internal.compiler.codegen.AnnotationTargetTypeConstants;
import org.eclipse.jdt.internal.compiler.env.ClassSignature;
import org.eclipse.jdt.internal.compiler.env.EnumConstantSignature;
import org.eclipse.jdt.internal.compiler.env.IBinaryAnnotation;
import org.eclipse.jdt.internal.compiler.env.IBinaryElementValuePair;
import org.eclipse.jdt.internal.compiler.env.IBinaryNestedType;
import org.eclipse.jdt.internal.compiler.env.IBinaryType;
import org.eclipse.jdt.internal.compiler.env.IBinaryTypeAnnotation;
import org.eclipse.jdt.internal.compiler.impl.Constant;
import org.eclipse.jdt.internal.compiler.lookup.TagBits;
import org.eclipse.jdt.internal.compiler.lookup.TypeIds;

import com.google.common.base.Charsets;

/**
 * Adopted from {@link ClassFileReader#hasStructuralChanges(byte[], boolean, boolean)}
 * 
 * Last updated to match JDT I20160517-2000.
 */
public class ClassfileDigester {

  // TODO use Guava Hasher
  private final MessageDigest digester;

  public ClassfileDigester() {
    try {
      digester = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unsupported JVM", e);
    }
  }

  public byte[] digest(IBinaryType classFile) {

    // type level comparison
    // modifiers
    updateInt(classFile.getModifiers());

    // only consider a portion of the tagbits which indicate a structural change for dependents
    // e.g. @Override change has no influence outside
    long OnlyStructuralTagBits = TagBits.AnnotationTargetMASK // different @Target status ?
        | TagBits.AnnotationDeprecated // different @Deprecated status ?
        | TagBits.AnnotationRetentionMASK // different @Retention status ?
        | TagBits.HierarchyHasProblems; // different hierarchy status ?

    // meta-annotations
    updateLong(classFile.getTagBits() & OnlyStructuralTagBits);
    // annotations
    updateAnnotations(classFile.getAnnotations());
    updateTypeAnnotations(classFile.getTypeAnnotations());

    // generic signature
    updateChars(classFile.getGenericSignature());
    // superclass
    updateChars(classFile.getSuperclassName());
    // interfaces
    char[][] interfacesNames = classFile.getInterfaceNames();
    if (interfacesNames != null) {
      for (int i = 0; i < interfacesNames.length; i++) {
        updateChars(interfacesNames[i]);
      }
    }

    // member types
    IBinaryNestedType[] memberTypes = classFile.getMemberTypes();
    if (memberTypes != null) {
      for (int i = 0; i < memberTypes.length; i++) {
        updateChars(memberTypes[i].getName());
        updateInt(memberTypes[i].getModifiers());
      }
    }

    // fields
    FieldInfo[] fieldInfos = (FieldInfo[]) classFile.getFields();
    if (fieldInfos != null) {
      for (int i = 0; i < fieldInfos.length; i++) {
        updateField(fieldInfos[i]);
      }
    }

    // methods
    MethodInfo[] methodInfos = (MethodInfo[]) classFile.getMethods();
    if (methodInfos != null) {
      for (int i = 0; i < methodInfos.length; i++) {
        updateMethod(classFile, methodInfos[i]);
      }
    }

    // missing types
    char[][][] missingTypes = classFile.getMissingTypeNames();
    if (missingTypes != null) {
      for (int i = 0; i < missingTypes.length; i++) {
        for (int j = 0; j < missingTypes[i].length; j++) {
          if (j > 0) {
            updateChar('.'); // don't ask why
          }
          updateChars(missingTypes[i][j]);
        }
      }
    }

    return digester.digest();
  }

  private void updateMethod(IBinaryType classFile, MethodInfo methodInfo) {
    // generic signature
    updateChars(methodInfo.getGenericSignature());
    updateInt(methodInfo.getModifiers());
    updateLong(methodInfo.getTagBits() & TagBits.AnnotationDeprecated);
    updateAnnotations(methodInfo.getAnnotations());
    // parameter annotations:
    for (int i = 0; i < methodInfo.getAnnotatedParametersCount(); i++) {
      updateAnnotations(methodInfo.getParameterAnnotations(i, classFile.getName()));
    }
    updateTypeAnnotations(methodInfo.getTypeAnnotations());

    updateChars(methodInfo.getSelector());
    updateChars(methodInfo.getMethodDescriptor());
    updateChars(methodInfo.getGenericSignature());

    char[][] thrownExceptions = methodInfo.getExceptionTypeNames();
    for (int i = 0; i < thrownExceptions.length; i++) {
      updateChars(thrownExceptions[i]);
    }
  }

  private void updateField(FieldInfo fieldInfo) {
    // generic signature
    updateChars(fieldInfo.getGenericSignature());
    updateInt(fieldInfo.getModifiers());
    updateLong(fieldInfo.getTagBits() & TagBits.AnnotationDeprecated);
    updateAnnotations(fieldInfo.getAnnotations());
    updateTypeAnnotations(fieldInfo.getTypeAnnotations());
    updateChars(fieldInfo.getName());
    updateChars(fieldInfo.getTypeName());
    updateBoolean(fieldInfo.hasConstant());
    if (fieldInfo.hasConstant()) {
      updateConstant(fieldInfo.getConstant());
    }
  }

  private void updateConstant(Constant constant) {
    updateInt(constant.typeID());
    updateString(constant.getClass().getName());
    switch (constant.typeID()) {
      case TypeIds.T_int:
        updateInt(constant.intValue());
        break;
      case TypeIds.T_byte:
        updateByte(constant.byteValue());
        break;
      case TypeIds.T_short:
        updateShort(constant.shortValue());
        break;
      case TypeIds.T_char:
        updateChar(constant.charValue());
        break;
      case TypeIds.T_long:
        updateLong(constant.longValue());
        break;
      case TypeIds.T_float:
        updateFloat(constant.floatValue());
        break;
      case TypeIds.T_double:
        updateDouble(constant.doubleValue());
        break;
      case TypeIds.T_boolean:
        updateBoolean(constant.booleanValue());
        break;
      case TypeIds.T_JavaLangString:
        updateString(constant.stringValue());
        break;
      default:
        throw new IllegalArgumentException("Unexpected constant typeID=" + constant.typeID());
    }
  }

  private void updateAnnotations(IBinaryAnnotation[] annotations) {
    if (annotations != null) {
      for (int i = 0; i < annotations.length; i++) {
        updateAnnotation(annotations[i]);
      }
    }
  }

  private void updateAnnotation(IBinaryAnnotation annotation) {
    updateChars(annotation.getTypeName());
    IBinaryElementValuePair[] pairs = annotation.getElementValuePairs();
    for (int j = 0; j < pairs.length; j++) {
      updateChars(pairs[j].getName());
      final Object value = pairs[j].getValue();
      if (value instanceof Object[]) {
        Object[] values = (Object[]) value;
        for (int n = 0; n < values.length; n++) {
          updateAnnotationValue(values[n]);
        }
      } else {
        updateAnnotationValue(value);
      }
    }
  }

  private void updateAnnotationValue(Object object) {
    // @see org.eclipse.jdt.internal.compiler.env.IBinaryElementValuePair.getValue()
    // @see org.eclipse.jdt.internal.compiler.classfmt.AnnotationInfo.decodeDefaultValue()
    if (object instanceof ClassSignature) {
      updateChars(((ClassSignature) object).getTypeName());
    } else if (object instanceof Constant) {
      updateConstant((Constant) object);
    } else if (object instanceof EnumConstantSignature) {
      updateChars(((EnumConstantSignature) object).getTypeName());
      updateChars(((EnumConstantSignature) object).getEnumConstantName());
    } else if (object instanceof IBinaryAnnotation) {
      updateAnnotation((IBinaryAnnotation) object);
    } else {
      throw new IllegalArgumentException("Unsupported annotation value " + object.toString());
    }
  }

  private void updateTypeAnnotations(IBinaryTypeAnnotation[] typeAnnotations) {
    if (typeAnnotations != null) {
      for (IBinaryTypeAnnotation typeAnnotation : typeAnnotations) {
        if (!affectsSignature(typeAnnotation)) {
          continue;
        }
        updateAnnotation(typeAnnotation.getAnnotation());
      }
    }
  }

  private boolean affectsSignature(IBinaryTypeAnnotation typeAnnotation) {
    int targetType = typeAnnotation.getTargetType();
    if (targetType >= AnnotationTargetTypeConstants.LOCAL_VARIABLE && targetType <= AnnotationTargetTypeConstants.METHOD_REFERENCE_TYPE_ARGUMENT) {
      return false; // affects detail within a block
    }
    return true;
  }

  //
  // TODO move to a general purpose digester?
  //

  private void updateLong(long value) {
    byte[] tmp = new byte[8];
    tmp[0] = (byte) ((value >> 0x00) & 0xFF);
    tmp[1] = (byte) ((value >> 0x08) & 0xFF);
    tmp[2] = (byte) ((value >> 0x10) & 0xFF);
    tmp[3] = (byte) ((value >> 0x18) & 0xFF);
    tmp[4] = (byte) ((value >> 0x20) & 0xFF);
    tmp[5] = (byte) ((value >> 0x28) & 0xFF);
    tmp[6] = (byte) ((value >> 0x30) & 0xFF);
    tmp[7] = (byte) ((value >> 0x38) & 0xFF);
    digester.update(tmp);
  }

  private void updateInt(int value) {
    byte[] tmp = new byte[4];
    tmp[0] = (byte) ((value >> 0x00) & 0xFF);
    tmp[1] = (byte) ((value >> 0x08) & 0xFF);
    tmp[2] = (byte) ((value >> 0x10) & 0xFF);
    tmp[3] = (byte) ((value >> 0x18) & 0xFF);
    digester.update(tmp);
  }

  private void updateShort(short value) {
    byte[] tmp = new byte[2];
    tmp[0] = (byte) ((value >> 0x00) & 0xFF);
    tmp[1] = (byte) ((value >> 0x08) & 0xFF);
    digester.update(tmp);
  }

  private void updateChars(char[] value) {
    if (value != null) {
      for (int i = 0; i < value.length; i++) {
        updateChar(value[i]);
      }
    }
  }

  private void updateString(String value) {
    digester.update(value.getBytes(Charsets.UTF_8));
  }

  private void updateDouble(double value) {
    updateLong(Double.doubleToRawLongBits(value));
  }

  private void updateFloat(float value) {
    updateInt(Float.floatToRawIntBits(value));
  }

  private void updateChar(char value) {
    updateInt(value);
  }

  private void updateByte(byte value) {
    digester.update(value);
  }

  private void updateBoolean(boolean value) {
    digester.update(value ? (byte) 1 : (byte) 0);
  }

}
