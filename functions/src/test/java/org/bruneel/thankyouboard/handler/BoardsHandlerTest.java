package org.bruneel.thankyouboard.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent.ProxyRequestContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import org.bruneel.thankyouboard.service.BoardsService;
import org.bruneel.thankyouboard.service.PdfJobService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class BoardsHandlerTest {

    private static final String ACCEPT_VERSION_1 = "application/json; version=1";

    @Test
    void getBoardById_allowsAnonymous() {
        BoardsService service = mock(BoardsService.class);
        BoardsHandler handler = new BoardsHandler(service);

        APIGatewayProxyResponseEvent expected = ResponseUtil.jsonResponse(200, Map.of("ok", true));
        when(service.getBoard("abc", null)).thenReturn(expected);

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/api/boards/abc")
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withPathParameters(Map.of("id", "abc"));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getHeaders()).containsKey("x-request-id");
        verify(service).getBoard("abc", null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void listBoards_requiresSubClaim() {
        BoardsService service = mock(BoardsService.class);
        BoardsHandler handler = new BoardsHandler(service);

        when(service.listBoards(null)).thenReturn(ResponseUtil.jsonResponse(401, Map.of("error", "Unauthorized")));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/api/boards")
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withRequestContext(new ProxyRequestContext());

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(401);
        verify(service).listBoards(null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void listBoards_extractsSubFromAuthorizerClaims() {
        BoardsService service = mock(BoardsService.class);
        BoardsHandler handler = new BoardsHandler(service);

        String sub = "auth0|user-1";
        when(service.listBoards(sub)).thenReturn(ResponseUtil.jsonResponse(200, Map.of("ok", true)));

        ProxyRequestContext rc = new ProxyRequestContext();
        rc.setAuthorizer(Map.of("claims", Map.of("sub", sub)));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/api/boards")
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withRequestContext(rc);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(service).listBoards(sub);
        verifyNoMoreInteractions(service);
    }

    @Test
    void createBoard_extractsSubFromAuthorizerJwtClaims() {
        BoardsService service = mock(BoardsService.class);
        BoardsHandler handler = new BoardsHandler(service);

        String sub = "auth0|user-2";
        when(service.createBoard("{\"title\":\"t\",\"recipientName\":\"r\"}", sub))
                .thenReturn(ResponseUtil.jsonResponse(200, Map.of("ok", true)));

        ProxyRequestContext rc = new ProxyRequestContext();
        rc.setAuthorizer(Map.of("jwt", Map.of("claims", Map.of("sub", sub))));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/api/boards")
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withBody("{\"title\":\"t\",\"recipientName\":\"r\"}")
                .withRequestContext(rc);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(service).createBoard("{\"title\":\"t\",\"recipientName\":\"r\"}", sub);
        verifyNoMoreInteractions(service);
    }

    @Test
    void updateBoard_routesToServiceWithSub() {
        BoardsService service = mock(BoardsService.class);
        BoardsHandler handler = new BoardsHandler(service);

        String sub = "auth0|user-1";
        when(service.updateBoard("abc", "{\"title\":\"t\",\"recipientName\":\"r\"}", sub))
                .thenReturn(ResponseUtil.jsonResponse(200, Map.of("ok", true)));

        ProxyRequestContext rc = new ProxyRequestContext();
        rc.setAuthorizer(Map.of("claims", Map.of("sub", sub)));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("PUT")
                .withPath("/api/boards/abc")
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withPathParameters(Map.of("id", "abc"))
                .withBody("{\"title\":\"t\",\"recipientName\":\"r\"}")
                .withRequestContext(rc);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(service).updateBoard("abc", "{\"title\":\"t\",\"recipientName\":\"r\"}", sub);
        verifyNoMoreInteractions(service);
    }

    @Test
    void deleteBoard_routesToServiceWithSub() {
        BoardsService service = mock(BoardsService.class);
        BoardsHandler handler = new BoardsHandler(service);

        String sub = "auth0|user-1";
        when(service.deleteBoard("abc", sub))
                .thenReturn(ResponseUtil.jsonResponse(204, Map.of()));

        ProxyRequestContext rc = new ProxyRequestContext();
        rc.setAuthorizer(Map.of("jwt", Map.of("claims", Map.of("sub", sub))));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("DELETE")
                .withPath("/api/boards/abc")
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withPathParameters(Map.of("id", "abc"))
                .withRequestContext(rc);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(204);
        verify(service).deleteBoard("abc", sub);
        verifyNoMoreInteractions(service);
    }

    @Test
    void downloadBoardPdf_routesToServiceWithSub() {
        BoardsService service = mock(BoardsService.class);
        BoardsHandler handler = new BoardsHandler(service);

        String sub = "auth0|user-1";
        when(service.downloadBoardPdf("abc", sub))
                .thenReturn(ResponseUtil.jsonResponse(200, Map.of("ok", true)));

        ProxyRequestContext rc = new ProxyRequestContext();
        rc.setAuthorizer(Map.of("claims", Map.of("sub", sub)));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/api/boards/abc/pdf")
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withPathParameters(Map.of("id", "abc"))
                .withRequestContext(rc);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(service).downloadBoardPdf("abc", sub);
        verifyNoMoreInteractions(service);
    }

    @Test
    void createPdfJob_routesToPdfJobServiceWithSub() {
        BoardsService service = mock(BoardsService.class);
        PdfJobService pdfJobService = mock(PdfJobService.class);
        BoardsHandler handler = new BoardsHandler(service, pdfJobService);

        String sub = "auth0|user-1";
        when(pdfJobService.createPdfJob(eq("abc"), eq(sub), anyString()))
                .thenReturn(ResponseUtil.jsonResponse(202, Map.of("status", "PENDING")));

        ProxyRequestContext rc = new ProxyRequestContext();
        rc.setAuthorizer(Map.of("claims", Map.of("sub", sub)));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("POST")
                .withPath("/api/boards/abc/pdf-jobs")
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withPathParameters(Map.of("id", "abc"))
                .withRequestContext(rc);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(202);
        assertThat(response.getHeaders()).containsKey("x-request-id");
        verify(pdfJobService).createPdfJob(eq("abc"), eq(sub), anyString());
        verifyNoInteractions(service);
    }

    @Test
    void getPdfJobStatus_routesToPdfJobServiceWithSub() {
        BoardsService service = mock(BoardsService.class);
        PdfJobService pdfJobService = mock(PdfJobService.class);
        BoardsHandler handler = new BoardsHandler(service, pdfJobService);

        String sub = "auth0|user-1";
        String jobId = "job-123";
        when(pdfJobService.getPdfJobStatus("abc", jobId, sub))
                .thenReturn(ResponseUtil.jsonResponse(200, Map.of("status", "RUNNING")));

        ProxyRequestContext rc = new ProxyRequestContext();
        rc.setAuthorizer(Map.of("claims", Map.of("sub", sub)));

        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent()
                .withHttpMethod("GET")
                .withPath("/api/boards/abc/pdf-jobs/" + jobId)
                .withHeaders(Map.of("Accept", ACCEPT_VERSION_1))
                .withPathParameters(Map.of("id", "abc"))
                .withRequestContext(rc);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertThat(response.getStatusCode()).isEqualTo(200);
        verify(pdfJobService).getPdfJobStatus("abc", jobId, sub);
        verifyNoInteractions(service);
    }
}

