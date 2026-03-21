package org.bruneel.thankyouboard.repository;

import org.bruneel.thankyouboard.model.PdfJob;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PdfJobRepository {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final long TTL_SECONDS = 24 * 60 * 60;

    private final DynamoDbClient client;
    private final String tableName;

    public PdfJobRepository(String tableName) {
        this.client = DynamoDbClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .addExecutionInterceptor(new com.amazonaws.xray.interceptors.TracingInterceptor())
                        .build())
                .build();
        this.tableName = tableName;
    }

    PdfJobRepository(DynamoDbClient client, String tableName) {
        this.client = client;
        this.tableName = tableName;
    }

    public void save(PdfJob job) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("jobId", s(job.getJobId().toString()));
        item.put("boardId", s(job.getBoardId().toString()));
        item.put("ownerId", s(job.getOwnerId()));
        item.put("status", s(job.getStatus().name()));
        item.put("createdAt", s(job.getCreatedAt().format(ISO)));
        item.put("updatedAt", s(job.getUpdatedAt().format(ISO)));

        long ttl = job.getExpiresAt() > 0
                ? job.getExpiresAt()
                : Instant.now().getEpochSecond() + TTL_SECONDS;
        item.put("expiresAt", AttributeValue.builder().n(String.valueOf(ttl)).build());

        if (job.getDownloadKey() != null) {
            item.put("downloadKey", s(job.getDownloadKey()));
        }
        if (job.getErrorCode() != null) {
            item.put("errorCode", s(job.getErrorCode()));
        }
        if (job.getErrorMessage() != null) {
            item.put("errorMessage", s(job.getErrorMessage()));
        }

        client.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }

    public Optional<PdfJob> findById(UUID jobId) {
        GetItemResponse response = client.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("jobId", s(jobId.toString())))
                .build());

        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromItem(response.item()));
    }

    private static PdfJob fromItem(Map<String, AttributeValue> item) {
        PdfJob job = new PdfJob();
        job.setJobId(UUID.fromString(req(item, "jobId")));
        job.setBoardId(UUID.fromString(req(item, "boardId")));
        job.setOwnerId(req(item, "ownerId"));
        job.setStatus(PdfJob.Status.valueOf(req(item, "status")));
        job.setCreatedAt(ZonedDateTime.parse(req(item, "createdAt"), ISO));
        job.setUpdatedAt(ZonedDateTime.parse(req(item, "updatedAt"), ISO));
        job.setDownloadKey(opt(item, "downloadKey"));
        job.setErrorCode(opt(item, "errorCode"));
        job.setErrorMessage(opt(item, "errorMessage"));
        AttributeValue ttl = item.get("expiresAt");
        if (ttl != null && ttl.n() != null) {
            job.setExpiresAt(Long.parseLong(ttl.n()));
        }
        return job;
    }

    private static String req(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        if (v == null || v.s() == null) {
            throw new IllegalArgumentException("Missing required attribute: " + key);
        }
        return v.s();
    }

    private static String opt(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return (v != null && v.s() != null) ? v.s() : null;
    }

    private static AttributeValue s(String value) {
        return AttributeValue.builder().s(value).build();
    }
}
