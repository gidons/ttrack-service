package org.raincityvoices.ttrack.service.storage.mapper;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface RowKey {
    String value() default "";
}
