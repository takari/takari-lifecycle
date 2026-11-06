package io.takari.maven.plugins.compile.jdt;

import static io.takari.maven.testing.TestResources.cp;
import static io.takari.maven.testing.TestResources.rm;
import static io.takari.maven.testing.TestResources.touch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.Assert;
import org.junit.Test;

public class CompileJdtTest extends AbstractCompileJdtTest {

    /**
     * Asserts specified output exists and is not older than specified input
     */
    protected static void assertBuildOutput(Path basedir, String input, String output) throws IOException {
        Path inputFile = basedir.resolve(input);
        Path outputFile = basedir.resolve(output);
        Assert.assertTrue(
                "output is older than input",
                Files.getLastModifiedTime(outputFile).compareTo(Files.getLastModifiedTime(inputFile)) >= 0);
    }

    @Test
    public void testBasic() throws Exception {
        Path basedir = resources.getBasedir("compile-jdt/basic").toPath();

        // initial build
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(), "target/classes/basic/Basic1.class", "target/classes/basic/Basic2.class");
        assertBuildOutput(basedir, "src/main/java/basic/Basic1.java", "target/classes/basic/Basic1.class");
        assertBuildOutput(basedir, "src/main/java/basic/Basic2.java", "target/classes/basic/Basic2.class");

        // no-change rebuild
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(basedir.toFile(), new String[0]);
        mojos.assertCarriedOverOutputs(
                basedir.toFile(), "target/classes/basic/Basic1.class", "target/classes/basic/Basic2.class");

