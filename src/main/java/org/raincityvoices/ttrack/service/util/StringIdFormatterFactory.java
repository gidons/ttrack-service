package org.raincityvoices.ttrack.service.util;

import static com.google.common.collect.ImmutableMap.toImmutableMap;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Set;

import org.raincityvoices.ttrack.service.api.SongId;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Parser;
import org.springframework.format.Printer;
import org.springframework.lang.NonNull;

import com.google.common.collect.ImmutableSet;

public class StringIdFormatterFactory implements AnnotationFormatterFactory<StrId> {

    private final Set<Class<?>> CLASSES = ImmutableSet.of(SongId.class);
    // private final Map<Class<?>, Parser<?>> PARSERS = new HashMap<>();
    private final Map<Class<?>, Parser<?>> PARSERS = CLASSES.stream().collect(toImmutableMap(c->c, this::createParser));
    private Printer<?> PRINTER = (id, locale) -> id.toString();

    @Override
    public @NonNull Set<Class<?>> getFieldTypes() {
        return CLASSES;
    }

    @Override
    public @NonNull Parser<?> getParser(@NonNull StrId annotation, @NonNull Class<?> fieldType) {
        return PARSERS.get(fieldType);
    }

    @Override
    public @NonNull Printer<?> getPrinter(@NonNull StrId annotation, @NonNull Class<?> fieldType) {
        return PRINTER;
    }

    private Parser<?> createParser(Class<?> clazz) {
        Constructor<?> constructor;
        try {
            constructor = clazz.getConstructor(String.class);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new RuntimeException("UNEXPECTED EXCEPTION: Unable to find string constructor for " + clazz);
        }
        return (idStr, locale) -> {
            try {
                return constructor.newInstance(idStr);
            } catch(Exception e) {
                throw new RuntimeException("Unable to instantiate StringId class " + clazz + " from string '" + idStr + "'");
            }
        };
    }
}
