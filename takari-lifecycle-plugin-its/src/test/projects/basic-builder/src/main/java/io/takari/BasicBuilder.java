package io.takari;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.lang.model.element.Modifier;

import io.takari.builder.Builder;
import io.takari.builder.BuilderContext;
import io.takari.builder.GeneratedSourcesDirectory;
import io.takari.builder.InputDirectoryFiles;
import io.takari.builder.LifecyclePhase;
import io.takari.builder.OutputFile;
import io.takari.builder.Parameter;

public class BasicBuilder {
  
  @Parameter(required=true)
  private String inputText;
  
  @OutputFile
  private Path outputFile;
  
  /**
   * 
   * @throws Exception
   */
  @Builder(name="build-enum", defaultPhase=LifecyclePhase.GENERATE_SOURCES)
  public void execute () throws Exception {
    Files.write(outputFile, inputText.getBytes());
  }
}
