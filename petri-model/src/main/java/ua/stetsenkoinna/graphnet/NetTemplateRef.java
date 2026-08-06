package ua.stetsenkoinna.graphnet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A reference to the net library method a Petri-object was instantiated from, together with
 * the arguments it was called with.
 *
 * <p>The net itself is always stored in full, so a model opens even when the library method
 * has been renamed or removed. What this reference adds is provenance: it says that this
 * object is the {@code CreateNetSMOwithoutQueue(2, 0.5, "First")} instance, which lets the
 * editor show the template name and re-instantiate the object with different arguments.
 *
 * <p>Arguments are kept as text exactly as the user typed them; the conversion to the
 * method's parameter types happens when the library is called.
 */
public final class NetTemplateRef implements Serializable {

    private final String methodName;
    private final List<String> arguments;

    /**
     * @param methodName name of the net library method
     * @param arguments the arguments the method was called with, in declaration order
     */
    public NetTemplateRef(String methodName, List<String> arguments) {
        this.methodName = Objects.requireNonNull(methodName, "methodName");
        this.arguments = List.copyOf(arguments == null ? Collections.emptyList() : arguments);
    }

    /**
     * @param methodName name of a net library method taking no arguments
     */
    public NetTemplateRef(String methodName) {
        this(methodName, Collections.emptyList());
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * @return the arguments as text, in declaration order; never {@code null}
     */
    public List<String> getArguments() {
        return arguments;
    }

    /**
     * @return a mutable copy of the arguments, for dialogs that let the user edit them
     */
    public List<String> copyArguments() {
        return new ArrayList<>(arguments);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NetTemplateRef other)) {
            return false;
        }
        return methodName.equals(other.methodName) && arguments.equals(other.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodName, arguments);
    }

    @Override
    public String toString() {
        return methodName + "(" + String.join(", ", arguments) + ")";
    }
}
