package LibNet;

import PetriObj.PetriNet;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;

public class NetLibraryManager {

    // Static fields for paths and constant strings
    private static final String LIBRARY_NAME = "NetLibrary";
    private static final String DYNAMIC_LIBRARY_NAME = "NetLibraryDynamic";
    private static final String PACKAGE_NAME = "LibNet";
    private static final Path SOURCE_PATH = Paths.get("src", PACKAGE_NAME, LIBRARY_NAME + ".java");
    private static final Path DESTINATION_PATH = Paths.get(DYNAMIC_LIBRARY_NAME, DYNAMIC_LIBRARY_NAME + ".java");
    private static final String JAVA_CLASSPATH = System.getProperty("java.class.path");
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"));
    private static final Path DESTINATION_DIRECTORY = PROJECT_ROOT.resolve(DYNAMIC_LIBRARY_NAME);

    private final JavaParser javaParser = new JavaParser();
    private Class<?> loadedClass;
    private URLClassLoader classLoader;

    public NetLibraryManager() throws Exception {
        createNetLibraryDynamic();
        compileAndLoadClass();
    }

    public void addMethod(final String methodText) throws Exception {
        writeNewMethod(javaParser, methodText, getMethodNamesStream(loadedClass));
        createNetLibraryDynamic();
        compileAndLoadClass();
    }

    public PetriNet callMethod(final String methodName) throws Exception {
        final var method = Arrays.stream(loadedClass.getMethods())
                .filter((m) -> m.getName().equals(methodName)).findFirst();
        if (method.isEmpty()) {
            throw new Exception("No method with name \"" + methodName + "\" found");
        }
        return (PetriNet) method.get().invoke(null);
    }

    public ArrayList<String> getMethodNamesArrayList() {
        return getMethodNamesStream(loadedClass)
                .filter((name) -> name.startsWith("CreateNet"))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void compileAndLoadClass() throws Exception {
        final var compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler.run(null, null, null, "-classpath", JAVA_CLASSPATH, DESTINATION_PATH.toString()) != 0) {
            throw new AssertionError("NetLib compilation error");
        }

        if (classLoader != null) {
            classLoader.close();
        }

        classLoader = URLClassLoader.newInstance(new URL[]{DESTINATION_DIRECTORY.toUri().toURL()});
        loadedClass = Class.forName(DYNAMIC_LIBRARY_NAME, true, classLoader);
    }

    private static Stream<String> getMethodNamesStream(final Class<?> cls) {
        return Arrays.stream(cls.getMethods()).map(Method::getName);
    }

    private void createNetLibraryDynamic() throws Exception {
        if (Files.exists(DESTINATION_PATH)) {
            Files.delete(DESTINATION_PATH);
            System.out.println("Existing destination file deleted.");
        }

        Files.createDirectories(DESTINATION_PATH.getParent());
        Files.copy(SOURCE_PATH, DESTINATION_PATH, StandardCopyOption.REPLACE_EXISTING);

        final var libNetFile = new File(DESTINATION_PATH.toString());
        final var parseResult = javaParser.parse(libNetFile);
        if (!parseResult.isSuccessful()) {
            System.out.println("Failed to parse libNetFile!");
            throw new Exception(parseResult.getProblem(0).getVerboseMessage());
        }

        final var compilationUnit = parseResult.getResult().get();
        compilationUnit.removePackageDeclaration();
        final var netLibClass = compilationUnit.getClassByName(LIBRARY_NAME)
                .orElseThrow(() -> new Exception(LIBRARY_NAME + " class not found!"));
        netLibClass.setName(DYNAMIC_LIBRARY_NAME);

        try (FileWriter writer = new FileWriter(libNetFile)) {
            writer.write(compilationUnit.toString());
        }
    }

    private static void writeNewMethod(
            final JavaParser javaParser,
            final String methodText,
            final Stream<String> methodNames
    ) throws Exception {

        final var libNetFile = new File(SOURCE_PATH.toString());
        final var parseResult = javaParser.parse(libNetFile);
        if (!parseResult.isSuccessful()) {
            System.out.println("Failed to parse libNetFile!");
            throw new Exception(parseResult.getProblem(0).getVerboseMessage());
        }

        final var compilationUnit = parseResult.getResult().get();
        final var netLibClass = compilationUnit.getClassByName(LIBRARY_NAME)
                .orElseThrow(() -> new Exception(LIBRARY_NAME + " class not found!"));

        final var methodParseResult = javaParser.parseMethodDeclaration(methodText);
        if (!methodParseResult.isSuccessful()) {
            System.out.println("Failed to parse method text!");
            throw new Exception(methodParseResult.getProblem(0).getVerboseMessage());
        }

        final var method = methodParseResult.getResult().get();

        if (methodNames.anyMatch((m) -> m.equals(method.getName().asString()))) {
            throw new Exception("Method with such name already exists");
        }

        netLibClass.addMember(method);

        try (FileWriter writer = new FileWriter(libNetFile)) {
            writer.write(compilationUnit.toString());
        }
    }
}
