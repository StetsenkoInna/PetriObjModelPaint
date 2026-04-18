package ua.stetsenkoinna.LibNet;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that should be hidden from the UI.
 * Methods marked with this annotation will not appear in the method selection dropdown.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HiddenFromUI {
    /**
     * Optional reason why the method is hidden
     * @return the reason for hiding this method
     */
    String value() default "Hidden from UI";
}