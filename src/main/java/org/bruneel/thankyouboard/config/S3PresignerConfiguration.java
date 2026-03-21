package org.bruneel.thankyouboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(value = "images.storage", havingValue = "s3")
public class S3PresignerConfiguration {

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(@Value("${images.s3.region:}") String region) {
        S3Presigner.Builder builder = S3Presigner.builder();
        if (region != null && !region.isBlank()) {
            builder.region(Region.of(region.trim()));
        }
        return builder.build();
    }
}
