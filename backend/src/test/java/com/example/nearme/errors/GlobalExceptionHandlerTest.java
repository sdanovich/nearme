package com.example.nearme.errors;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock ErrorPublisher publisher;
    @Mock HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(publisher);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bodyOf(ResponseEntity<?> r) {
        return (Map<String, Object>) r.getBody();
    }

    @Test
    void badRequestReturns400AndCapturesHttpError() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/prices");

        ResponseEntity<?> resp = handler.handleBadRequest(
                new IllegalArgumentException("bad price"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(bodyOf(resp)).containsEntry("error", "bad price");

        ArgumentCaptor<ErrorEvent> ev = ArgumentCaptor.forClass(ErrorEvent.class);
        verify(publisher).capture(ev.capture());
        assertThat(ev.getValue().kind()).isEqualTo("HTTP_ERROR");
        assertThat(ev.getValue().httpStatus()).isEqualTo(400);
        assertThat(ev.getValue().method()).isEqualTo("POST");
        assertThat(ev.getValue().path()).isEqualTo("/api/prices");
        assertThat(ev.getValue().exception()).isEqualTo(IllegalArgumentException.class.getName());
        assertThat(ev.getValue().message()).isEqualTo("bad price");
    }

    @Test
    void unexpectedReturns500WithGenericBodyAndCapturesException() {
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/stations/nearby");

        ResponseEntity<?> resp = handler.handleUnexpected(
                new RuntimeException("kaboom"), request);

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(bodyOf(resp)).containsEntry("error", "Internal server error");

        ArgumentCaptor<ErrorEvent> ev = ArgumentCaptor.forClass(ErrorEvent.class);
        verify(publisher).capture(ev.capture());
        assertThat(ev.getValue().kind()).isEqualTo("EXCEPTION");
        assertThat(ev.getValue().httpStatus()).isEqualTo(500);
    }

    @Test
    void nullRequestIsHandledGracefully() {
        ResponseEntity<?> resp = handler.handleBadRequest(
                new IllegalArgumentException("no request"), null);

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        ArgumentCaptor<ErrorEvent> ev = ArgumentCaptor.forClass(ErrorEvent.class);
        verify(publisher).capture(ev.capture());
        assertThat(ev.getValue().method()).isNull();
        assertThat(ev.getValue().path()).isNull();
    }

    @Test
    void captureFailureNeverMasksOriginalResponse() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/prices");
        doThrow(new RuntimeException("publish exploded")).when(publisher).capture(any());

        ResponseEntity<?> resp = assertThatReturns(() ->
                handler.handleBadRequest(new IllegalArgumentException("bad"), request));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    // small helper so the lambda above reads cleanly and any throw fails the test
    private ResponseEntity<?> assertThatReturns(java.util.function.Supplier<ResponseEntity<?>> call) {
        ResponseEntity<?>[] holder = new ResponseEntity<?>[1];
        assertThatCode(() -> holder[0] = call.get()).doesNotThrowAnyException();
        return holder[0];
    }
}
