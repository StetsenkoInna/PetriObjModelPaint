package graphpresentation;

/**
 * A class loader to load NetLibrary.java. Becuase we have to recompile and reload that class
 * each time a new net is added to the library, we need to create a new instance of a class loader
 * each time. See https://jenkov.com/tutorials/java-reflection/dynamic-class-loading-reloading.html
 * @author Leonid
 */
public class NetLibraryClassLoader extends ClassLoader {
    
    public NetLibraryClassLoader(ClassLoader parent) {
        super(parent);
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        return super.loadClass(name);
    }
    
}
