package org.bruneel.thankyouboard.repository;

import org.bruneel.thankyouboard.model.Post;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Optional;

/**
 * Posts table design:
 * PK: boardId (partition key)
 * SK: createdAt#postId (sort key) - enables query by board, ordered by createdAt
 */
public class PostRepository {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DynamoDbClient client;
    private final String tableName;

    public PostRepository(String tableName) {
        this.client = DynamoDbClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .addExecutionInterceptor(new com.amazonaws.xray.interceptors.TracingInterceptor())
                        .build())
                .build();
        this.tableName = tableName;
    }

    public void save(Post post) {
        String sk = post.getCreatedAt().format(ISO) + "#" + post.getId();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("boardId", AttributeValue.builder().s(post.getBoardId().toString()).build());
        item.put("sortKey", AttributeValue.builder().s(sk).build());
        item.put("id", AttributeValue.builder().s(post.getId().toString()).build());
        item.put("authorName", AttributeValue.builder().s(post.getAuthorName()).build());
        if (post.getMessageText() != null) {
            item.put("messageText", AttributeValue.builder().s(post.getMessageText()).build());
        }
        if (post.getGiphyUrl() != null) {
            item.put("giphyUrl", AttributeValue.builder().s(post.getGiphyUrl()).build());
        }
        if (post.getUploadedImageUrl() != null) {
            item.put("uploadedImageUrl", AttributeValue.builder().s(post.getUploadedImageUrl()).build());
        }
        item.put("createdAt", AttributeValue.builder().s(post.getCreatedAt().format(ISO)).build());

        if (post.getCapabilityTokenHash() != null) {
            item.put("capabilityTokenHash", AttributeValue.builder().s(post.getCapabilityTokenHash()).build());
        }
        if (post.getCapabilityTokenExpiresAt() != null) {
            item.put("capabilityTokenExpiresAt", AttributeValue.builder().s(post.getCapabilityTokenExpiresAt().format(ISO)).build());
        }

        client.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    public List<Post> findByBoardIdOrderByCreatedAtAsc(UUID boardId) {
        QueryResponse response = client.query(QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("boardId = :bid")
                .expressionAttributeValues(Map.of(":bid", AttributeValue.builder().s(boardId.toString()).build()))
                .scanIndexForward(true)
                .build());

        List<Post> posts = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            posts.add(fromItem(item));
        }
        return posts;
    }

    public long countByBoardId(UUID boardId) {
        QueryResponse response = client.query(QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("boardId = :bid")
                .expressionAttributeValues(Map.of(":bid", AttributeValue.builder().s(boardId.toString()).build()))
                .select(Select.COUNT)
                .build());
        return response.count() != null ? response.count().longValue() : 0L;
    }

    public Optional<Post> findById(UUID postId) {
        if (postId == null) {
            return Optional.empty();
        }

        Map<String, AttributeValue> values = Map.of(
                ":id", AttributeValue.builder().s(postId.toString()).build()
        );

        try {
            QueryResponse response = client.query(QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("postId-index")
                    .keyConditionExpression("id = :id")
                    .expressionAttributeValues(values)
                    .limit(1)
                    .build());
            if (response.items() == null || response.items().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(fromItem(response.items().getFirst()));
        } catch (DynamoDbException e) {
            // Backwards-compatible fallback when the GSI doesn't exist yet.
            ScanResponse response = client.scan(ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("id = :id")
                    .expressionAttributeValues(values)
                    .limit(1)
                    .build());
            if (response.items() == null || response.items().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(fromItem(response.items().getFirst()));
        }
    }

    public Post updatePost(
            Post existing,
            boolean updateAuthorName, String authorName,
            boolean updateMessageText, String messageText,
            boolean updateGiphyUrl, String giphyUrl,
            boolean updateUploadedImageUrl, String uploadedImageUrl
    ) {
        String sortKey = existing.getCreatedAt().format(ISO) + "#" + existing.getId();
        Map<String, AttributeValue> key = Map.of(
                "boardId", AttributeValue.builder().s(existing.getBoardId().toString()).build(),
                "sortKey", AttributeValue.builder().s(sortKey).build()
        );

        Map<String, String> names = new HashMap<>();
        Map<String, AttributeValue> values = new HashMap<>();
        List<String> setParts = new ArrayList<>();
        List<String> removeParts = new ArrayList<>();

        if (updateAuthorName) {
            names.put("#authorName", "authorName");
            values.put(":authorName", AttributeValue.builder().s(authorName).build());
            setParts.add("#authorName = :authorName");
        }
        if (updateMessageText) {
            names.put("#messageText", "messageText");
            if (messageText == null || messageText.isBlank()) {
                removeParts.add("#messageText");
            } else {
                values.put(":messageText", AttributeValue.builder().s(messageText).build());
                setParts.add("#messageText = :messageText");
            }
        }
        if (updateGiphyUrl) {
            names.put("#giphyUrl", "giphyUrl");
            if (giphyUrl == null || giphyUrl.isBlank()) {
                removeParts.add("#giphyUrl");
            } else {
                values.put(":giphyUrl", AttributeValue.builder().s(giphyUrl).build());
                setParts.add("#giphyUrl = :giphyUrl");
            }
        }
        if (updateUploadedImageUrl) {
            names.put("#uploadedImageUrl", "uploadedImageUrl");
            if (uploadedImageUrl == null || uploadedImageUrl.isBlank()) {
                removeParts.add("#uploadedImageUrl");
            } else {
                values.put(":uploadedImageUrl", AttributeValue.builder().s(uploadedImageUrl).build());
                setParts.add("#uploadedImageUrl = :uploadedImageUrl");
            }
        }

        StringBuilder expr = new StringBuilder();
        if (!setParts.isEmpty()) {
            expr.append("SET ").append(String.join(", ", setParts));
        }
        if (!removeParts.isEmpty()) {
            if (!expr.isEmpty()) expr.append(" ");
            expr.append("REMOVE ").append(String.join(", ", removeParts));
        }
        if (expr.isEmpty()) {
            return existing;
        }

        UpdateItemResponse response = client.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression(expr.toString())
                .expressionAttributeNames(names.isEmpty() ? null : names)
                .expressionAttributeValues(values.isEmpty() ? null : values)
                .conditionExpression("attribute_exists(boardId)")
                .returnValues(ReturnValue.ALL_NEW)
                .build());

        return fromItem(response.attributes());
    }

    public void delete(Post existing) {
        String sortKey = existing.getCreatedAt().format(ISO) + "#" + existing.getId();
        Map<String, AttributeValue> key = Map.of(
                "boardId", AttributeValue.builder().s(existing.getBoardId().toString()).build(),
                "sortKey", AttributeValue.builder().s(sortKey).build()
        );
        client.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("attribute_exists(boardId)")
                .build());
    }

    private static Post fromItem(Map<String, AttributeValue> item) {
        String id = getRequiredString(item, "id");
        String boardId = getRequiredString(item, "boardId");
        String authorName = getRequiredString(item, "authorName");
        String messageText = getOptionalString(item, "messageText");
        String giphyUrl = getOptionalString(item, "giphyUrl");
        String uploadedImageUrl = getOptionalString(item, "uploadedImageUrl");
        String createdAt = getRequiredString(item, "createdAt");

        Post post = new Post(
                UUID.fromString(id),
                UUID.fromString(boardId),
                authorName,
                messageText,
                giphyUrl,
                uploadedImageUrl,
                ZonedDateTime.parse(createdAt, ISO)
        );
        String capabilityTokenHash = getOptionalString(item, "capabilityTokenHash");
        String capabilityTokenExpiresAt = getOptionalString(item, "capabilityTokenExpiresAt");
        post.setCapabilityTokenHash(capabilityTokenHash);
        post.setCapabilityTokenExpiresAt(capabilityTokenExpiresAt != null
                ? ZonedDateTime.parse(capabilityTokenExpiresAt, ISO)
                : null);

        return post;
    }

    private static String getRequiredString(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.s() == null) {
            throw new IllegalArgumentException("Missing required attribute: " + key);
        }
        return value.s();
    }

    private static String getOptionalString(Map<String, AttributeValue> item, String key) {
        if (!item.containsKey(key)) {
            return null;
        }
        AttributeValue value = item.get(key);
        return (value != null && value.s() != null) ? value.s() : null;
    }
}
