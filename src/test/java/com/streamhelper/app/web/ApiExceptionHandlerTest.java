package com.streamhelper.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {

    @Test
    void mapsMaxUploadSizeExceptionToPayloadTooLargeResponse() {
        ApiExceptionHandler handler = new ApiExceptionHandler();

        var response = handler.uploadTooLarge(new MaxUploadSizeExceededException(1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).containsEntry("error", "UPLOAD_TOO_LARGE");
        assertThat(response.getBody().get("message").toString()).contains("too large");
    }
}
