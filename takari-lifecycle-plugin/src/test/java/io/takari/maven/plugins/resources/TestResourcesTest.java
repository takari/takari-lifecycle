package io.takari.maven.plugins.resources;

import static io.takari.maven.testing.TestResources.assertFileContents;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.testing.TestResources;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class TestResourcesTest {

    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final IncrementalBuildRule mojos = new IncrementalBuildRule();

    @Test
    public void testResources() throws Exception {
        Path basedir =
                resources.getBasedir("resources/project-with-test-resources").toPath();
        mojos.executeMojo(basedir.toFile(), "process-test-resources");
        Path resource = basedir.resolve("target/test-classes/resource.txt");
        Assert.assertTrue(Files.exists(resource));
        String line = Files.readAllLines(resource, Charset.defaultCharset()).get(0);
        Assert.assertTrue(line.contains("resource.txt"));
    }

    @Test
    public void testResourcesWithTargetPath() throws Exception {
        Path basedir = resources
                .getBasedir("resources/project-with-test-resources-with-target-path")
                .toPath();
        mojos.executeMojo(basedir.toFile(), "process-test-resources");
        Path resource = basedir.resolve("target/test-classes/resources/targetPath/resource.txt");
        Assert.assertTrue(Files.exists(resource));
        String line = Files.readAllLines(resource, Charset.defaultCharset()).get(0);
        Assert.assertTrue(line.contains("resource.txt"));
    }

    @Test
    public void testResourcesWithFiltering() throws Exception {
        Path basedir = resources
                .getBasedir("resources/project-with-test-resources-filtered")
                .toPath();
        mojos.executeMojo(basedir.toFile(), "process-test-resources");
        assertFileContents(basedir.toFile(), "expected-resource.txt", "target/test-classes/resource.txt");
    }
}
