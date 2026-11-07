/*
 * Copyright (c) 2014-2026 Takari, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 */
package io.takari.maven.plugins.compile.javac;

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.incrementalbuild.Output;
import io.takari.incrementalbuild.Resource;
import io.takari.maven.plugins.compile.CompilerBuildContext;
import io.takari.maven.plugins.compile.ProjectClasspathDigester;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerConfiguration;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerOutput;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerOutputProcessor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;

@Named(CompilerJavacLauncher.ID)
public class CompilerJavacLauncher extends AbstractCompilerJavac {

    public static final String ID = "forked-javac";

    private File jar;

    private File basedir;

    private File buildDirectory;

    private String meminitial;

    private String maxmem;

    @Inject
    public CompilerJavacLauncher(CompilerBuildContext context, ProjectClasspathDigester digester) {
        super(context, digester);
    }

    @Override
    public int compile(Map<File, Resource<File>> sources) throws IOException {
        if (sources.isEmpty()) {
            return 0;
        }

        Path options = Files.createTempFile(buildDirectory.toPath(), "javac-forked", ".options");
        Path output = Files.createTempFile(buildDirectory.toPath(), "javac-forked", ".output");
        compile(options, output, sources);
        // don't delete temp files in case of an exception
        // they maybe useful to debug the problem
        Files.deleteIfExists(options);
        Files.deleteIfExists(output);

        return sources.size();
    }

    private void compile(Path options, Path output, final Map<File, Resource<File>> sources) throws IOException {
        new CompilerConfiguration(getSourceEncoding(), getCompilerOptions(), sources.keySet()).write(options);

        // use the same JVM as the one used to run Maven (the "java.home" one)
        String executable = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        if (File.separatorChar == '\\') {
            executable = executable + ".exe";
        }

        List<String> cli = new ArrayList<>();
        cli.add(executable);

        // jvm options
        cli.add("-cp");
        cli.add(jar.getAbsolutePath());
        if (meminitial != null) {
            cli.add("-Xms" + meminitial);
        }
        if (maxmem != null) {
            cli.add("-Xmx" + maxmem);
        }

        // main class and program arguments
        cli.add(CompilerJavacForked.class.getName());
        cli.add(options.toAbsolutePath().toString());
        cli.add(output.toAbsolutePath().toString());

        log.debug("External java process command line:\n   {}", cli);
        Process process = new ProcessBuilder(cli).directory(basedir).start();
        // best effort to avoid orphaned child process
        Thread destroyer = new Thread(process::destroy);
        Runtime.getRuntime().addShutdownHook(destroyer);
        int exitCode;
        try {
            process.getOutputStream().close();
            // pump child output to this JVM's System.out/err, like commons-exec PumpStreamHandler did
            Thread stderrPump = new Thread(() -> pump(process.getErrorStream(), System.err));
            stderrPump.start();
            pump(process.getInputStream(), System.out);
            exitCode = process.waitFor();
            stderrPump.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new InterruptedIOException("Interrupted while waiting for external java process");
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(destroyer);
            } catch (IllegalStateException e) {
                // JVM is already shutting down
            }
        }
        if (exitCode != 0) {
            if (!log.isDebugEnabled()) {
                log.info("External java process command line:\n   {}", cli);
            }
            throw new IOException("Process exited with an error: " + exitCode);
        }

        final Map<File, Output<File>> outputs = new HashMap<File, Output<File>>();

        CompilerOutput.process(output, new CompilerOutputProcessor() {
            @Override
            public void processOutput(File inputFile, File outputFile) {
                outputs.put(outputFile, context.processOutput(outputFile));
            }

            @Override
            public void addMessage(String path, int line, int column, String message, MessageSeverity kind) {
                if (".".equals(path)) {
                    context.addPomMessage(message, kind, null);
                } else {
                    File file = Path.of(path).toFile();
                    Resource<File> resource = sources.get(file);
                    if (resource == null) {
                        resource = outputs.get(file);
                    }
                    if (resource != null) {
                        if (isShowWarnings() || kind != MessageSeverity.WARNING) {
                            resource.addMessage(line, column, message, kind, null);
                        }
                    } else {
                        log.warn("Unexpected java resource {}", file);
                    }
                }
            }

            @Override
            public void addLogMessage(String message) {
                log.warn(message);
            }
        });
    }

    private static void pump(InputStream in, OutputStream out) {
        try (in) {
            in.transferTo(out);
            out.flush();
        } catch (IOException e) {
            // child went away; nothing more to copy
        }
    }

    public void setBasedir(File basedir) {
        this.basedir = basedir;
    }

    public void setJar(File jar) {
        this.jar = jar;
    }

    public void setBuildDirectory(File buildDirectory) {
        this.buildDirectory = buildDirectory;
    }

    public void setMeminitial(String meminitial) {
        this.meminitial = meminitial;
    }

    public void setMaxmem(String maxmem) {
        this.maxmem = maxmem;
    }

    @Override
    protected String getCompilerId() {
        return ID;
    }
}
