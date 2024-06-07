package codes.mettlach.processor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.JavaFileObjects;
import com.google.testing.compile.Compilation.Status;
import static com.google.testing.compile.Compiler.javac;
import static org.junit.Assert.assertEquals;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class JavaDraperBuildProcessorTest {
    private final Path originalFooFilePath = Paths.get("src/test/java/codes/mettlach/processor/test_src/FooPresenter.java");
    private final Path backupFooFilePath = Paths.get("src/test/java/codes/mettlach/processor/test_src/FooPresenter.bak.java");
    @Before
    public final void setUp() throws IOException {
        Files.copy(originalFooFilePath, backupFooFilePath, StandardCopyOption.REPLACE_EXISTING);
    }

    @After
    public final void tearDown() throws IOException {
        Files.deleteIfExists(originalFooFilePath);
        Files.move(backupFooFilePath, originalFooFilePath);
    }

    @Test
    public void annotatedClassCompiles() throws MalformedURLException {
        final JavaDraperBuildProcessor processor = new JavaDraperBuildProcessor();
        File presenterSourceFile =
                new File("src/test/java/codes/mettlach/processor/test_src/FooPresenter.java");
        File targetSourceFile = new File("src/test/java/codes/mettlach/processor/test_src/Foo.java");
        File barSourceFile = new File("src/test/java/codes/mettlach/processor/test_src/Bar.java");
        Compilation compilation = javac().withProcessors(processor).compile(
                JavaFileObjects.forResource(presenterSourceFile.toURI().toURL()),
                JavaFileObjects.forResource(targetSourceFile.toURI().toURL()),
                JavaFileObjects.forResource(barSourceFile.toURI().toURL()));
        assertEquals(Status.SUCCESS, compilation.status());
    }
}
