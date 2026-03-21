package org.bruneel.infra;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class InfraApp {

    public static void main(String[] args) {
        App app = new App();

        String envName = (String) app.getNode().tryGetContext("env");
        if (envName == null || envName.isBlank()) {
            envName = "test";
        }

        String architecture = (String) app.getNode().tryGetContext("architecture");
        boolean serverless = "serverless".equals(architecture != null ? architecture : "");

        String account = (String) app.getNode().tryGetContext("account");
        String region  = (String) app.getNode().tryGetContext("region");
        String auth0Domain = (String) app.getNode().tryGetContext("auth0Domain");
        String auth0Audience = (String) app.getNode().tryGetContext("auth0Audience");
        String giphyApiKey = (String) app.getNode().tryGetContext("giphyApiKey");

        if (account == null || account.isBlank()) {
            account = System.getenv("CDK_DEFAULT_ACCOUNT");
        }
        if (region == null || region.isBlank()) {
            region = System.getenv("CDK_DEFAULT_REGION");
        }

        EnvironmentConfig config = EnvironmentConfig.forEnv(envName, auth0Domain, auth0Audience, giphyApiKey);
        StackProps stackProps = StackProps.builder()
                .env(Environment.builder()
                        .account(account)
                        .region(region)
                        .build())
                .description("Thank You Board – " + config.envName().toUpperCase()
                        + (serverless ? " (serverless)" : "") + " environment")
                .build();

        if (serverless) {
            new ServerlessStack(app, config.prefix() + "-serverless", stackProps, config);
        } else {
            new AppStack(app, config.prefix(), stackProps, config);
        }

        app.synth();
    }
}
