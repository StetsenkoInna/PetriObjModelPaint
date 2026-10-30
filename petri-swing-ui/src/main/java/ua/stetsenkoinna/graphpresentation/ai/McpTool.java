package ua.stetsenkoinna.graphpresentation.ai;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a tool at its implementation; discovery and dispatch share this declaration. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpTool {
    String name();
    String description();
    String inputSchema() default "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":false}";
}
