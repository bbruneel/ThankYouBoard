package org.bruneel.infra;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.assertions.Template;

/**
 * Unit tests for the serverless CDK stack using the CDK assertions library.
 * Synthesizes the stack and asserts on the generated CloudFormation template.
 * <p>
 * Requires the Lambda JAR to exist (run {@code cd functions && mvn package} first);
 * otherwise tests are skipped.
 */
class ServerlessStackTest {

    @Test
    void stackSynthesizesAndCloudFrontOriginDomainHasNoColon() {
        File lambdaJar = new File("../functions/target/lambda-handler.jar");
        assumeTrue(lambdaJar.isFile(),
                "Lambda JAR not found. Run: cd functions && mvn package -q");

        App app = new App();
        EnvironmentConfig config = EnvironmentConfig.forEnv("test", "test.auth0.com", "urn:test-api", "");
        StackProps props = StackProps.builder()
                .env(Environment.builder()
                        .account("123456789012")
                        .region("eu-west-1")
                        .build())
                .build();

        ServerlessStack stack = new ServerlessStack(app, config.prefix() + "-serverless", props, config);

        Template template = Template.fromStack(stack);

        template.resourceCountIs("AWS::CloudFront::Distribution", 1);

        // CloudFront origin names cannot contain a colon (e.g. "https://" would fail at deploy).
        // Assert no origin uses a literal domain string containing ":".
        Map<String, Map<String, Object>> distributions = template.findResources("AWS::CloudFront::Distribution");
        for (Map<String, Object> resource : distributions.values()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propsMap = (Map<String, Object>) resource.get("Properties");
            assertNotNull(propsMap);
            @SuppressWarnings("unchecked")
            Map<String, Object> distConfig = (Map<String, Object>) propsMap.get("DistributionConfig");
            assertNotNull(distConfig);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> origins = (List<Map<String, Object>>) distConfig.get("Origins");
            assertNotNull(origins);
            for (Map<String, Object> origin : origins) {
                Object domainName = origin.get("DomainName");
                if (domainName instanceof String s) {
                    assertFalse(s.contains(":"),
                            "CloudFront origin DomainName must not contain a colon (use hostname only, not URL). Got: " + s);
                }
            }
        }
    }
}
