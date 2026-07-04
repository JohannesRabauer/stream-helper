package com.streamhelper.app.transcription;

import com.streamhelper.app.config.StreamHelperProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class YouTubeAudioDownloader {

    private final StreamHelperProperties properties;

    public YouTubeAudioDownloader(StreamHelperProperties properties) {
        this.properties = properties;
    }

    public Path downloadAudio(String youtubeUrl) {
        try {
            Path tempDir = Files.createTempDirectory("stream-helper-ytdlp-");
            ProcessBuilder processBuilder = new ProcessBuilder(
                    properties.getCommands().getYtDlpCommand(),
                    "-x",
                    "--audio-format",
                    "mp3",
                    "-o",
                    tempDir.resolve("%(id)s.%(ext)s").toString(),
                    youtubeUrl);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(Duration.ofMinutes(15).toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TranscriptionException("yt-dlp timed out while downloading audio");
            }
            if (process.exitValue() != 0) {
                throw new TranscriptionException("yt-dlp failed with exit code " + process.exitValue());
            }
            try (var files = Files.list(tempDir)) {
                return files.filter(Files::isRegularFile)
                        .max(Comparator.comparing(path -> path.toFile().lastModified()))
                        .orElseThrow(() -> new TranscriptionException("No downloaded audio file found"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Failed to download YouTube audio", exception);
        } catch (IOException exception) {
            throw new TranscriptionException("Failed to download YouTube audio", exception);
        }
    }
}
