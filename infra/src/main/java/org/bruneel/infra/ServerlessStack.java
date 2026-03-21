package org.bruneel.infra;

import java.io.File;
import java.util.List;
import java.util.Map;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Fn;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;
import software.amazon.awscdk.aws_apigatewayv2_authorizers.HttpJwtAuthorizer;
import software.amazon.awscdk.aws_apigatewayv2_integrations.HttpLambdaIntegration;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.CorsHttpMethod;
import software.amazon.awscdk.services.apigatewayv2.CorsPreflightOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;
import software.amazon.awscdk.services.apigatewayv2.PayloadFormatVersion;
import software.amazon.awscdk.services.cloudfront.AllowedMethods;
import software.amazon.awscdk.services.cloudfront.BehaviorOptions;
import software.amazon.awscdk.services.cloudfront.CachePolicy;
import software.amazon.awscdk.services.cloudfront.Distribution;
import software.amazon.awscdk.services.cloudfront.ErrorResponse;
import software.amazon.awscdk.services.cloudfront.OriginRequestPolicy;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.cloudfront.origins.HttpOrigin;
import software.amazon.awscdk.services.cloudfront.origins.S3BucketOrigin;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.ProjectionType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableEncryption;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.Tracing;
import software.amazon.awscdk.services.lambda.CfnFunction;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.cloudtrail.Trail;
import software.amazon.awscdk.services.cloudtrail.S3EventSelector;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

/**
 * Serverless architecture: Lambda + DynamoDB + API Gateway.
 * No runtime costs when idle – pay only for actual requests.
 */
public class ServerlessStack extends Stack {

