package io.takari.maven.plugins.plugin;

import static io.takari.maven.plugins.plugin.PluginDescriptorMojo.PATH_MOJOS_XML;
import static io.takari.maven.testing.TestMavenRuntime.newParameter;

import io.takari.incrementalbuild.maven.testing.IncrementalBuildRule;
import io.takari.maven.plugins.plugin.model.MojoDescriptor;
import io.takari.maven.plugins.plugin.model.MojoParameter;
import io.takari.maven.plugins.plugin.model.MojoRequirement;
import io.takari.maven.plugins.plugin.model.PluginDescriptor;
import io.takari.maven.plugins.plugin.model.io.xpp3.PluginDescriptorXpp3Reader;
import io.takari.maven.testing.TestProperties;
import io.takari.maven.testing.TestResources;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class MojoDescriptorGleanerTest {
    @Rule
    public final TestResources resources = new TestResources();

    @Rule
    public final IncrementalBuildRule mojos = new IncrementalBuildRule();

    public final TestProperties properties = new TestProperties();

    @Test
    public void testGleaner() throws Exception {
        Path basedir = resources.getBasedir("plugin-descriptor/gleaner").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        addDependency(project, "apache-plugin-annotations-jar");
        mojos.executeMojo(project, "compile", newParameter("compilerId", "jdt"));
        mojos.executeMojo(project, "mojo-annotation-processor");

        MojoDescriptor d = readDescriptor(basedir, "glean_RequirementTypes");

        Assert.assertEquals("java.util.Map", getRequirement(d, "mapRequirement").getRole());
        Assert.assertEquals(
                "java.util.List", getRequirement(d, "listRequirement").getRole());
        Assert.assertEquals("java.util.List", getParameter(d, "listParameter").getType());
        Assert.assertEquals("boolean", getParameter(d, "booleanParameter").getType());
        Assert.assertEquals(
                "java.lang.String[]", getParameter(d, "arrayParameter").getType());

        Assert.assertFalse(d.isTakariBuilder());

        // nested types use binary name so they can be loaded at runtime
        Assert.assertEquals(
                "io.takari.lifecycle.uts.plugindescriptor.glean_RequirementTypes$Nested",
                getParameter(d, "nestedParameter").getType());
        Assert.assertEquals(
                "io.takari.lifecycle.uts.plugindescriptor.glean_RequirementTypes$Nested",
                getRequirement(d, "nestedRoleRequirement").getRole());

        // type variable parameter is erased to its bound
        MojoDescriptor generic = readDescriptor(basedir, "glean_Generic");
        Assert.assertEquals(
                "java.lang.Object", getParameter(generic, "genericParameter").getType());
    }

    @Test
    public void testGleanerParameterAndComponent() throws Exception {
        Path basedir = resources.getBasedir("plugin-descriptor/gleaner").toPath();
        Files.writeString(
                basedir.resolve("src/main/java/io/takari/lifecycle/uts/plugindescriptor/glean_Both.java"),
                "package io.takari.lifecycle.uts.plugindescriptor;\n"
                        + "public class glean_Both {\n"
                        + "  @org.apache.maven.plugins.annotations.Parameter\n"
                        + "  @org.apache.maven.plugins.annotations.Component\n"
                        + "  private String both;\n"
                        + "}\n");
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        addDependency(project, "apache-plugin-annotations-jar");
        mojos.executeMojo(project, "compile", newParameter("compilerId", "jdt"));
        try {
            mojos.executeMojo(project, "mojo-annotation-processor");
            Assert.fail();
        } catch (MojoExecutionException expected) {
            // expected
        }
        mojos.assertMessages(
                basedir.toFile(),
                "src/main/java/io/takari/lifecycle/uts/plugindescriptor/glean_Both.java",
                "ERROR glean_Both.java [5:18] @Parameter and @Component are mutually exclusive");
    }

    @Test
    public void testGleanerForBuilder() throws Exception {
        Path basedir = resources.getBasedir("plugin-descriptor/basic-builder").toPath();
        MavenProject project = mojos.readMavenProject(basedir.toFile());
        addDependency(project, "apache-plugin-annotations-jar");
        addDependency(project, "maven-plugin-api-jar");
        addDependency(project, "takari-builder-jar");
        mojos.executeMojo(project, "compile", newParameter("compilerId", "jdt"));
        mojos.executeMojo(project, "mojo-annotation-processor");

        MojoDescriptor d = readDescriptor(basedir, "BasicBuilder");

        Assert.assertTrue(d.isTakariBuilder());
    }

    private MojoParameter getParameter(MojoDescriptor descriptor, String fieldName) {
        for (MojoParameter parameter : descriptor.getParameters()) {
            if (fieldName.equals(parameter.getName())) {
                return parameter;
            }
        }
        throw new AssertionError("No such parameter " + fieldName);
    }

    private MojoRequirement getRequirement(MojoDescriptor descriptor, String fieldName) {
        for (MojoRequirement requirement : descriptor.getRequirements()) {
            if (fieldName.equals(requirement.getFieldName())) {
                return requirement;
            }
        }
        throw new AssertionError("No such requirement " + fieldName);
    }

    private MojoDescriptor readDescriptor(Path basedir, String classname) throws IOException, XmlPullParserException {
        try (InputStream is = Files.newInputStream(basedir.resolve("target/classes/" + PATH_MOJOS_XML))) {
            PluginDescriptor descriptor = new PluginDescriptorXpp3Reader().read(is);
            return descriptor.getMojos().stream()
                    .filter(m -> m.getImplementation().endsWith("." + classname))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No such mojo " + classname));
        }
    }

    private void addDependency(MavenProject project, String property) throws Exception {
        mojos.newDependency(Path.of(properties.get(property)).toFile()).addTo(project);
    }
}
