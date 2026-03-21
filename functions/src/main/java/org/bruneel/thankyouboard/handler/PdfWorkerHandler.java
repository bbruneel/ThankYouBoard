package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import org.bruneel.thankyouboard.repository.BoardRepository;
import org.bruneel.thankyouboard.repository.PdfJobRepository;
import org.bruneel.thankyouboard.repository.PostRepository;
import org.bruneel.thankyouboard.service.PdfJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;
import software.amazon.lambda.powertools.tracing.TracingUtils;

import java.util.UUID;

public class PdfWorkerHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger log = LoggerFactory.getLogger(PdfWorkerHandler.class);
    private final PdfJobService pdfJobService;

    public PdfWorkerHandler() {
        String boardsTable = System.getenv("BOARDS_TABLE");
        String postsTable = System.getenv("POSTS_TABLE");
        String pdfJobsTable = System.getenv("PDF_JOBS_TABLE");
        String pdfBucket = System.getenv("PDF_BUCKET");
        String sqsQueueUrl = System.getenv("PDF_QUEUE_URL");

        SqsClient sqsClient = SqsClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .addExecutionInterceptor(new com.amazonaws.xray.interceptors.TracingInterceptor())
                        .build())
                .build();
        S3Client s3Client = S3Client.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .addExecutionInterceptor(new com.amazonaws.xray.interceptors.TracingInterceptor())
                        .build())
                .build();

        this.pdfJobService = new PdfJobService(
                new BoardRepository(boardsTable),
                new PostRepository(postsTable),
                new PdfJobRepository(pdfJobsTable),
                sqsClient,
                sqsQueueUrl,
                s3Client,
                S3Presigner.create(),
                pdfBucket);
    }

    PdfWorkerHandler(PdfJobService pdfJobService) {
        this.pdfJobService = pdfJobService;
    }

    @Override
    @Logging(clearState = true)
    @Tracing
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSEvent.SQSMessage message : event.getRecords()) {
            MDC.clear();
            String correlationId = extractCorrelationId(message);
            CorrelationContext.initFromSqs(correlationId, context);

            String body = message.getBody();
            log.info("Processing PDF job message: {}", body);

            if (body == null || body.isBlank()) {
                log.warn("PDF job message ignored: body is null or empty. Expected a single UUID.");
                throw new IllegalArgumentException("SQS message body must be a non-empty UUID");
            }

            try {
                UUID jobId = UUID.fromString(body.trim());
                MDC.put("job_id", jobId.toString());
                TracingUtils.putAnnotation("job_id", jobId.toString());
                pdfJobService.processJob(jobId);
            } catch (IllegalArgumentException e) {
                log.warn("PDF job message rejected: body is not a valid UUID. body='{}'. Expected format: single UUID per message.", body);
                throw e;
            } catch (Exception e) {
                log.error("Failed to process PDF job message: {}", body, e);
                throw e;
            }
        }
        return null;
    }

    private static String extractCorrelationId(SQSEvent.SQSMessage message) {
        if (message.getMessageAttributes() == null) return null;
        SQSEvent.MessageAttribute attr = message.getMessageAttributes().get("correlation_id");
        if (attr == null) return null;
        return attr.getStringValue();
    }
}
