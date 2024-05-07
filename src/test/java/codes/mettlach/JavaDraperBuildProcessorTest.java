package codes.mettlach;

import org.junit.Test;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compilation.Status;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.Assert.assertEquals;
import java.io.File;
import java.net.MalformedURLException;

public class JavaDraperBuildProcessorTest {
    @Test
    public void annotatedClassCompiles() throws MalformedURLException {
        final JavaDraperBuildProcessor processor = new JavaDraperBuildProcessor();
        File presenterSourceFile =
                new File("src/test/java/codes/mettlach/test_src/FooPresenter.java");
        File targetSourceFile = new File("src/test/java/codes/mettlach/test_src/Foo.java");
        Compilation compilation = javac().withProcessors(processor).compile(
                JavaFileObjects.forResource(presenterSourceFile.toURI().toURL()),
                JavaFileObjects.forResource(targetSourceFile.toURI().toURL()));
        assertEquals(Status.SUCCESS, compilation.status());
    }
}
