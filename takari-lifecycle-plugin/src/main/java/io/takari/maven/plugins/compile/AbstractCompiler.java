/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.takari.incrementalbuild.ResourceMetadata;
import io.takari.maven.plugins.compile.AbstractCompileMojo.AccessRulesViolation;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Debug;
import io.takari.maven.plugins.compile.AbstractCompileMojo.Proc;

public abstract class AbstractCompiler {

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected final CompilerBuildContext context;

  private File outputDirectory;

  private String source;

  private String target;

  private Proc proc;

  private File generatedSourcesDirectory;

  private String[] annotationProcessors;

  private boolean verbose;

  private boolean parameters;

  private File pom;

  private Charset sourceEncoding;

  private Map<String, String> annotationProcessorOptions;

  private Set<Debug> debug;

  private boolean showWarnings;

  private AccessRulesViolation transitiveDependencyReference;

  private AccessRulesViolation privatePackageReference;

  protected AbstractCompiler(CompilerBuildContext context) {
    this.context = context;
  }

  public void setOutputDirectory(File outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  protected File getOutputDirectory() {
    return outputDirectory;
  };

  public void setSource(String source) {
    this.source = source;
  }

  protected String getSource() {
    return source;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  protected String getTarget() {
    return target;
  }

  public void setProc(Proc proc) {
    this.proc = proc;
  }

  public void setDebug(Set<Debug> debug) {
    this.debug = debug;
  }

  protected Set<Debug> getDebug() {
    return debug;
  }

  protected Proc getProc() {
    return proc;
  }

  public void setGeneratedSourcesDirectory(File generatedSourcesDirectory) {
    this.generatedSourcesDirectory = generatedSourcesDirectory;
  }

  protected File getGeneratedSourcesDirectory() {
    return generatedSourcesDirectory;
  }

  public void setAnnotationProcessors(String[] annotationProcessors) {
    this.annotationProcessors = annotationProcessors;
  }

  protected String[] getAnnotationProcessors() {
    return annotationProcessors;
  }

  public void setAnnotationProcessorOptions(Map<String, String> annotationProcessorOptions) {
    this.annotationProcessorOptions = annotationProcessorOptions;
  }

  protected Map<String, String> getAnnotationProcessorOptions() {
    return annotationProcessorOptions;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  public void setParameters(boolean parameters) {
    this.parameters = parameters;
  }

  public void setPrivatePackageReference(AccessRulesViolation privatePackageReference) {
    this.privatePackageReference = privatePackageReference;
  }

  public AccessRulesViolation getPrivatePackageReference() {
    return privatePackageReference;
  }

  protected AccessRulesViolation getTransitiveDependencyReference() {
    return transitiveDependencyReference;
  }

  public void setTransitiveDependencyReference(AccessRulesViolation transitiveDependencyReference) {
    this.transitiveDependencyReference = transitiveDependencyReference;
  }

  protected boolean isVerbose() {
    return verbose;
  }

  protected boolean isParameters() {
    return parameters;
  }

  public void setPom(File pom) {
    this.pom = pom;
  }

  protected File getPom() {
    return pom;
  }

  public void setSourceEncoding(Charset sourceEncoding) {
    this.sourceEncoding = sourceEncoding;
  }

  protected Charset getSourceEncoding() {
    return sourceEncoding;
  }

  public void setShowWarnings(boolean showWarnings) {
    this.showWarnings = showWarnings;
  }

  protected boolean isShowWarnings() {
    return showWarnings;
  }

  public abstract boolean setProcessorpath(List<File> processorpath) throws IOException;

  public abstract boolean setClasspath(List<File> dependencies, File mainClasses, Set<File> directDependencies) throws IOException;

  public abstract boolean setSourcepath(List<File> dependencies, Set<File> sourceRoots) throws IOException;

  public abstract boolean setSources(List<ResourceMetadata<File>> sources) throws IOException;

  public abstract int compile() throws MojoExecutionException, IOException;

  public void skipCompile() {
    context.markUptodateExecution();
  }

}
