package LibNet;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.Arrays;

public class DynamicJavaExecution {

    public static void main(String[] args) throws Exception {
        String className = "HelloWorld";
        File sourceFile = new File(className + ".java");

        // Write initial code that implements Greeter
        writeCodeToFile(sourceFile, """
            public class HelloWorld {
                public void sayHello() {
                    System.out.println("sayHello!!!");
                }
            }
            """);

        // Compile, load, and use the initial version

        {
            Object greeter = compileAndLoadClass(className, sourceFile);
            final var sayHello = Arrays.stream(greeter.getClass().getMethods()).filter((m) -> m.getName().equals("sayHello"))
                    .findFirst().get();
            sayHello.invoke(greeter);
        }

        // Modify code and recompile to get the updated version
        writeCodeToFile(sourceFile, """
            public class HelloWorld {
                public void sayBye() {
                    System.out.println("sayBye!!!");
                }
            }
            """);
        {
            Object greeter = compileAndLoadClass(className, sourceFile);
            final var sayBye = Arrays.stream(greeter.getClass().getMethods()).filter((m) -> m.getName().equals("sayBye"))
                    .findFirst().get();
            sayBye.invoke(greeter);
        }
    }

    private static void writeCodeToFile(File sourceFile, String code) throws Exception {
        try (PrintWriter out = new PrintWriter(new FileWriter(sourceFile))) {
            out.println(code);
        }
    }

    private static Object compileAndLoadClass(String className, File sourceFile) throws Exception {
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

        // Create an instance, cast it to Greeter, and close the ClassLoader
        Object greeter = loadedClass.getDeclaredConstructor().newInstance();
        classLoader.close();  // Close the ClassLoader after loading

        return greeter;  // Return the instance of Greeter
    }
}
