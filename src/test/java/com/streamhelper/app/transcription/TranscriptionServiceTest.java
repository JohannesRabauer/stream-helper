package com.streamhelper.app.transcription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhelper.app.config.StreamHelperProperties;
import org.junit.jupiter.api.Test;

class TranscriptionServiceTest {

    @Test
    void formatsTimestampsForMinutesAndHours() {
        StreamHelperProperties properties = new StreamHelperProperties();
        TranscriptionService service = new TranscriptionService(properties, mock(YouTubeAudioDownloader.class), new ObjectMapper());

        assertThat(service.formatTimestamp(65)).isEqualTo("01:05");
        assertThat(service.formatTimestamp(3700)).isEqualTo("01:01:40");
    }

    @Test
    void extractsOpenAiErrorMessageFromJsonResponse() {
        String responseBody = """
                {"error":{"message":"Maximum content size limit (26214400) exceeded","type":"invalid_request_error"}}
                """;

        String details = TranscriptionService.extractOpenAiErrorDetails(responseBody);

        assertThat(details).isEqualTo("Maximum content size limit (26214400) exceeded");
    }

    @Test
    void fallsBackToRawErrorBodyWhenJsonCannotBeParsed() {
        String responseBody = "OpenAI gateway timeout while processing transcription";

        String details = TranscriptionService.extractOpenAiErrorDetails(responseBody);

        assertThat(details).isEqualTo("OpenAI gateway timeout while processing transcription");
    }
}
