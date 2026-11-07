package io.takari.maven.plugins.compile.javac;

import static org.junit.Assert.assertEquals;

import io.takari.incrementalbuild.MessageSeverity;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerOutput;
import io.takari.maven.plugins.compile.javac.CompilerJavacForked.CompilerOutputProcessor;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.Diagnostic.Kind;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CompilerOutputTest {
    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void testRoundTripMultilineLogAndEmptyMessage() throws Exception {
        Path file = temp.newFile().toPath();
        String log = "first line\n  indented\n\nlast";
        CompilerOutput output = new CompilerOutput(file);
        output.addLogMessage(log);
        output.addMessage("/some path/A.java", 1, 2, "", Kind.ERROR);
        output.close();

        List<String> seen = new ArrayList<>();
        CompilerOutput.process(file, new CompilerOutputProcessor() {
            @Override
            public void processOutput(File inputFile, File outputFile) {}

            @Override
            public void addMessage(String path, int line, int column, String message, MessageSeverity kind) {
                seen.add(path + "|" + line + "|" + column + "|" + message + "|" + kind);
            }

            @Override
            public void addLogMessage(String message) {
                seen.add(message);
            }
        });

        assertEquals(List.of(log, "/some path/A.java|1|2||ERROR"), seen);
    }
}
