package io.takari.maven.plugins.compile.jdt;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.StandardJavaFileManager;

import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.apt.dispatch.BaseProcessingEnvImpl;
import org.eclipse.jdt.internal.compiler.apt.model.ElementsImpl;
import org.eclipse.jdt.internal.compiler.apt.model.Factory;
import org.eclipse.jdt.internal.compiler.lookup.Binding;
import org.eclipse.jdt.internal.compiler.lookup.ImportBinding;
import org.eclipse.jdt.internal.compiler.lookup.LookupEnvironment;
import org.eclipse.jdt.internal.compiler.lookup.PackageBinding;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.eclipse.jdt.internal.compiler.lookup.TypeBinding;

import io.takari.maven.plugins.compile.CompilerBuildContext;

// TODO reconcile with BatchProcessingEnvImpl
class ProcessingEnvImpl extends BaseProcessingEnvImpl {

  private Set<Consumer<String>> referencedTypeObservers = new LinkedHashSet<>();

  // TODO this shadows private member of the superclass, not pretty
  private final Factory _factory;

  public ProcessingEnvImpl(CompilerBuildContext context, StandardJavaFileManager fileManager, Map<String, String> processorOptions, Compiler compiler, CompilerJdt incrementalCompiler) {
    this._filer = new FilerImpl(context, fileManager, incrementalCompiler, this);
    this._messager = new MessagerImpl(context, this);
    this._processorOptions = processorOptions != null ? processorOptions : Collections.<String, String>emptyMap();
    this._compiler = compiler;

    this._elementUtils = new ElementsImpl(this) {
      @Override
      public TypeElement getTypeElement(CharSequence name) {
        observeType(name.toString());
        return super.getTypeElement(name);
      }

      @Override
      public PackageElement getPackageElement(CharSequence name) {
        observeType(name.toString());
        return super.getPackageElement(name);
      }
    };

    this._factory = new Factory(this) {
      private void observeType(Binding binding) {
        if (binding instanceof ImportBinding) {
          observeType(((ImportBinding) binding).compoundName);
        } else if (binding instanceof PackageBinding) {
          observeType(((PackageBinding) binding).compoundName);
        } else if (binding instanceof TypeBinding) {
          observeType((TypeBinding) binding);
        }

        // no need to explicitly handle variable references
        // - variable elements can only obtained through enclosing type,
        // which means the enclosing type is already observed
        // - to obtain type of the variable itself, processors need
        // to call Element.asType, which triggers subsequent calls here

        // ditto method references
      }

      private void observeType(TypeBinding binding) {
        binding = binding.leafComponentType().erasure();
        if (binding instanceof ReferenceBinding) {
          ReferenceBinding referenceBinding = (ReferenceBinding) binding;
          observeType(referenceBinding.compoundName);
          // TODO only track referenced member types (requires jdt.apt changes)
          for (ReferenceBinding memberType : referenceBinding.memberTypes()) {
            observeType(memberType.compoundName);
          }
        }
      }

      private void observeType(char[][] compoundName) {
        ProcessingEnvImpl.this.observeType(CharOperation.toString(compoundName));
      }

      @Override
      public Element newElement(Binding binding, ElementKind kindHint) {
        observeType(binding);
        return super.newElement(binding, kindHint);
      }

      @Override
      public TypeMirror newTypeMirror(Binding binding) {
        observeType(binding);
        return super.newTypeMirror(binding);
      }

      // TODO newTypeParameterElement
      // TODO newPackageElement
    };
  }

  private void observeType(String type) {
    referencedTypeObservers.forEach(o -> o.accept(type));
  }

  @Override
  public Locale getLocale() {
    return Locale.getDefault(); // TODO
  }

  @Override
  public Factory getFactory() {
    return _factory;
  }

  public void incrementalIterationReset() {
    reset();
    setErrorRaised(false);

    ((FilerImpl) _filer).incrementalIterationReset();
  }

  public void addReferencedTypeObserver(Consumer<String> observer) {
    referencedTypeObservers.add(observer);
  }

  @Override
  public LookupEnvironment getLookupEnvironment() {
    LookupEnvironment _le = super.getLookupEnvironment();
    return _le;
  }
}
