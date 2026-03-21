package org.bruneel.thankyouboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableResilientMethods
public class AppConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        log.debug("API versioning: version required in Accept, supported versions: 1");
        configurer
                .useMediaTypeParameter(MediaType.APPLICATION_JSON, "version")
                .useMediaTypeParameter(MediaType.APPLICATION_PDF, "version")
                .setVersionRequired(true)
                .addSupportedVersions("1");
    }
}