        // one file changed
        cp(basedir.toFile(), "src/main/java/basic/Basic1.java-changed", "src/main/java/basic/Basic1.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/basic/Basic1.class");
        assertBuildOutput(basedir, "src/main/java/basic/Basic1.java", "target/classes/basic/Basic1.class");
    }

    @Test
    public void testBasic_timestampChangeRebuild() throws Exception {
        Path basedir = resources.getBasedir("compile-jdt/basic").toPath();
        Path classes = basedir.resolve("target/classes");

        // initial build
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(classes.toFile(), "basic/Basic1.class", "basic/Basic2.class");

        // move back timestamp, round to 10s to accommodate filesystem timestamp rounding
        long timestamp = System.currentTimeMillis() - 20000L;
        timestamp = timestamp - (timestamp % 10000L);
        Files.setLastModifiedTime(classes.resolve("basic/Basic1.class"), FileTime.fromMillis(timestamp));

        touch(basedir.toFile(), "src/main/java/basic/Basic1.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(classes.toFile(), "basic/Basic1.class");
        // timestamp is the same if the file is the same
        Assert.assertEquals(
                timestamp,
                Files.getLastModifiedTime(classes.resolve("basic/Basic1.class")).toMillis());
    }

    @Test
    public void testSupertype() throws Exception {
        Path basedir = resources.getBasedir("compile-jdt/supertype").toPath();

        // initial build
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(),
                "target/classes/supertype/SubClass.class",
                "target/classes/supertype/SuperClass.class",
                "target/classes/supertype/SuperInterface.class");

        // non-structural change
        cp(
                basedir.toFile(),
                "src/main/java/supertype/SuperClass.java-comment",
                "src/main/java/supertype/SuperClass.java");
        cp(
                basedir.toFile(),
                "src/main/java/supertype/SuperInterface.java-comment",
                "src/main/java/supertype/SuperInterface.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(),
                "target/classes/supertype/SuperClass.class",
                "target/classes/supertype/SuperInterface.class");

        // superclass change
        cp(
                basedir.toFile(),
                "src/main/java/supertype/SuperClass.java-member",
                "src/main/java/supertype/SuperClass.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(),
                "target/classes/supertype/SubClass.class",
                "target/classes/supertype/SuperClass.class");
    }

    @Test
    public void testSupertype_superClassChangeDoesNotTriggerRebuildOfImplementedInterfaces() throws Exception {
        Path basedir = resources.getBasedir("compile-jdt/supertype").toPath();

        // initial build
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(),
                "target/classes/supertype/SubClass.class",
                "target/classes/supertype/SuperClass.class",
                "target/classes/supertype/SuperInterface.class");

        // superclass insignificant change
        cp(
                basedir.toFile(),
                "src/main/java/supertype/SuperClass.java-methodBody",
                "src/main/java/supertype/SuperClass.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/supertype/SuperClass.class");

        // superclass significant change
        cp(
                basedir.toFile(),
                "src/main/java/supertype/SuperClass.java-member",
                "src/main/java/supertype/SuperClass.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(),
                "target/classes/supertype/SubClass.class",
                "target/classes/supertype/SuperClass.class");
    }

    @Test
    public void testCircular() throws Exception {
        Path basedir =
                mojos.compile(resources.getBasedir("compile-jdt/circular")).toPath();
        mojos.assertBuildOutputs(
                basedir.toFile(), "target/classes/circular/ClassA.class", "target/classes/circular/ClassB.class");

        cp(basedir.toFile(), "src/main/java/circular/ClassA.java-changed", "src/main/java/circular/ClassA.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(), "target/classes/circular/ClassA.class", "target/classes/circular/ClassB.class");
    }

    @Test
    public void testReference() throws Exception {
        Path basedir =
                mojos.compile(resources.getBasedir("compile-jdt/reference")).toPath();
        mojos.assertBuildOutputs(
                basedir.toFile(), "target/classes/reference/Parameter.class", "target/classes/reference/Type.class");

        // no change rebuild
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(basedir.toFile(), new String[0]);
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/classes/reference/Parameter.class")));
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/classes/reference/Type.class")));

        // insignificant change
        cp(
                basedir.toFile(),
                "src/main/java/reference/Parameter.java-comment",
                "src/main/java/reference/Parameter.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/reference/Parameter.class");
        Assert.assertTrue(Files.isReadable(basedir.resolve("target/classes/reference/Type.class")));

        // significant change
        cp(basedir.toFile(), "src/main/java/reference/Parameter.java-method", "src/main/java/reference/Parameter.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(), "target/classes/reference/Parameter.class", "target/classes/reference/Type.class");
    }

    @Test
    public void testMissing() throws Exception {
        final String[] messages = {
            "ERROR Error.java [6:12] Missing cannot be resolved to a type",
            "ERROR Error.java [8:20] Missing cannot be resolved to a type"
        };

        Path basedir = resources.getBasedir("compile-jdt/missing").toPath();
        try {
            mojos.compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException expected) {
            // expected
        }
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/missing/Other.class");
        Assert.assertFalse(Files.exists(basedir.resolve("target/classes/missing/Error.class")));
        mojos.assertMessages(basedir.toFile(), "src/main/java/missing/Error.java", messages);

        // no change rebuild
        try {
            mojos.compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException expected) {
            // expected
        }
        mojos.assertBuildOutputs(basedir.toFile(), new String[0]);
        mojos.assertMessages(basedir.toFile(), "src/main/java/missing/Error.java", messages);

        // fix the problem
        cp(basedir.toFile(), "src/main/java/missing/Missing.java-missing", "src/main/java/missing/Missing.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                basedir.toFile(), "target/classes/missing/Error.class", "target/classes/missing/Missing.class");

        // reintroduce the problem
        rm(basedir.toFile(), "src/main/java/missing/Missing.java");
        try {
            mojos.compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException expected) {
            // expected
        }
        mojos.assertDeletedOutputs(
                basedir.toFile(), "target/classes/missing/Error.class", "target/classes/missing/Missing.class");
    }

    @Test
    public void testMultifile() throws Exception {
        Path basedir =
                mojos.compile(resources.getBasedir("compile-jdt/multifile")).toPath();
        mojos.assertBuildOutputs(
                basedir.toFile(),
                "target/classes/multifile/ClassA.class",
                "target/classes/multifile/ClassB.class",
                "target/classes/multifile/ClassB$Nested.class");

        touch(basedir.toFile(), "src/main/java/multifile/ClassA.java");
        cp(basedir.toFile(), "src/main/java/multifile/ClassB.java-changed", "src/main/java/multifile/ClassB.java");
        try {
            mojos.compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException expected) {
            // expected
        }
        // TODO assert expected error messages
        mojos.assertBuildOutputs(basedir.toFile(), "target/classes/multifile/ClassB.class");
        mojos.assertDeletedOutputs(
                basedir.toFile(),
                "target/classes/multifile/ClassA.class",
                "target/classes/multifile/ClassB$Nested.class");
    }

    @Test
    public void testSecondaryType() throws Exception {
        Path basedir = mojos.compile(resources.getBasedir("compile-jdt/secondary-type"))
                .toPath();
        Path classes = basedir.resolve("target/classes");
        mojos.assertBuildOutputs(
                classes.toFile() //
                ,
                "secondary/Primary.class" //
                ,
                "secondary/Secondary.class" //
                ,
                "secondary/SecondarySubclass.class" //
                ,
                "secondary/SecondarySubclassClient.class");

        touch(basedir.toFile(), "src/main/java/secondary/SecondarySubclassClient.java");
        mojos.compile(basedir.toFile());
        mojos.assertBuildOutputs(
                classes.toFile() //
                ,
                "secondary/SecondarySubclassClient.class");
    }

    @Test
    public void testRemovedTypes() throws Exception {
        Path basedir =
                mojos.compile(resources.getBasedir("compile-jdt/removed-types")).toPath();
        Path classes = basedir.resolve("target/classes");
        mojos.assertBuildOutputs(
                classes.toFile() //
                ,
                "removed/A.class" //
                ,
                "removed/B.class" //
                ,
                "removed/Dummy.class");

        rm(basedir.toFile(), "src/main/java/removed/A.java");
        rm(basedir.toFile(), "src/main/java/removed/B.java");
        mojos.compile(basedir.toFile());
        mojos.assertDeletedOutputs(
                classes.toFile() //
                ,
                "removed/A.class" //
                ,
                "removed/B.class");
    }

    @Test
    public void testRemoveSecondaryType() throws Exception {
        Path basedir = mojos.compile(resources.getBasedir("compile-jdt/remove-secondary-type"))
                .toPath();
        Path classes = basedir.resolve("target/classes");
        mojos.assertBuildOutputs(
                classes.toFile() //
                ,
                "secondary/A.class" //
                ,
                "secondary/B.class" //
                ,
                "secondary/ASecondary.class");

        cp(basedir.toFile(), "src/main/java/secondary/A.java-changed", "src/main/java/secondary/A.java");
        try {
            mojos.compile(basedir.toFile());
            Assert.fail();
        } catch (MojoExecutionException expected) {
            // TODO assert error message
        }
        mojos.assertBuildOutputs(
                classes.toFile() //
                ,
                "secondary/A.class");
        mojos.assertDeletedOutputs(
                classes.toFile() //
                ,
                "secondary/B.class" //
                ,
                "secondary/ASecondary.class");
    }
}
