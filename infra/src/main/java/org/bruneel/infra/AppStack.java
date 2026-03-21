package org.bruneel.infra;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.apprunner.CfnService;
import software.amazon.awscdk.services.apprunner.CfnVpcConnector;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.CachePolicy;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.ErrorResponse;
import software.amazon.awscdk.services.cloudfront.OriginRequestPolicy;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.HttpOrigin;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOrigin;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.SubnetConfiguration;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecr.assets.DockerImageAsset;
import software.amazon.awscdk.services.ecr.assets.Platform;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.constructs.Construct;

public class AppStack extends Stack {

    public AppStack(Construct scope, String id, StackProps props, EnvironmentConfig config) {
        super(scope, id, props);

        // ---------------------------------------------------------------
        //  Networking – minimal VPC with isolated subnets only (no NAT)
        // ---------------------------------------------------------------
        Vpc vpc = Vpc.Builder.create(this, "Vpc")
                .vpcName(config.prefix() + "-vpc")
                .maxAzs(2)
                .natGateways(0)
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder()
                                .name("isolated")
                                .subnetType(SubnetType.PRIVATE_ISOLATED)
                                .cidrMask(24)
                                .build()))
                .build();

        SecurityGroup appRunnerSg = SecurityGroup.Builder.create(this, "AppRunnerSg")
                .vpc(vpc)
                .securityGroupName(config.prefix() + "-apprunner-sg")
                .description("App Runner VPC connector")
                .allowAllOutbound(true)
                .build();

        SecurityGroup dbSg = SecurityGroup.Builder.create(this, "DbSg")
                .vpc(vpc)
                .securityGroupName(config.prefix() + "-db-sg")
                .description("RDS PostgreSQL")
                .allowAllOutbound(false)
                .build();

        dbSg.addIngressRule(appRunnerSg, Port.tcp(5432), "App Runner → RDS");

        // ---------------------------------------------------------------
        //  Database – RDS PostgreSQL
        // ---------------------------------------------------------------
        DatabaseInstance db = DatabaseInstance.Builder.create(this, "Database")
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_16)
                                .build()))
                .instanceType(instanceTypeFromString(config.rdsInstanceClass()))
                .vpc(vpc)
                .vpcSubnets(software.amazon.awscdk.services.ec2.SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_ISOLATED)
                        .build())
                .securityGroups(List.of(dbSg))
                .databaseName("thankyouboard")
                .credentials(Credentials.fromGeneratedSecret("postgres"))
                .allocatedStorage(config.rdsStorageGb())
                .backupRetention(Duration.days(config.backupRetentionDays()))
                .storageEncrypted(true)
                .multiAz(config.multiAz())
                .deletionProtection(config.deletionProtection())
                .removalPolicy(config.deletionProtection() ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .build();

        ISecret dbSecret = db.getSecret();

        // ---------------------------------------------------------------
        //  Backend Docker image (built by CDK from repo-root Dockerfile)
        // ---------------------------------------------------------------
        DockerImageAsset backendImage = DockerImageAsset.Builder.create(this, "BackendImage")
                .directory(new File("..").getAbsolutePath())
                .platform(Platform.LINUX_AMD64)
                .build();

        // ---------------------------------------------------------------
        //  IAM roles for App Runner
        // ---------------------------------------------------------------
        Role accessRole = Role.Builder.create(this, "AppRunnerAccessRole")
                .assumedBy(new ServicePrincipal("build.apprunner.amazonaws.com"))
                .build();
        backendImage.getRepository().grantPull(accessRole);

        Role instanceRole = Role.Builder.create(this, "AppRunnerInstanceRole")
                .assumedBy(new ServicePrincipal("tasks.apprunner.amazonaws.com"))
                .build();

        if (dbSecret != null) {
            dbSecret.grantRead(instanceRole);
        }

        // ---------------------------------------------------------------
        //  App Runner VPC connector (reach RDS in isolated subnets)
        // ---------------------------------------------------------------
        List<String> subnetIds = vpc.getIsolatedSubnets().stream()
                .map(ISubnet::getSubnetId)
                .collect(Collectors.toList());

        CfnVpcConnector vpcConnector = CfnVpcConnector.Builder.create(this, "VpcConnector")
                .subnets(subnetIds)
                .securityGroups(List.of(appRunnerSg.getSecurityGroupId()))
                .vpcConnectorName(config.prefix() + "-connector")
                .build();

        // ---------------------------------------------------------------
        //  App Runner service
        // ---------------------------------------------------------------
        String jdbcUrl = "jdbc:postgresql://"
                + db.getDbInstanceEndpointAddress() + ":"
                + db.getDbInstanceEndpointPort() + "/thankyouboard";

        List<CfnService.KeyValuePairProperty> envVars = List.of(
                kvp("SPRING_DATASOURCE_URL", jdbcUrl),
                kvp("SPRING_DATASOURCE_USERNAME", "postgres"),
                kvp("SPRING_FLYWAY_ENABLED", "true"),
                kvp("SPRING_JPA_HIBERNATE_DDL_AUTO", "validate"),
                kvp("AUTH0_DOMAIN", config.auth0Domain()),
                kvp("AUTH0_AUDIENCE", config.auth0Audience()),
                kvp("GIPHY_API_KEY", config.giphyApiKey()));

        List<CfnService.KeyValuePairProperty> envSecrets = dbSecret != null
                ? List.of(kvp("SPRING_DATASOURCE_PASSWORD", dbSecret.getSecretArn() + ":password::"))
                : List.of();

        CfnService backendService = CfnService.Builder.create(this, "BackendService")
                .serviceName(config.prefix() + "-backend")
                .sourceConfiguration(CfnService.SourceConfigurationProperty.builder()
                        .autoDeploymentsEnabled(false)
                        .authenticationConfiguration(
                                CfnService.AuthenticationConfigurationProperty.builder()
                                        .accessRoleArn(accessRole.getRoleArn())
                                        .build())
                        .imageRepository(CfnService.ImageRepositoryProperty.builder()
                                .imageIdentifier(backendImage.getImageUri())
                                .imageRepositoryType("ECR")
                                .imageConfiguration(
                                        CfnService.ImageConfigurationProperty.builder()
                                                .port("8080")
                                                .runtimeEnvironmentVariables(envVars)
                                                .runtimeEnvironmentSecrets(envSecrets)
                                                .build())
                                .build())
                        .build())
                .instanceConfiguration(CfnService.InstanceConfigurationProperty.builder()
                        .cpu(config.appRunnerCpu())
                        .memory(config.appRunnerMemory())
                        .instanceRoleArn(instanceRole.getRoleArn())
                        .build())
                .networkConfiguration(CfnService.NetworkConfigurationProperty.builder()
                        .egressConfiguration(CfnService.EgressConfigurationProperty.builder()
                                .egressType("VPC")
                                .vpcConnectorArn(vpcConnector.getAttrVpcConnectorArn())
                                .build())
                        .build())
                .healthCheckConfiguration(CfnService.HealthCheckConfigurationProperty.builder()
                        .protocol("TCP")
                        .interval(10)
                        .timeout(5)
                        .healthyThreshold(1)
                        .unhealthyThreshold(5)
                        .build())
                .build();

        // ---------------------------------------------------------------
        //  Frontend – S3 bucket
        // ---------------------------------------------------------------
        Bucket frontendBucket = Bucket.Builder.create(this, "FrontendBucket")
                .bucketName(config.prefix() + "-frontend-" + this.getAccount())
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(config.deletionProtection() ? RemovalPolicy.RETAIN : RemovalPolicy.DESTROY)
                .autoDeleteObjects(!config.deletionProtection())
                .build();

        // ---------------------------------------------------------------
        //  CloudFront CDN
        // ---------------------------------------------------------------
        // Use hostname only: CloudFront origin name cannot contain a colon (no https://).
        String apiOriginDomain = backendService.getAttrServiceUrl().replaceFirst("^https?://", "");
        HttpOrigin apiOrigin = HttpOrigin.Builder.create(apiOriginDomain)
                .protocolPolicy(software.amazon.awscdk.services.cloudfront.OriginProtocolPolicy.HTTPS_ONLY)
                .build();

        Distribution cdn = Distribution.Builder.create(this, "CDN")
                .comment(config.prefix() + " distribution")
                .defaultBehavior(BehaviorOptions.builder()
                        .origin(S3BucketOrigin.withOriginAccessControl(frontendBucket))
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                        .build())
                .additionalBehaviors(Map.of(
                        "/api/*", BehaviorOptions.builder()
                                .origin(apiOrigin)
                                .allowedMethods(AllowedMethods.ALLOW_ALL)
                                .cachePolicy(CachePolicy.CACHING_DISABLED)
                                .originRequestPolicy(OriginRequestPolicy.ALL_VIEWER_EXCEPT_HOST_HEADER)
                                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                                .build()))
                .defaultRootObject("index.html")
                .errorResponses(List.of(
                        ErrorResponse.builder()
                                .httpStatus(403)
                                .responseHttpStatus(200)
                                .responsePagePath("/index.html")
                                .ttl(Duration.seconds(0))
                                .build(),
                        ErrorResponse.builder()
                                .httpStatus(404)
                                .responseHttpStatus(200)
                                .responsePagePath("/index.html")
                                .ttl(Duration.seconds(0))
                                .build()))
                .build();

        // ---------------------------------------------------------------
        //  Deploy built frontend to S3 (only if dist/ exists)
        // ---------------------------------------------------------------
        File distDir = new File("../frontend/dist");
        if (distDir.isDirectory()) {
            BucketDeployment.Builder.create(this, "DeployFrontend")
                    .sources(List.of(Source.asset(distDir.getAbsolutePath())))
                    .destinationBucket(frontendBucket)
                    .distribution(cdn)
                    .distributionPaths(List.of("/*"))
                    .build();
        } else {
            System.out.println("⚠  frontend/dist not found – skipping S3 upload. "
                    + "Run 'cd frontend && npx vite build' before deploying.");
        }

        // ---------------------------------------------------------------
        //  Stack outputs
        // ---------------------------------------------------------------
        CfnOutput.Builder.create(this, "CloudFrontUrl")
                .value("https://" + cdn.getDistributionDomainName())
                .description("CloudFront distribution URL (frontend + API)")
                .build();

        CfnOutput.Builder.create(this, "AppRunnerUrl")
                .value("https://" + backendService.getAttrServiceUrl())
                .description("App Runner backend URL (direct)")
                .build();

        CfnOutput.Builder.create(this, "RdsEndpoint")
                .value(db.getDbInstanceEndpointAddress())
                .description("RDS PostgreSQL endpoint")
                .build();

        CfnOutput.Builder.create(this, "FrontendBucketName")
                .value(frontendBucket.getBucketName())
                .description("S3 bucket for frontend assets")
                .build();
    }

    /** Map RDS instance class strings like "db.t4g.micro" to CDK InstanceType. */
    private static InstanceType instanceTypeFromString(String instanceClass) {
        return new InstanceType(instanceClass.replace("db.", ""));
    }

    private static CfnService.KeyValuePairProperty kvp(String name, String value) {
        return CfnService.KeyValuePairProperty.builder()
                .name(name).value(value).build();
    }
}
