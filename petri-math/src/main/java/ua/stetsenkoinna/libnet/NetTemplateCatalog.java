package ua.stetsenkoinna.libnet;

import ua.stetsenkoinna.petriobj.PetriNet;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The net library seen as a catalogue of reusable net templates.
 *
 * <p>A template is a public static {@link NetLibrary} method that returns a
 * {@link PetriNet}; its parameters are the knobs of the net it builds — number of channels,
 * mean service time, the name to give the instance. Instantiating one with real arguments
 * is how a Petri-object model gets several objects that differ only in their parameters.
 *
 * <p>Arguments arrive as text, the way a user types them, and are converted to the method's
 * parameter types here. Methods marked {@link HiddenFromUI} are left out, as everywhere else
 * in the user interface.
 */
public final class NetTemplateCatalog {

    /** Separator between the elements of an array argument, e.g. {@code 0.2, 0.3, 0.5}. */
    private static final String ARRAY_SEPARATOR = ",";

    private NetTemplateCatalog() {}

    /**
     * One parameter of a template.
     *
     * @param name declared parameter name, or {@code argN} when the class carries no
     *        parameter names
     * @param type the type an argument has to be converted to
     */
    public record TemplateParameter(String name, Class<?> type) {}

    /**
     * A net library method that can be instantiated.
     */
    public record Template(String name, List<TemplateParameter> parameters) {

        /**
         * @return the method signature as shown to the user, e.g.
         *         {@code CreateNetSMOwithoutQueue(int numChannel, double timeMean, String name)}
         */
        public String signature() {
            return name + "(" + parameters.stream()
                    .map(parameter -> parameter.type().getSimpleName() + " " + parameter.name())
                    .collect(Collectors.joining(", ")) + ")";
        }

        @Override
        public String toString() {
            return signature();
        }
    }

    /**
     * @return every net template the library offers, sorted by name
     */
    public static List<Template> templates() {
        List<Template> templates = new ArrayList<>();
        for (Method method : NetLibrary.class.getDeclaredMethods()) {
            if (!isTemplate(method)) {
                continue;
            }
            List<TemplateParameter> parameters = new ArrayList<>();
            for (Parameter parameter : method.getParameters()) {
                parameters.add(new TemplateParameter(parameter.getName(), parameter.getType()));
            }
            templates.add(new Template(method.getName(), parameters));
        }
        templates.sort(Comparator.comparing(Template::name, String.CASE_INSENSITIVE_ORDER));
        return templates;
    }

    /**
     * @param name method name to look up
     * @return the template with that name, or {@code null} when the library has none
     */
    public static Template find(String name) {
        return templates().stream()
                .filter(template -> template.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds a net from a template.
     *
     * @param methodName name of the net library method
     * @param arguments the arguments as text, one per declared parameter
     * @return the net the method built
     * @throws IllegalArgumentException if there is no such template, the number of arguments
     *         does not match, or an argument cannot be read as its parameter type
     * @throws Exception whatever the library method itself throws — typically an invalid
     *         net structure or time delay
     */
    public static PetriNet instantiate(String methodName, List<String> arguments) throws Exception {
        Method method = Arrays.stream(NetLibrary.class.getDeclaredMethods())
                .filter(NetTemplateCatalog::isTemplate)
                .filter(candidate -> candidate.getName().equals(methodName))
                .filter(candidate -> candidate.getParameterCount() == arguments.size())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Net library has no template " + methodName + " taking "
                                + arguments.size() + " argument(s)"));

        Class<?>[] types = method.getParameterTypes();
        Object[] values = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            values[i] = convert(arguments.get(i), types[i], method.getParameters()[i].getName());
        }

        try {
            return (PetriNet) method.invoke(null, values);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw failure;
        }
    }

    private static boolean isTemplate(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && Modifier.isStatic(method.getModifiers())
                && method.getReturnType() == PetriNet.class
                && !method.isAnnotationPresent(HiddenFromUI.class);
    }

    /**
     * Reads one textual argument as the type the parameter declares.
     */
    private static Object convert(String argument, Class<?> type, String parameterName) {
        String text = argument == null ? "" : argument.trim();
        try {
            if (type == String.class) {
                return argument == null ? "" : argument;
            }
            if (type == int.class || type == Integer.class) {
                return Integer.parseInt(text);
            }
            if (type == long.class || type == Long.class) {
                return Long.parseLong(text);
            }
            if (type == double.class || type == Double.class) {
                return Double.parseDouble(text);
            }
            if (type == float.class || type == Float.class) {
                return Float.parseFloat(text);
            }
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(text);
            }
            if (type == double[].class) {
                return splitToDoubles(text);
            }
            if (type == int[].class) {
                return splitToInts(text);
            }
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException(
                    "Argument '" + parameterName + "' expects " + type.getSimpleName()
                            + ", got \"" + argument + "\"", malformed);
        }
        throw new IllegalArgumentException(
                "Argument '" + parameterName + "' has unsupported type " + type.getSimpleName());
    }

    private static double[] splitToDoubles(String text) {
        if (text.isEmpty()) {
            return new double[0];
        }
        String[] parts = text.split(ARRAY_SEPARATOR);
        double[] values = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Double.parseDouble(parts[i].trim());
        }
        return values;
    }

    private static int[] splitToInts(String text) {
        if (text.isEmpty()) {
            return new int[0];
        }
        String[] parts = text.split(ARRAY_SEPARATOR);
        int[] values = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            values[i] = Integer.parseInt(parts[i].trim());
        }
        return values;
    }
}
