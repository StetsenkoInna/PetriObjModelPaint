package ua.stetsenkoinna.LibNet;

import ua.stetsenkoinna.PetriObj.PetriNet;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Arrays;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import ua.stetsenkoinna.libnetannotation.annotation.NetLibraryMethod;

public class NetLibraryManager {

    private static final String NET_LIBRARY_DYNAMIC_NAME = NetLibrary.class.getSimpleName().concat("Dynamic");
    private static final String JAVA_CLASSPATH = System.getProperty("java.class.path");

    private final Path netLibDynTempPath = Paths.get(
            Files.createTempDirectory(NET_LIBRARY_DYNAMIC_NAME)
                .resolve(NET_LIBRARY_DYNAMIC_NAME)
                    .toString()
                    .concat(".java")
    );
    private final Path netLibrarySourceFile;
    private Class<?> loadedClass;
    private URLClassLoader classLoader;

    public NetLibraryManager() throws Exception {
        final Properties properties = new Properties();
        properties.load(getClass().getClassLoader().getResourceAsStream("application.properties"));
        final Path source_dir = Paths.get(properties.getProperty("project.basedir"), "src", "main", "java");
        netLibrarySourceFile = Paths.get(
                Arrays.stream(NetLibrary.class.getName().split("[.]"))
                        .reduce(source_dir, Path::resolve, Path::resolve)
                        .toString()
                        .concat(".java")
        );
        updateNetLibraryDynamic(netLibrarySourceFile, netLibDynTempPath);
        compileAndLoadClass();
    }

    public void addMethod(final String methodText) throws Exception {
        writeNewMethod(netLibrarySourceFile, methodText, getMethodNamesStream(loadedClass));
        updateNetLibraryDynamic(netLibrarySourceFile, netLibDynTempPath);
        compileAndLoadClass();
    }

    public static class MethodNotFound extends Exception {
        MethodNotFound(final String methodName) {
            super(methodName);
        }
    }

    public PetriNet callMethod(
            final String methodName
    ) throws MethodNotFound,
            InvocationTargetException,
            IllegalAccessException
    {
        final Optional<Method> method = Arrays.stream(loadedClass.getMethods())
                .filter((m) -> m.getName().equals(methodName)).findFirst();
        if (method.isEmpty()) {
            throw new MethodNotFound("No method with name \"" + methodName + "\" found");
        }
        return (PetriNet) method.get().invoke(null);
    }

    public ArrayList<String> getMethodNamesArrayList() {
        return Arrays.stream(loadedClass.getMethods())
                .filter((m) -> m.isAnnotationPresent(NetLibraryMethod.class))
                .map(Method::getName)
                .sorted()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void compileAndLoadClass() throws Exception {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler.run(null, null, null, "-classpath", JAVA_CLASSPATH, netLibDynTempPath.toString()) != 0) {
            throw new AssertionError("NetLib compilation error");
        }

        if (classLoader != null) {
            classLoader.close();
        }

        classLoader = URLClassLoader.newInstance(new URL[]{netLibDynTempPath.getParent().toUri().toURL()});
        loadedClass = Class.forName(NET_LIBRARY_DYNAMIC_NAME, true, classLoader);
    }

    private static Stream<String> getMethodNamesStream(final Class<?> cls) {
        return Arrays.stream(cls.getMethods()).map(Method::getName);
    }

    private static void updateNetLibraryDynamic(
            final Path netLibrarySourceFile,
            final Path netLibDynTempPath
    ) throws Exception {
        Files.copy(netLibrarySourceFile, netLibDynTempPath, StandardCopyOption.REPLACE_EXISTING);
        final File libNetFile = new File(netLibDynTempPath.toString());
        final JavaParser javaParser = new JavaParser();
        final ParseResult<CompilationUnit> parseResult = javaParser.parse(libNetFile);
        if (!parseResult.isSuccessful()) {
            System.out.println("Failed to parse libNetFile!");
            throw new Exception(parseResult.getProblem(0).getVerboseMessage());
        }

        final CompilationUnit compilationUnit = parseResult.getResult().get();
        compilationUnit.removePackageDeclaration();
        final ClassOrInterfaceDeclaration netLibClass = compilationUnit.getClassByName(NetLibrary.class.getSimpleName())
                .orElseThrow(() -> new Exception(NetLibrary.class.getSimpleName().concat(" class not found!")));
        netLibClass.setName(NET_LIBRARY_DYNAMIC_NAME);

        try (FileWriter writer = new FileWriter(libNetFile)) {
            writer.write(compilationUnit.toString());
        }
    }

    private static void writeNewMethod(
            final Path netLibrarySourceFile,
            final String methodText,
            final Stream<String> methodNames
    ) throws Exception {
        final File libNetFile = new File(netLibrarySourceFile.toString());
        final JavaParser javaParser = new JavaParser();
        final ParseResult<CompilationUnit> parseResult = javaParser.parse(libNetFile);
        if (!parseResult.isSuccessful()) {
            System.out.println("Failed to parse libNetFile!");
            throw new Exception(parseResult.getProblem(0).getVerboseMessage());
        }

        final CompilationUnit compilationUnit = parseResult.getResult().get();
        final ClassOrInterfaceDeclaration netLibClass = compilationUnit.getClassByName(NetLibrary.class.getSimpleName())
                .orElseThrow(() -> new Exception(NetLibrary.class.getSimpleName() + " class not found!"));

        final ParseResult<MethodDeclaration> methodParseResult = javaParser.parseMethodDeclaration(methodText);

        if (!methodParseResult.isSuccessful()) {
            System.out.println("Failed to parse methodDeclaration text!");
            throw new Exception(methodParseResult.getProblem(0).getVerboseMessage());
        }

        final MethodDeclaration methodDeclaration = methodParseResult.getResult().get();
        methodDeclaration.addAnnotation(NetLibraryMethod.class);

        if (methodNames.anyMatch((m) -> m.equals(methodDeclaration.getName().asString()))) {
            throw new Exception("Method with such name already exists");
        }

        netLibClass.addMember(methodDeclaration);

        try (FileWriter writer = new FileWriter(libNetFile)) {
            writer.write(compilationUnit.toString());
        }
    }
}