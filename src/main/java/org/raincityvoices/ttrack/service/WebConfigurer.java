package org.raincityvoices.ttrack.service;

import org.raincityvoices.ttrack.service.util.StringIdFormatterFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class WebConfigurer implements WebMvcConfigurer {

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        // log.info("Adding formatters");
        // registry.addFormatterForFieldAnnotation(new StringIdFormatterFactory());
    }
}
