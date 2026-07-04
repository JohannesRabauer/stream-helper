package com.streamhelper.app.transcription;

import static org.assertj.core.api.Assertions.assertThat;

import com.streamhelper.app.config.StreamHelperProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class YouTubeAudioDownloaderTest {

    @Test
    void buildsYtDlpCommandWithRuntimeAndExtractorArgs() {
        StreamHelperProperties properties = new StreamHelperProperties();
        YouTubeAudioDownloader downloader = new YouTubeAudioDownloader(properties);

        var command = downloader.buildYtDlpCommand(Path.of("/tmp/audio"), "https://youtube.com/live/abc123");

        assertThat(command).contains("--js-runtimes", "node,deno");
        assertThat(command).contains("--extractor-args", "youtube:player_client=android,web");
        assertThat(command).contains("--retries", "3");
        assertThat(command).contains("--fragment-retries", "3");
    }

    @Test
    void createsHelpfulErrorMessageForHttp403() {
        StreamHelperProperties properties = new StreamHelperProperties();
        YouTubeAudioDownloader downloader = new YouTubeAudioDownloader(properties);

        String message = downloader.buildFailureMessage(1, "ERROR: unable to download video data: HTTP Error 403: Forbidden");

        assertThat(message).contains("HTTP 403");
        assertThat(message).contains("upload the file directly");
    }
}
