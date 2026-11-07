/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.resources.filtering;

import io.takari.incrementalbuild.BuildContext;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Named
@Singleton
public class ResourcesProcessor {

    private final BuildContext buildContext;
    private final FilterResourcesProcessor filterProcessor;

    @Inject
    public ResourcesProcessor(BuildContext buildContext) {
        this.buildContext = buildContext;
        this.filterProcessor = new FilterResourcesProcessor(buildContext);
    }

    public void process(
            File sourceDirectory, File targetDirectory, List<String> includes, List<String> excludes, String encoding)
            throws IOException {
        for (Resource<File> input : buildContext.registerAndProcessInputs(sourceDirectory, includes, excludes)) {
            copyResource(input, sourceDirectory, targetDirectory);
        }
    }

    private void copyResource(Resource<File> input, File sourceDirectory, File targetDirectory) throws IOException {
        File outputFile = relativize(sourceDirectory, targetDirectory, input.getResource());
        Output<File> output = input.associateOutput(outputFile);
        try (InputStream is = Files.newInputStream(input.getResource().toPath());
                OutputStream os = output.newOutputStream()) {
            is.transferTo(os);
        }
    }

    static File relativize(File sourceDirectory, File targetDirectory, File sourceFile) {
        String sourceDir = sourceDirectory.getAbsolutePath();
        String source = sourceFile.getAbsolutePath();
        if (!source.startsWith(sourceDir)) {
            throw new IllegalArgumentException(); // can't happen
        }
        String relative = source.substring(sourceDir.length());
        // Path.of(String, String...) joins like new File(File, String); relative starts with a separator
        return Path.of(targetDirectory.getPath(), relative).toFile();
    }

    public void process(
            File sourceDirectory,
            File targetDirectory,
            List<String> includes,
            List<String> excludes,
            Map<Object, Object> filterProperties,
            List<File> filters,
            Charset encoding,
            MissingPropertyAction mpa,
            boolean escapeWindowsPaths)
            throws IOException {
        filterProcessor.process(
                sourceDirectory,
                targetDirectory,
                includes,
                excludes,
                filterProperties,
                filters,
                encoding,
                mpa,
                escapeWindowsPaths);
    }

    public void filter(
            Resource resource, Reader reader, Writer writer, Map<Object, Object> properties, MissingPropertyAction mpa)
            throws IOException {
        filterProcessor.filter(resource, reader, writer, properties, mpa);
    }
}
