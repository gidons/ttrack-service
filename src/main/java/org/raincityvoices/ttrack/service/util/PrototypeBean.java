package org.raincityvoices.ttrack.service.util;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
@Retention(RetentionPolicy.RUNTIME)
public @interface PrototypeBean {

}
