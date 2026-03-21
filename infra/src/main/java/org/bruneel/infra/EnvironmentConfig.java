package org.bruneel.infra;

/**
 * Per-environment sizing and behaviour knobs.
 * Extend this record when you need a new environment-specific setting.
 */
public record EnvironmentConfig(
        String envName,
        String prefix,
        String rdsInstanceClass,
        String appRunnerCpu,
        String appRunnerMemory,
        int rdsStorageGb,
        int backupRetentionDays,
        boolean deletionProtection,
        boolean multiAz,
        String auth0Domain,
        String auth0Audience,
        String giphyApiKey
) {

    public static EnvironmentConfig forEnv(
            String env,
            String auth0Domain,
            String auth0Audience,
            String giphyApiKey) {
        String resolvedAuth0Domain = requireContextValue("auth0Domain", auth0Domain);
        String resolvedAuth0Audience = requireContextValue("auth0Audience", auth0Audience);
        String resolvedGiphyApiKey = giphyApiKey != null ? giphyApiKey.trim() : "";
        if (resolvedGiphyApiKey.isBlank()) {
            resolvedGiphyApiKey = "";
        }
        return switch (env.toLowerCase()) {
            case "test" -> new EnvironmentConfig(
                    "test", "thankyouboard-test",
                    "db.t4g.micro", "0.25 vCPU", "0.5 GB",
                    20, 1, false, false,
                    resolvedAuth0Domain, resolvedAuth0Audience, resolvedGiphyApiKey);
            case "qa" -> new EnvironmentConfig(
                    "qa", "thankyouboard-qa",
                    "db.t4g.micro", "0.5 vCPU", "1 GB",
                    20, 1, false, false,
                    resolvedAuth0Domain, resolvedAuth0Audience, resolvedGiphyApiKey);
            case "prd" -> new EnvironmentConfig(
                    "prd", "thankyouboard-prd",
                    "db.t4g.small", "1 vCPU", "2 GB",
                    50, 7, true, true,
                    resolvedAuth0Domain, resolvedAuth0Audience, resolvedGiphyApiKey);
            default -> throw new IllegalArgumentException(
                    "Unknown environment: " + env + ". Use: test, qa, or prd");
        };
    }

    private static String requireContextValue(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required CDK context '" + key + "'. Pass it with -c " + key + "=<value>");
        }
        return value;
    }
}
