/**
 * Copyright (c) 2014 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.resources;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.Incremental;
import io.takari.incrementalbuild.Incremental.Configuration;
import io.takari.maven.plugins.TakariLifecycleMojo;
import io.takari.resources.filtering.MissingPropertyAction;
import io.takari.resources.filtering.ResourcesProcessor;

public abstract class AbstractProcessResourcesMojo extends TakariLifecycleMojo {

  /**
   * Sets what should be the outcome when filtering hits a missing property.
   * <p>
   * Allowed values are:
   * </p>
   * <ul>
   * <li><code>empty</code> - The filtered value will be empty string (default).</li>
   * <li><code>leave</code> - The filtered value will be left as-is, unfiltered (basically the expression itself, mimics maven-resources-plugin).</li>
   * <li><code>fail</code> - Missing property will be reported as error and fails the build.</li>
   * </ul>
   *
   * @since 1.13.4
   */
  @Parameter
  protected MissingPropertyAction missingPropertyAction = MissingPropertyAction.empty;

  @Parameter(defaultValue = "${project.properties}")
  @Incremental(configuration = Configuration.ignore)
  private Properties properties;

  @Parameter(defaultValue = "${session.executionProperties}")
  @Incremental(configuration = Configuration.ignore)
  private Properties sessionProperties;

  //
  // use explicit reflective properties instead of wider objects like MavenSession or Settings
  // this way resources will be properly reprocessed whenever the properties change
  //

  // oddly, ${localRepository} did not work
  @Parameter(defaultValue = "${settings.localRepository}")
  @Incremental(configuration = Configuration.ignore)
  private File localRepository;

  @Parameter(defaultValue = "${session.request.userSettingsFile}")
  @Incremental(configuration = Configuration.ignore)
  private File userSettingsFile;

  @Parameter(property = "encoding", defaultValue = "${project.build.sourceEncoding}")
  private String encoding;

  @Component
  private ResourcesProcessor processor;

  @Component
  private BuildContext context;

  protected void process(List<Resource> resources, File outputDirectory) throws MojoExecutionException {
    for (Resource resource : resources) {
      boolean filter = Boolean.parseBoolean(resource.getFiltering());
      try {
        File sourceDirectory = new File(resource.getDirectory());
        // Ensure the sourceDirectory is actually present before attempting to process any resources
        if (!sourceDirectory.exists()) {
          continue;
        }
        sourceDirectory = sourceDirectory.getCanonicalFile();
        File targetDirectory;
        if (resource.getTargetPath() != null) {
          targetDirectory = new File(outputDirectory, resource.getTargetPath());
        } else {
          targetDirectory = outputDirectory;
        }
        if (filter) {
          Map<Object, Object> properties = new HashMap<Object, Object>(this.properties);
          properties.putAll(sessionProperties); // command line parameters win over project properties
          properties.put("project", project);
          properties.put("localRepository", localRepository);
          properties.put("userSettingsFile", userSettingsFile);
          List<File> filters = project.getFilters().stream().map(File::new).collect(Collectors.toList());
          processor.process(sourceDirectory, targetDirectory, resource.getIncludes(), resource.getExcludes(), properties, filters, encoding, missingPropertyAction);
        } else {
          processor.process(sourceDirectory, targetDirectory, resource.getIncludes(), resource.getExcludes(), encoding);
        }
      } catch (IOException e) {
        throw new MojoExecutionException(e.getMessage(), e);
      }
    }
  }

  @Override
  protected void skipMojo() throws MojoExecutionException {
    context.markSkipExecution();
  }
}
