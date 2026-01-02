package org.raincityvoices.ttrack.service.storage.mapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation for marking a composite property that is mapped to any number
 * of table entity properties. Any properties of the annotated property's type
 * are mapped recursively.
 * 
 * For example, consider the following classes:
 * <pre>
 * public class A {
 *     @RowKey
 *     String rowKey;
 *     int someInt;
 *     @Embedded
 *     B someB;    
 * }
 * 
 * public class B {
 *     String someString;
 *     @Property("SomeNumber")
 *     double someDouble;
 * }
 * </pre>
 * 
 * An object someA of type A will be mapped as follows:
 * <ul>
 * <li>{@code someA.rowKey} => {@literal RowKey}
 * <li>{@code someA.someInt} => {@literal SomeInt}
 * <li>{@code someA.someB.someString} => {@literal SomeString}
 * <li>{@code someA.someB.someDouble} => {@literal SomeNumber}
 * </ul>
 * 
 * If {@code typePolicy == STATIC_ONLY} (the default), the embedded property
 * will be mapped using its static type: only the static type's properties will be
 * written, and the static type will be instantiated on read.
 * 
 * If {@code typePolicy == CLASSNAME_ATTRIBUTE}, the embedded property
 * will be mapped using its dynamic (runtime) type, and the dynamic class name
 * will be written to the property {@literal "PropName.class"} (where
 * {@literal "propName"} is the name of the embedded POJO property.)
 * This class name will be used to reconstruct the object on read.
 * 
 * TODO add more flexible TypePolicy options.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Embedded {

    public enum TypePolicy {
        STATIC_ONLY,
        CLASSNAME_ATTRIBUTE
    }

    TypePolicy typePolicy() default TypePolicy.STATIC_ONLY;

}
