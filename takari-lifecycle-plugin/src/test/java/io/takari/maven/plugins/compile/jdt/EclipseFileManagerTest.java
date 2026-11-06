package io.takari.maven.plugins.compile.jdt;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import javax.tools.StandardLocation;
import org.eclipse.jdt.internal.compiler.apt.util.EclipseFileManager;
import org.junit.Test;

public class EclipseFileManagerTest {

    @Test
    public void test514121_closeClassloaders() throws Exception {
        EclipseFileManager fileManager = new EclipseFileManager(null, StandardCharsets.UTF_8);
        fileManager.setLocation(
                StandardLocation.ANNOTATION_PROCESSOR_PATH,
                List.of(Path.of("src/test/jars/commons-lang-2.0.jar").toFile()));
        URLClassLoader loader = (URLClassLoader) fileManager.getClassLoader(StandardLocation.ANNOTATION_PROCESSOR_PATH);
        assertNotNull(loader.findResource("META-INF/LICENSE.txt")); // sanity check
        fileManager.close();
        assertNull(loader.findResource("META-INF/LICENSE.txt")); // assert the classloader is closed
    }
}
