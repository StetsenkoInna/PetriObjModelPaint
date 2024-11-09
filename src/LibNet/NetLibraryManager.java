package LibNet;


import com.github.javaparser.ParseResult;
import com.github.javaparser.Problem;
import graphpresentation.PetriNetsFrame;

import javax.swing.*;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.Modifier;

public class NetLibraryManager {

    private static final String LIB_NET_NAME = "NetLibrary";
    private static final String LIB_NET_PATH = FileSystems.getDefault()
            .getPath(System.getProperty("user.dir"),"src", "LibNet", "NetLibrary.java")
            .toString();

    private final static String LIB_NET_DEFAULT_BODY =
"""
package LibNet;
import PetriObj.ExceptionInvalidNetStructure;
import PetriObj.PetriNet;
import PetriObj.PetriP;
import PetriObj.PetriT;
import PetriObj.ArcIn;
import PetriObj.ArcOut;
import java.util.ArrayList;
public class NetLibrary {

}
""";


    private final JavaParser javaParser = new JavaParser();
    private Class<?> netLibraryClass;

    public NetLibraryManager() {

    }

    public void addMethod(final String methodText) {

    }

    public void callMethod(final String methodName) {

    }

    public Stream<String> getMethodNames() {
        return Arrays.stream(netLibraryClass.getMethods()).map(Method::getName);
    }



    private static void writeNewMethod(final JavaParser javaParser, final String methodText) throws IOException {
        final File libNetFile = new File(LIB_NET_PATH, "rw");
        final ParseResult<CompilationUnit> parseResult = javaParser.parse(libNetFile);
        if (!parseResult.isSuccessful()) {
            System.out.println("Failed to parse libNetFile!");
            throw new RuntimeException(parseResult.getProblem(0).getVerboseMessage());
        }
        final var compilationUnit = parseResult.getResult().get();
        final ClassOrInterfaceDeclaration netLibClass = compilationUnit.getClassByName(LIB_NET_NAME)
                .orElseThrow(() -> new RuntimeException("NetLibrary class not found!"));
        final var methodParseResult = javaParser.parseMethodDeclaration(methodText);
        if (!methodParseResult.isSuccessful()) {
            System.out.println("Failed to parse method text!");
            throw new RuntimeException(methodParseResult.getProblem(0).getVerboseMessage());
        }
        netLibClass.addMember(methodParseResult.getResult().get());
        FileWriter writer = new FileWriter(libNetFile);
        writer.write(compilationUnit.toString());
        writer.close();
    }

    private static Class<?> compileAndLoadClass(final String className, final File sourceFile) throws Exception {
        // Compile the code with the classpath set to the current runtime
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        String classpath = System.getProperty("java.class.path");
        if (compiler.run(null, null, null, "-classpath", classpath, sourceFile.getPath()) != 0) {
            System.out.println("Compilation failed.");
            return null;
        }

        // Load the compiled class with a new ClassLoader
        URLClassLoader classLoader = URLClassLoader.newInstance(new URL[] { new File("").toURI().toURL() });
        Class<?> loadedClass = Class.forName(className, true, classLoader);

        classLoader.close();

        return loadedClass;
    }
}