    public ServerlessStack(Construct scope, String id, StackProps props, EnvironmentConfig config) {
        super(scope, id, props);

        Tags.of(this).add("Environment", config.envName());
        Tags.of(this).add("Application", "thankyouboard");

        // ---------------------------------------------------------------
        //  DynamoDB tables (on-demand billing = no cost when idle)
        // ---------------------------------------------------------------
        Table boardsTable = Table.Builder.create(this, "BoardsTable")
                .tableName(config.prefix() + "-boards")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .encryption(TableEncryption.DEFAULT)
                .build();

        // Query by ownerId for dashboards (avoid full table scans).
        boardsTable.addGlobalSecondaryIndex(software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps.builder()
                .indexName("ownerId-createdAt-index")
                .partitionKey(Attribute.builder().name("ownerId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("createdAt").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        Table postsTable = Table.Builder.create(this, "PostsTable")
                .tableName(config.prefix() + "-posts")
                .partitionKey(Attribute.builder().name("boardId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("sortKey").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .encryption(TableEncryption.DEFAULT)
                .build();

        // Lookup by postId for edit/delete without scanning.
        postsTable.addGlobalSecondaryIndex(software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps.builder()
                .indexName("postId-index")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        Table pdfJobsTable = Table.Builder.create(this, "PdfJobsTable")
                .tableName(config.prefix() + "-pdf-jobs")
                .partitionKey(Attribute.builder().name("jobId").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .encryption(TableEncryption.DEFAULT)
                .timeToLiveAttribute("expiresAt")
                .build();

        // ---------------------------------------------------------------
        //  SQS queue for async PDF generation (+ dead-letter queue)
        // ---------------------------------------------------------------
        Queue pdfDlq = Queue.Builder.create(this, "PdfJobDlq")
                .queueName(config.prefix() + "-pdf-jobs-dlq")
                .retentionPeriod(Duration.days(7))
                .build();

        Queue pdfQueue = Queue.Builder.create(this, "PdfJobQueue")
                .queueName(config.prefix() + "-pdf-jobs")
                .visibilityTimeout(Duration.seconds(600))
                .deadLetterQueue(DeadLetterQueue.builder()
                        .queue(pdfDlq)
                        .maxReceiveCount(3)
                        .build())
                .build();

        // ---------------------------------------------------------------
        //  S3 buckets for generated PDFs + access logs
        // ---------------------------------------------------------------
        Bucket pdfLogsBucket = Bucket.Builder.create(this, "PdfLogsBucket")
                .bucketName(config.prefix() + "-pdf-logs-" + this.getAccount())
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        Bucket pdfBucket = Bucket.Builder.create(this, "PdfBucket")
                .bucketName(config.prefix() + "-pdf-exports-" + this.getAccount())
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                // Server access logging for PDF GET/PUT diagnostics
                .serverAccessLogsBucket(pdfLogsBucket)
                .serverAccessLogsPrefix("pdf-access/")
                .build();

        // ---------------------------------------------------------------
        //  S3 bucket for user-uploaded images (served via CloudFront)
        // ---------------------------------------------------------------
        Bucket imagesBucket = Bucket.Builder.create(this, "ImagesBucket")
                .bucketName(config.prefix() + "-images-" + this.getAccount())
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .lifecycleRules(List.of(
                        LifecycleRule.builder()
                                .expiration(Duration.days(365))
                                .build()))
                .build();

        // ---------------------------------------------------------------
        //  CloudTrail – scoped to S3 data events for the PDF bucket
        // ---------------------------------------------------------------
        Trail pdfTrail = Trail.Builder.create(this, "PdfTrail")
                .trailName(config.prefix() + "-pdf-trail")
                .isMultiRegionTrail(false)
                .includeGlobalServiceEvents(false)
                .cloudWatchLogsRetention(RetentionDays.ONE_MONTH)
                .build();

        pdfTrail.addS3EventSelector(List.of(
                S3EventSelector.builder()
                        .bucket(pdfBucket)
                        .objectPrefix("pdf-exports/")
                        .build()));

        // ---------------------------------------------------------------
        //  Lambda functions (one per API resource)
        // ---------------------------------------------------------------
        File lambdaJar = new File("../functions/target/lambda-handler.jar");
        if (!lambdaJar.isFile()) {
            throw new IllegalStateException(
                    "Lambda JAR not found. Run: cd functions && mvn package -q");
        }
        // Use the JAR file path so the deployment zip has the JAR at root.
        // Using the parent directory zips target/ with the JAR inside a subpath, so Lambda cannot load it (ClassNotFoundException).
        var lambdaCode = Code.fromAsset(lambdaJar.getAbsolutePath());
        // Map.of only supports up to 10 key/value pairs; we have 11, so use Map.ofEntries.
        var lambdaEnv = Map.ofEntries(
                Map.entry("BOARDS_TABLE", boardsTable.getTableName()),
                Map.entry("POSTS_TABLE", postsTable.getTableName()),
                Map.entry("MAX_POSTS_PER_BOARD", "100"),
                Map.entry("MAX_BOARDS_PER_OWNER", "100"),
                Map.entry("AUTH0_DOMAIN", config.auth0Domain()),
                Map.entry("AUTH0_AUDIENCE", config.auth0Audience()),
                Map.entry("BOARDS_IMAGES_ALLOWED_HOSTS", "giphy.com,*.giphy.com,giphyusercontent.com,*.giphyusercontent.com"),
                Map.entry("BOARDS_PDF_IMAGE_FETCH_TIMEOUT", "2s"),
                Map.entry("BOARDS_PDF_MAX_IMAGE_BYTES_PER_IMAGE", "1048576"),
                Map.entry("BOARDS_PDF_MAX_IMAGE_BYTES_TOTAL", "10485760"),
                Map.entry("POWERTOOLS_SERVICE_NAME", "thankyouboard-api"));

        var pdfLambdaEnv = new java.util.HashMap<>(lambdaEnv);
        pdfLambdaEnv.put("PDF_JOBS_TABLE", pdfJobsTable.getTableName());
        pdfLambdaEnv.put("PDF_BUCKET", pdfBucket.getBucketName());
        pdfLambdaEnv.put("PDF_QUEUE_URL", pdfQueue.getQueueUrl());

        String boardsFnName = config.prefix() + "-boards";
        LogGroup boardsLogGroup = LogGroup.Builder.create(this, "BoardsLambdaLogGroup")
                .logGroupName("/aws/lambda/" + boardsFnName)
                .retention(RetentionDays.ONE_MONTH)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Function boardsLambda = Function.Builder.create(this, "BoardsLambda")
                .functionName(boardsFnName)
                .runtime(Runtime.JAVA_25)
                .handler("org.bruneel.thankyouboard.handler.BoardsHandler")
                .code(lambdaCode)
                .memorySize(256)
                .timeout(Duration.seconds(30))
                .environment(pdfLambdaEnv)
                .tracing(Tracing.ACTIVE)
                .logGroup(boardsLogGroup)
                .build();

        // Enable SnapStart on published versions of the boards Lambda
        CfnFunction boardsLambdaCfn = (CfnFunction) boardsLambda.getNode().getDefaultChild();
        boardsLambdaCfn.setSnapStart(CfnFunction.SnapStartProperty.builder()
                .applyOn("PublishedVersions")
                .build());

        // Alias that always points to the current SnapStart-enabled version
        Alias boardsAlias = Alias.Builder.create(this, "BoardsLambdaAlias")
                .aliasName("live")
                .version(boardsLambda.getCurrentVersion())
                .build();

        String postsFnName = config.prefix() + "-posts";
        LogGroup postsLogGroup = LogGroup.Builder.create(this, "PostsLambdaLogGroup")
                .logGroupName("/aws/lambda/" + postsFnName)
                .retention(RetentionDays.ONE_MONTH)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Function postsLambda = Function.Builder.create(this, "PostsLambda")
                .functionName(postsFnName)
                .runtime(Runtime.JAVA_25)
                .handler("org.bruneel.thankyouboard.handler.PostsHandler")
                .code(lambdaCode)
                .memorySize(256)
                .timeout(Duration.seconds(30))
                .environment(lambdaEnv)
                .tracing(Tracing.ACTIVE)
                .logGroup(postsLogGroup)
                .build();

        // Enable SnapStart on published versions of the posts Lambda
        CfnFunction postsLambdaCfn = (CfnFunction) postsLambda.getNode().getDefaultChild();
        postsLambdaCfn.setSnapStart(CfnFunction.SnapStartProperty.builder()
                .applyOn("PublishedVersions")
                .build());

        // Alias that always points to the current SnapStart-enabled version
        Alias postsAlias = Alias.Builder.create(this, "PostsLambdaAlias")
                .aliasName("live")
                .version(postsLambda.getCurrentVersion())
                .build();

        String imagesFnName = config.prefix() + "-images";
        LogGroup imagesLogGroup = LogGroup.Builder.create(this, "ImagesLambdaLogGroup")
                .logGroupName("/aws/lambda/" + imagesFnName)
                .retention(RetentionDays.ONE_MONTH)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Function imagesLambda = Function.Builder.create(this, "ImagesLambda")
                .functionName(imagesFnName)
                .runtime(Runtime.JAVA_25)
                .handler("org.bruneel.thankyouboard.handler.ImagesHandler")
                .code(lambdaCode)
                .memorySize(256)
                .timeout(Duration.seconds(30))
                .environment(Map.of(
                        "IMAGE_BUCKET", imagesBucket.getBucketName(),
                        "IMAGES_PRESIGN_EXPIRES_SECONDS", "600",
                        "POWERTOOLS_SERVICE_NAME", "thankyouboard-api"))
                .tracing(Tracing.ACTIVE)
                .logGroup(imagesLogGroup)
                .build();

        CfnFunction imagesLambdaCfn = (CfnFunction) imagesLambda.getNode().getDefaultChild();
        imagesLambdaCfn.setSnapStart(CfnFunction.SnapStartProperty.builder()
                .applyOn("PublishedVersions")
                .build());

        Alias imagesAlias = Alias.Builder.create(this, "ImagesLambdaAlias")
                .aliasName("live")
                .version(imagesLambda.getCurrentVersion())
                .build();

        String giphyFnName = config.prefix() + "-giphy";
        LogGroup giphyLogGroup = LogGroup.Builder.create(this, "GiphyLambdaLogGroup")
                .logGroupName("/aws/lambda/" + giphyFnName)
                .retention(RetentionDays.ONE_MONTH)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        var giphyLambdaEnv = new java.util.HashMap<>(lambdaEnv);
        giphyLambdaEnv.put("GIPHY_API_KEY", config.giphyApiKey());

        Function giphyLambda = Function.Builder.create(this, "GiphyLambda")
                .functionName(giphyFnName)
                .runtime(Runtime.JAVA_25)
                .handler("org.bruneel.thankyouboard.handler.GiphyHandler")
                .code(lambdaCode)
                .memorySize(256)
                .timeout(Duration.seconds(30))
                .environment(giphyLambdaEnv)
                .tracing(Tracing.ACTIVE)
                .logGroup(giphyLogGroup)
                .build();

        // Enable SnapStart on published versions of the giphy Lambda
        CfnFunction giphyLambdaCfn = (CfnFunction) giphyLambda.getNode().getDefaultChild();
        giphyLambdaCfn.setSnapStart(CfnFunction.SnapStartProperty.builder()
                .applyOn("PublishedVersions")
                .build());

        // Alias that always points to the current SnapStart-enabled version
        Alias giphyAlias = Alias.Builder.create(this, "GiphyLambdaAlias")
                .aliasName("live")
                .version(giphyLambda.getCurrentVersion())
                .build();

        // ---------------------------------------------------------------
        //  PDF Worker Lambda (SQS-triggered, generous memory/timeout)
        // ---------------------------------------------------------------
        String pdfWorkerFnName = config.prefix() + "-pdf-worker";
        LogGroup pdfWorkerLogGroup = LogGroup.Builder.create(this, "PdfWorkerLambdaLogGroup")
                .logGroupName("/aws/lambda/" + pdfWorkerFnName)
                .retention(RetentionDays.ONE_MONTH)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Function pdfWorkerLambda = Function.Builder.create(this, "PdfWorkerLambda")
                .functionName(pdfWorkerFnName)
                .runtime(Runtime.JAVA_25)
                .handler("org.bruneel.thankyouboard.handler.PdfWorkerHandler")
                .code(lambdaCode)
                .memorySize(1024)
                .timeout(Duration.minutes(5))
                .environment(pdfLambdaEnv)
                .tracing(Tracing.ACTIVE)
                .logGroup(pdfWorkerLogGroup)
                .build();

        CfnFunction pdfWorkerCfn = (CfnFunction) pdfWorkerLambda.getNode().getDefaultChild();
        pdfWorkerCfn.setSnapStart(CfnFunction.SnapStartProperty.builder()
                .applyOn("PublishedVersions")
                .build());

        Alias pdfWorkerAlias = Alias.Builder.create(this, "PdfWorkerLambdaAlias")
                .aliasName("live")
                .version(pdfWorkerLambda.getCurrentVersion())
                .build();

        pdfWorkerAlias.addEventSource(SqsEventSource.Builder.create(pdfQueue)
                .batchSize(1)
                .build());

        // ---------------------------------------------------------------
        //  IAM grants
        // ---------------------------------------------------------------
        boardsTable.grantReadWriteData(boardsLambda);
        postsTable.grantReadWriteData(postsLambda);
        boardsTable.grantReadData(postsLambda);

        pdfJobsTable.grantReadWriteData(boardsLambda);
        pdfQueue.grantSendMessages(boardsLambda);
        pdfBucket.grantRead(boardsLambda);

        pdfJobsTable.grantReadWriteData(pdfWorkerLambda);
        boardsTable.grantReadData(pdfWorkerLambda);
        postsTable.grantReadData(pdfWorkerLambda);
        pdfBucket.grantReadWrite(pdfWorkerLambda);

        // Allow images Lambda to presign PUTs (and optionally validate bucket access)
        imagesBucket.grantPut(imagesLambda);

        // ---------------------------------------------------------------
        //  API Gateway HTTP API – explicit routes, JWT auth for boards list/create
        // ---------------------------------------------------------------
        HttpApi api = HttpApi.Builder.create(this, "Api")
                .apiName(config.prefix() + "-api")
                .corsPreflight(CorsPreflightOptions.builder()
                        .allowOrigins(List.of("*"))
                        .allowMethods(List.of(
                                CorsHttpMethod.GET,
                                CorsHttpMethod.POST,
                                CorsHttpMethod.PUT,
                                CorsHttpMethod.DELETE,
                                CorsHttpMethod.OPTIONS))
                        .allowHeaders(List.of(
                                "Content-Type",
                                "Accept",
                                "Authorization",
                                "X-Request-Id",
                                "X-Post-Capability-Token"
                        ))
                        .exposeHeaders(List.of("x-request-id"))
                        .build())
                .build();

        String issuer = "https://" + config.auth0Domain() + "/";
        HttpJwtAuthorizer auth0Authorizer = HttpJwtAuthorizer.Builder.create("Auth0JwtAuthorizer", issuer)
                .jwtAudience(List.of(config.auth0Audience()))
                .build();

        HttpLambdaIntegration boardsIntegration = HttpLambdaIntegration.Builder
                .create("BoardsIntegration", boardsAlias)
                .payloadFormatVersion(PayloadFormatVersion.VERSION_1_0)
                .scopePermissionToRoute(false)
                .build();
        HttpLambdaIntegration postsIntegration = HttpLambdaIntegration.Builder
                .create("PostsIntegration", postsAlias)
                .payloadFormatVersion(PayloadFormatVersion.VERSION_1_0)
                .scopePermissionToRoute(false)
                .build();
        HttpLambdaIntegration giphyIntegration = HttpLambdaIntegration.Builder
                .create("GiphyIntegration", giphyAlias)
                .payloadFormatVersion(PayloadFormatVersion.VERSION_1_0)
                .scopePermissionToRoute(false)
                .build();
        HttpLambdaIntegration imagesIntegration = HttpLambdaIntegration.Builder
                .create("ImagesIntegration", imagesAlias)
                .payloadFormatVersion(PayloadFormatVersion.VERSION_1_0)
                .scopePermissionToRoute(false)
                .build();

        // Secured: /api/boards (dashboard list + create)
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards")
                .methods(List.of(HttpMethod.GET))
                .integration(boardsIntegration)
                .authorizer(auth0Authorizer)
                .build());
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards")
                .methods(List.of(HttpMethod.POST))
                .integration(boardsIntegration)
                .authorizer(auth0Authorizer)
                .build());

        // Anonymous: /api/boards/{id} (view), secured PUT/DELETE (edit/delete)
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}")
                .methods(List.of(HttpMethod.GET))
                .integration(boardsIntegration)
                .build());
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}")
                .methods(List.of(HttpMethod.PUT))
                .integration(boardsIntegration)
                .authorizer(auth0Authorizer)
                .build());
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}/pdf")
                .methods(List.of(HttpMethod.GET))
                .integration(boardsIntegration)
                .authorizer(auth0Authorizer)
                .build());
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}")
                .methods(List.of(HttpMethod.DELETE))
                .integration(boardsIntegration)
                .authorizer(auth0Authorizer)
                .build());

        // Secured: /api/boards/{id}/pdf-jobs (create + status)
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}/pdf-jobs")
                .methods(List.of(HttpMethod.POST))
                .integration(boardsIntegration)
                .authorizer(auth0Authorizer)
                .build());
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}/pdf-jobs/{jobId}")
                .methods(List.of(HttpMethod.GET))
                .integration(boardsIntegration)
                .authorizer(auth0Authorizer)
                .build());

        // Anonymous: /api/boards/{id}/posts
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}/posts")
                .methods(List.of(HttpMethod.GET, HttpMethod.POST))
                .integration(postsIntegration)
                .build());

        // Secured: /api/boards/{id}/posts/{postId} (edit/delete)
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}/posts/{postId}")
                .methods(List.of(HttpMethod.PUT, HttpMethod.DELETE))
                .integration(postsIntegration)
                .build());

        // Anonymous: /api/boards/{id}/images/presign
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/boards/{id}/images/presign")
                .methods(List.of(HttpMethod.POST))
                .integration(imagesIntegration)
                .build());

        // Anonymous: /api/giphy/search and /api/giphy/trending
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/giphy/search")
                .methods(List.of(HttpMethod.GET))
                .integration(giphyIntegration)
                .build());
        api.addRoutes(AddRoutesOptions.builder()
                .path("/api/giphy/trending")
                .methods(List.of(HttpMethod.GET))
                .integration(giphyIntegration)
                .build());

        // Extract hostname at deploy time: CloudFront origin must be a domain name (no scheme, no path).
        // api.getUrl() is a token; split by "/" and take index 2: "https://host/path" -> host.
        // Do not pass assumedLength: the HTTP API URL has only 3 segments, so assumedLength 5 would
        // cause CDK to emit Fn::Select [4] which fails at deploy time.
        String apiOriginDomain = Fn.select(2, Fn.split("/", api.getUrl()));

        // ---------------------------------------------------------------
        //  Frontend – S3 bucket
        // ---------------------------------------------------------------
        Bucket frontendBucket = Bucket.Builder.create(this, "FrontendBucket")
                .bucketName(config.prefix() + "-serverless-frontend-" + this.getAccount())
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(RemovalPolicy.DESTROY)
                .autoDeleteObjects(true)
                .build();

        // ---------------------------------------------------------------
        //  CloudFront CDN (frontend + API via Lambda/API Gateway)
        // ---------------------------------------------------------------
        HttpOrigin apiOrigin = HttpOrigin.Builder.create(apiOriginDomain)
                .protocolPolicy(software.amazon.awscdk.services.cloudfront.OriginProtocolPolicy.HTTPS_ONLY)
                .build();

        Distribution cdn = Distribution.Builder.create(this, "CDN")
                .comment(config.prefix() + " serverless distribution")
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
                                .build(),
                        "/images/*", BehaviorOptions.builder()
                                .origin(S3BucketOrigin.withOriginAccessControl(imagesBucket))
                                .allowedMethods(AllowedMethods.ALLOW_GET_HEAD_OPTIONS)
                                .cachePolicy(CachePolicy.CACHING_OPTIMIZED)
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

        // Browser uploads go directly to S3 with a presigned PUT URL (not through CloudFront).
        // IMPORTANT: Avoid referencing the CloudFront distribution domain here, otherwise
        // CloudFormation ends up with a circular dependency (CDN -> bucket origin, bucket -> CDN token).
        //
        // We allow any origin because the presigned URL already authorizes the request.
        imagesBucket.addCorsRule(CorsRule.builder()
                .allowedOrigins(List.of("*"))
                .allowedMethods(List.of(HttpMethods.PUT, HttpMethods.GET, HttpMethods.HEAD))
                .allowedHeaders(List.of("*"))
                .exposedHeaders(List.of("ETag", "x-amz-request-id", "x-amz-id-2"))
                .maxAge(3000)
                .build());

        // NOTE: We intentionally do NOT inject the CloudFront domain into Lambda environment variables,
        // to avoid circular dependencies (CDN -> API -> Lambda alias/version, Lambda -> CDN token).
        // The images presign endpoint derives the base URL from request headers when called via CloudFront.
        String giphyHosts = "giphy.com,*.giphy.com,giphyusercontent.com,*.giphyusercontent.com";
        postsLambda.addEnvironment("BOARDS_IMAGES_ALLOWED_HOSTS", giphyHosts);
        boardsLambda.addEnvironment("BOARDS_IMAGES_ALLOWED_HOSTS", giphyHosts);
        pdfWorkerLambda.addEnvironment("BOARDS_IMAGES_ALLOWED_HOSTS", giphyHosts);

        // Allow any CloudFront distribution host for uploaded images.
        // (The presign service constructs URLs pointing at the current distribution domain.)
        String uploadedHosts = "*.cloudfront.net";
        postsLambda.addEnvironment("BOARDS_UPLOADED_IMAGES_ALLOWED_HOSTS", uploadedHosts);
        boardsLambda.addEnvironment("BOARDS_UPLOADED_IMAGES_ALLOWED_HOSTS", uploadedHosts);
        pdfWorkerLambda.addEnvironment("BOARDS_UPLOADED_IMAGES_ALLOWED_HOSTS", uploadedHosts);

        // Deploy built frontend to S3
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

        CfnOutput.Builder.create(this, "CloudFrontUrl")
                .value("https://" + cdn.getDistributionDomainName())
                .description("CloudFront distribution URL (frontend + API)")
                .build();

        CfnOutput.Builder.create(this, "ApiGatewayUrl")
                .value(api.getUrl())
                .description("API Gateway base URL")
                .build();

        CfnOutput.Builder.create(this, "FrontendBucketName")
                .value(frontendBucket.getBucketName())
                .description("S3 bucket for frontend assets")
                .build();
    }
}
