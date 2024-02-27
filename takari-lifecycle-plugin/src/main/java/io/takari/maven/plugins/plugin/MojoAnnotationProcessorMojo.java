/*
 * Copyright (c) 2014-2024 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.plugin;

import io.takari.maven.plugins.compile.CompileMojo;
import io.takari.maven.plugins.compile.jdt.CompilerJdt;
import java.io.File;
import java.util.Collections;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(
        name = "mojo-annotation-processor",
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        configurator = "takari")
public class MojoAnnotationProcessorMojo extends CompileMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        this.proc = Proc.only;
        if (this.sourcepath == null) {
            this.sourcepath = Sourcepath.disable; // assume all dependencies have been already compiled
        }
        this.compilerId = CompilerJdt.ID;
        this.annotationProcessors = new String[] {MojoDescriptorGleaner.class.getName()};

        super.execute();
    }

    @Override
    protected List<File> getProcessorpath() {
        return Collections.emptyList();
    }
}
