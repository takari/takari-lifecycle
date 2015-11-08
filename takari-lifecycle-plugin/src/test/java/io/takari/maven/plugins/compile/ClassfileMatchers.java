package io.takari.maven.plugins.compile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.common.io.Files;

class ClassfileMatchers {

  private static class DebugInfo extends ClassVisitor {
    private boolean hasSource = false;
    private boolean hasLines = false;
    private boolean hasVars = false;

    public DebugInfo() {
      super(Opcodes.ASM5);
    }

    @Override
    public void visitSource(String source, String debug) {
      hasSource = true;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM5) {
        @Override
        public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
          hasVars = true;
        }

        @Override
        public void visitLineNumber(int line, Label start) {
          hasLines = true;
        }
      };
    }

    public boolean hasSource() {
      return hasSource;
    }

    public boolean hasVars() {
      return hasVars;
    }

    public boolean hasLines() {
      return hasLines;
    }
  };

  private static class AnnotationInfo extends ClassVisitor {

    private final Set<String> annotations = new HashSet<>();

    public AnnotationInfo() {
      super(Opcodes.ASM5);
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      annotations.add(desc);
      return null;
    }

    public Set<String> getAnnotations() {
      return annotations;
    }
  }

  private static class MethodParameterInfo extends ClassVisitor {

    private Set<String> methodParameterNames = new HashSet<>();

    public MethodParameterInfo() {
      super(Opcodes.ASM5);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM5) {

        @Override
        public void visitParameter(String name, int access) {
          methodParameterNames.add(name);
          super.visitParameter(name, access);
        }
      };
    }

    public boolean hasMethodParameterName(String methodParameterName) {
      return methodParameterNames.contains(methodParameterName);
    }
  };

  private static abstract class ClassfileMatcher<T extends ClassVisitor> extends BaseMatcher<File> {
    private String description;

    protected ClassfileMatcher(String description) {
      this.description = description;
    }

    @Override
    public final boolean matches(Object item) {
      File file = (File) item;
      try (InputStream is = Files.asByteSource(file).openBufferedStream()) {
        T visitor = newClassVisitor();
        new ClassReader(is).accept(visitor, 0);
        return matches(visitor);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void describeTo(Description description) {
      description.appendText(this.description);
    }

    protected abstract T newClassVisitor();

    protected abstract boolean matches(T info);
  }

  public static Matcher<File> hasDebugSource() {
    return new ClassfileMatcher<DebugInfo>("include debug source info") {
      @Override
      protected boolean matches(DebugInfo info) {
        return info.hasSource();
      }

      @Override
      protected DebugInfo newClassVisitor() {
        return new DebugInfo();
      }
    };
  }

  public static Matcher<File> hasDebugLines() {
    return new ClassfileMatcher<DebugInfo>("include debug lines info") {
      @Override
      protected boolean matches(DebugInfo info) {
        return info.hasLines();
      }

      @Override
      protected DebugInfo newClassVisitor() {
        return new DebugInfo();
      }
    };
  }

  public static Matcher<File> hasDebugVars() {
    return new ClassfileMatcher<DebugInfo>("include debug lines info") {
      @Override
      protected boolean matches(DebugInfo info) {
        return info.hasVars();
      }

      @Override
      protected DebugInfo newClassVisitor() {
        return new DebugInfo();
      }
    };
  }

  public static Matcher<File> hasAnnotation(final String annotation) {
    final String desc = "L" + annotation.replace('.', '/') + ";";
    return new ClassfileMatcher<AnnotationInfo>("has annotation " + annotation) {
      @Override
      protected boolean matches(AnnotationInfo info) {
        return info.getAnnotations().contains(desc);
      }

      @Override
      protected AnnotationInfo newClassVisitor() {
        return new AnnotationInfo();
      }
    };
  }

  public static Matcher<File> hasMethodParameterWithName(final String parameter) {
    return new ClassfileMatcher<MethodParameterInfo>("has method parameter " + parameter) {
      @Override
      protected boolean matches(MethodParameterInfo info) {
        return info.hasMethodParameterName(parameter);
      }

      @Override
      protected MethodParameterInfo newClassVisitor() {
        return new MethodParameterInfo();
      }
    };
  }
}
