package io.takari.maven.plugins.compile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.google.common.io.Files;

class ClassfileMatchers {

  private static class ClassInfo extends ClassVisitor {
    private boolean hasSource = false;
    private boolean hasLines = false;
    private boolean hasVars = false;

    public ClassInfo() {
      super(Opcodes.ASM4);
    }

    @Override
    public void visitSource(String source, String debug) {
      hasSource = true;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM4) {
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

  private static ClassInfo parse(File file) throws IOException {
    InputStream is = Files.asByteSource(file).openBufferedStream();
    try {
      final ClassReader r = new ClassReader(is);
      ClassInfo info = new ClassInfo();
      r.accept(info, 0);
      return info;
    } finally {
      is.close();
    }
  }

  private static abstract class ClassfileMatcher extends BaseMatcher<File> {
    private String description;

    protected ClassfileMatcher(String description) {
      this.description = description;
    }

    @Override
    public final boolean matches(Object item) {
      try {
        return matches(parse((File) item));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void describeTo(Description description) {
      description.appendText(this.description);
    }

    protected abstract boolean matches(ClassInfo info);
  }

  public static Matcher<File> hasDebugSource() {
    return new ClassfileMatcher("include debug source info") {
      @Override
      protected boolean matches(ClassInfo info) {
        return info.hasSource();
      }
    };
  }

  public static Matcher<File> hasDebugLines() {
    return new ClassfileMatcher("include debug lines info") {
      @Override
      protected boolean matches(ClassInfo info) {
        return info.hasLines();
      }
    };
  }

  public static Matcher<File> hasDebugVars() {
    return new ClassfileMatcher("include debug lines info") {
      @Override
      protected boolean matches(ClassInfo info) {
        return info.hasVars();
      }
    };
  }
}
