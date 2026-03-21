package org.bruneel.thankyouboard.images;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@Profile("!e2e")
@ConditionalOnProperty(value = "images.storage", havingValue = "local", matchIfMissing = true)
public class UploadsWebConfig implements WebMvcConfigurer {

    private final Path root;

    public UploadsWebConfig(@Value("${images.local.root-dir:./.local-uploads}") String rootDir) {
        this.root = Path.of(rootDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(root.toUri().toString());
    }
}

