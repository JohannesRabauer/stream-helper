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
}
