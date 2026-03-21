package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.bruneel.thankyouboard.service.PostsService;
import org.bruneel.thankyouboard.security.Auth0JwtSubVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import software.amazon.lambda.powertools.logging.Logging;
import software.amazon.lambda.powertools.tracing.Tracing;
import software.amazon.lambda.powertools.tracing.TracingUtils;

import java.util.Map;

public class PostsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger log = LoggerFactory.getLogger(PostsHandler.class);
    private static final String POST_EDIT_DELETE_CAPABILITY_HEADER = "X-Post-Capability-Token";
    private final PostsService postsService;
    private final Auth0JwtSubVerifier jwtSubVerifier;

    public PostsHandler() {
        String postsTable = System.getenv("POSTS_TABLE");
        String boardsTable = System.getenv("BOARDS_TABLE");
        if (postsTable == null || boardsTable == null) {
            throw new IllegalStateException("POSTS_TABLE and BOARDS_TABLE must be set");
        }
        this.postsService = new PostsService(postsTable, boardsTable);

        String auth0Domain = System.getenv("AUTH0_DOMAIN");
        String auth0Audience = System.getenv("AUTH0_AUDIENCE");
        this.jwtSubVerifier = (auth0Domain != null && auth0Audience != null && !auth0Domain.isBlank() && !auth0Audience.isBlank())
                ? new Auth0JwtSubVerifier(auth0Domain, auth0Audience)
                : null;
    }

    @Override
    @Logging(clearState = true)
    @Tracing
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String correlationId = CorrelationContext.init(event, context);

        String method = event.getHttpMethod();
        log.info("Posts {} {}", method, event.getPath());

        if (!ResponseUtil.validateAcceptHeader(event)) {
            APIGatewayProxyResponseEvent resp = ResponseUtil.jsonResponse(400,
                    Map.of("error", "Missing or invalid Accept header. Required: application/json; version=1"));
            ResponseUtil.stampCorrelationId(resp, correlationId);
            return resp;
        }

        APIGatewayProxyResponseEvent response;
        String boardId = null;
        String postId = null;
        try {
            Map<String, String> pathParams = event.getPathParameters();
            boardId = pathParams != null ? pathParams.get("id") : null;
            postId = pathParams != null ? pathParams.get("postId") : null;

            if (boardId != null) {
                MDC.put("board_id", boardId);
                TracingUtils.putAnnotation("board_id", boardId);
            }
            if (postId != null) {
                MDC.put("post_id", postId);
                TracingUtils.putAnnotation("post_id", postId);
            }

            if ("GET".equals(method) && boardId != null) {
                response = postsService.listPostsByBoard(boardId);
            } else if ("POST".equals(method)) {
                if (boardId == null) {
                    log.warn("boardId required in path for POST. Path must be /api/boards/{boardId}/posts. path={}", event.getPath());
                    response = ResponseUtil.jsonResponse(400, Map.of("error", "boardId required in path"));
                } else {
                    response = postsService.createPost(boardId, event.getBody());
                }
            } else if ("PUT".equals(method) && boardId != null && postId != null) {
                String ownerId = extractOwnerId(event);
                String capabilityToken = extractCapabilityToken(event);
                TracingUtils.putAnnotation("action", "update_post");
                response = postsService.updatePost(boardId, postId, event.getBody(), ownerId, capabilityToken);
            } else if ("DELETE".equals(method) && boardId != null && postId != null) {
                String ownerId = extractOwnerId(event);
                String capabilityToken = extractCapabilityToken(event);
                TracingUtils.putAnnotation("action", "delete_post");
                response = postsService.deletePost(boardId, postId, ownerId, capabilityToken);
            } else {
                log.warn("Method not allowed: {} {}. For posts use GET/POST /api/boards/{id}/posts or PUT/DELETE /api/boards/{id}/posts/{postId}.", method, event.getPath());
                response = ResponseUtil.jsonResponse(405, Map.of("error", "Method not allowed"));
            }
        } catch (Exception e) {
            log.error("Error handling posts request", e);
            response = ResponseUtil.jsonResponse(500,
                    Map.of("error", "Internal server error", "correlationId", correlationId));
        }

        ResponseUtil.stampCorrelationId(response, correlationId);
        MDC.put("status", String.valueOf(response.getStatusCode()));
        int status = response.getStatusCode();
        if (status >= 400 && status < 500) {
            log.info("Posts response status={} path={} boardId={} postId={}", status, event.getPath(), boardId, postId);
        } else {
            log.info("Posts response status={}", status);
        }
        return response;
    }

    private static String extractSub(APIGatewayProxyRequestEvent event) {
        Map<String, Object> authorizer = event.getRequestContext() != null ? event.getRequestContext().getAuthorizer() : null;
        if (authorizer == null) return null;

        Object claimsObj = authorizer.get("claims");
        if (claimsObj instanceof Map<?, ?> claims) {
            Object sub = claims.get("sub");
            return sub != null ? sub.toString() : null;
        }
        Object jwtObj = authorizer.get("jwt");
        if (jwtObj instanceof Map<?, ?> jwt) {
            Object claimsObj2 = jwt.get("claims");
            if (claimsObj2 instanceof Map<?, ?> jwtClaims) {
                Object sub = jwtClaims.get("sub");
                return sub != null ? sub.toString() : null;
            }
        }
        return null;
    }

    // Case-insensitive because API Gateway may lowercase header names.
    // Package-private for unit testing.
    static String extractCapabilityToken(APIGatewayProxyRequestEvent event) {
        Map<String, String> headers = event.getHeaders();
        if (headers == null || headers.isEmpty()) return null;

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            if (headerName == null || entry.getValue() == null) continue;
            if (!headerName.equalsIgnoreCase(POST_EDIT_DELETE_CAPABILITY_HEADER)) continue;

            String token = entry.getValue().trim();
            return token.isEmpty() ? null : token;
        }

        return null;
    }

    private String extractOwnerId(APIGatewayProxyRequestEvent event) {
        String subFromAuthorizer = extractSub(event);
        if (subFromAuthorizer != null) return subFromAuthorizer;

        if (jwtSubVerifier == null) return null;

        Map<String, String> headers = event.getHeaders();
        if (headers == null) return null;

        // Headers can arrive in different casing depending on the client/proxy.
        String auth = headers.getOrDefault("Authorization", headers.get("authorization"));
        if (auth == null || auth.isBlank()) return null;
        auth = auth.trim();
        if (!auth.startsWith("Bearer ")) return null;

        String token = auth.substring("Bearer ".length()).trim();
        if (token.isEmpty()) return null;

        return jwtSubVerifier.verifyAndExtractSub(token);
    }

}
