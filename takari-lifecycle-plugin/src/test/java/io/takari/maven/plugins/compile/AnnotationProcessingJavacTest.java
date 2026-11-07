package io.takari.maven.plugins.compile;

import java.util.List;
import org.junit.runners.Parameterized.Parameters;

public class AnnotationProcessingJavacTest extends AnnotationProcessingTest {

    public AnnotationProcessingJavacTest(String compilerId) {
        super(compilerId);
    }

    @Parameters(name = "{0}")
    public static Iterable<Object[]> compilers() {
        return List.<Object[]>of(new Object[] {"javac"});
    }
}
