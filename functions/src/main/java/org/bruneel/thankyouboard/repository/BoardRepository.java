package org.bruneel.thankyouboard.repository;

import org.bruneel.thankyouboard.model.Board;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BoardRepository {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String LEGACY_OWNER_ID = "legacy-owner";

    private final DynamoDbClient client;
    private final String tableName;

    public BoardRepository(String tableName) {
        this.client = DynamoDbClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .addExecutionInterceptor(new com.amazonaws.xray.interceptors.TracingInterceptor())
                        .build())
                .build();
        this.tableName = tableName;
    }

    public void save(Board board) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(board.getId().toString()).build());
        item.put("ownerId", AttributeValue.builder().s(board.getOwnerId()).build());
        item.put("title", AttributeValue.builder().s(board.getTitle()).build());
        item.put("recipientName", AttributeValue.builder().s(board.getRecipientName()).build());
        item.put("createdAt", AttributeValue.builder().s(board.getCreatedAt().format(ISO)).build());

        client.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    /**
     * Returns all boards using a full table scan.
     * Suitable for low board counts. For higher scale, consider pagination or caching.
     */
    public List<Board> findAll() {
        ScanResponse response = client.scan(ScanRequest.builder()
                .tableName(tableName)
                .build());

        List<Board> boards = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            boards.add(fromItem(item));
        }
        boards.sort(Comparator.comparing(Board::getCreatedAt));
        return boards;
    }

    public List<Board> findByOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return List.of();
        }

        Map<String, AttributeValue> values = Map.of(
                ":ownerId", AttributeValue.builder().s(ownerId).build()
        );

        try {
            QueryResponse response = client.query(QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("ownerId-createdAt-index")
                    .keyConditionExpression("ownerId = :ownerId")
                    .expressionAttributeValues(values)
                    .scanIndexForward(true)
                    .build());

            List<Board> boards = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                boards.add(fromItem(item));
            }
            return boards;
        } catch (DynamoDbException e) {
            // Backwards-compatible fallback when the GSI doesn't exist yet.
            ScanResponse response = client.scan(ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("ownerId = :ownerId")
                    .expressionAttributeValues(values)
                    .build());

            List<Board> boards = new ArrayList<>();
            for (Map<String, AttributeValue> item : response.items()) {
                boards.add(fromItem(item));
            }
            boards.sort(Comparator.comparing(Board::getCreatedAt));
            return boards;
        }
    }

    public long countByOwnerId(String ownerId) {
        if (ownerId == null || ownerId.isBlank()) {
            return 0L;
        }

        Map<String, AttributeValue> values = Map.of(
                ":ownerId", AttributeValue.builder().s(ownerId).build()
        );

        try {
            QueryResponse response = client.query(QueryRequest.builder()
                    .tableName(tableName)
                    .indexName("ownerId-createdAt-index")
                    .keyConditionExpression("ownerId = :ownerId")
                    .expressionAttributeValues(values)
                    .select(Select.COUNT)
                    .build());

            return response.count() != null ? response.count().longValue() : 0L;
        } catch (DynamoDbException e) {
            // Backwards-compatible fallback when the GSI doesn't exist yet.
            ScanResponse response = client.scan(ScanRequest.builder()
                    .tableName(tableName)
                    .filterExpression("ownerId = :ownerId")
                    .expressionAttributeValues(values)
                    .select(Select.COUNT)
                    .build());

            return response.count() != null ? response.count().longValue() : 0L;
        }
    }

    public Optional<Board> findById(UUID id) {
        GetItemResponse response = client.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.builder().s(id.toString()).build()))
                .build());

        if (response.item() == null || response.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromItem(response.item()));
    }

    public Board updateBoard(UUID id, String ownerId, String title, String recipientName) {
        Map<String, AttributeValue> key = Map.of(
                "id", AttributeValue.builder().s(id.toString()).build()
        );

        Map<String, String> names = Map.of(
                "#title", "title",
                "#recipientName", "recipientName"
        );

        Map<String, AttributeValue> values = Map.of(
                ":ownerId", AttributeValue.builder().s(ownerId).build(),
                ":title", AttributeValue.builder().s(title).build(),
                ":recipientName", AttributeValue.builder().s(recipientName).build()
        );

        UpdateItemResponse response = client.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET #title = :title, #recipientName = :recipientName")
                .conditionExpression("ownerId = :ownerId")
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .returnValues(ReturnValue.ALL_NEW)
                .build());

        return fromItem(response.attributes());
    }

    public void deleteBoard(UUID id, String ownerId) {
        Map<String, AttributeValue> key = Map.of(
                "id", AttributeValue.builder().s(id.toString()).build()
        );

        Map<String, AttributeValue> values = Map.of(
                ":ownerId", AttributeValue.builder().s(ownerId).build()
        );

        client.deleteItem(DeleteItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .conditionExpression("ownerId = :ownerId")
                .expressionAttributeValues(values)
                .build());
    }

    private static Board fromItem(Map<String, AttributeValue> item) {
        String id = getRequiredString(item, "id");
        String ownerId = getOptionalString(item, "ownerId", LEGACY_OWNER_ID);
        String title = getRequiredString(item, "title");
        String recipientName = getRequiredString(item, "recipientName");
        String createdAt = getRequiredString(item, "createdAt");
        return new Board(
                UUID.fromString(id),
                ownerId,
                title,
                recipientName,
                ZonedDateTime.parse(createdAt, ISO)
        );
    }

    private static String getRequiredString(Map<String, AttributeValue> item, String key) {
        AttributeValue value = item.get(key);
        if (value == null || value.s() == null) {
            throw new IllegalArgumentException("Missing required attribute: " + key);
        }
        return value.s();
    }

    private static String getOptionalString(Map<String, AttributeValue> item, String key, String defaultValue) {
        AttributeValue value = item.get(key);
        if (value == null || value.s() == null || value.s().isBlank()) {
            return defaultValue;
        }
        return value.s();
    }
}
