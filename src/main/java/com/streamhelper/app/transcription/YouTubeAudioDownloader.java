package com.streamhelper.app.transcription;

import com.streamhelper.app.config.StreamHelperProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class YouTubeAudioDownloader {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeAudioDownloader.class);

    private final StreamHelperProperties properties;

    public YouTubeAudioDownloader(StreamHelperProperties properties) {
        this.properties = properties;
    }

    public Path downloadAudio(String youtubeUrl) {
        try {
            Path tempDir = Files.createTempDirectory("stream-helper-ytdlp-");
            Path ytDlpLog = tempDir.resolve("yt-dlp.log");
            ProcessBuilder processBuilder = new ProcessBuilder(
                    properties.getCommands().getYtDlpCommand(),
                    "--no-update",
                    "-x",
                    "--audio-format",
                    "mp3",
                    "-o",
                    tempDir.resolve("%(id)s.%(ext)s").toString(),
                    youtubeUrl);
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ytDlpLog.toFile());
            logger.info(
                    "Starting yt-dlp download: url={}, command={}, outputTemplate={}, log={}",
                    youtubeUrl,
                    properties.getCommands().getYtDlpCommand(),
                    tempDir.resolve("%(id)s.%(ext)s"),
                    ytDlpLog);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(Duration.ofMinutes(15).toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.error("yt-dlp timed out after 15 minutes: url={}, logTail={}", youtubeUrl, readTail(ytDlpLog, 40));
                throw new TranscriptionException("yt-dlp timed out while downloading audio");
            }
            if (process.exitValue() != 0) {
                logger.error(
                        "yt-dlp failed: url={}, exitCode={}, logTail={}",
                        youtubeUrl,
                        process.exitValue(),
                        readTail(ytDlpLog, 40));
                throw new TranscriptionException("yt-dlp failed with exit code " + process.exitValue());
            }
            try (var files = Files.list(tempDir)) {
                Path downloaded = files.filter(Files::isRegularFile)
                        .filter(path -> !path.equals(ytDlpLog))
                        .max(Comparator.comparing(path -> path.toFile().lastModified()))
                        .orElseThrow(() -> new TranscriptionException("No downloaded audio file found"));
                logger.info(
                        "yt-dlp download completed: url={}, file={}, sizeBytes={}",
                        youtubeUrl,
                        downloaded.getFileName(),
                        Files.size(downloaded));
                return downloaded;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while downloading YouTube audio: url={}", youtubeUrl, exception);
            throw new TranscriptionException("Failed to download YouTube audio", exception);
        } catch (IOException exception) {
            logger.error("I/O failure while downloading YouTube audio: url={}", youtubeUrl, exception);
            throw new TranscriptionException("Failed to download YouTube audio", exception);
        }
    }

    private String readTail(Path logFile, int maxLines) {
        if (!Files.exists(logFile)) {
            return "<no log file>";
        }
        ConcurrentLinkedDeque<String> tail = new ConcurrentLinkedDeque<>();
        try (var lines = Files.lines(logFile)) {
            lines.forEach(line -> {
                tail.addLast(line);
                if (tail.size() > maxLines) {
                    tail.removeFirst();
                }
            });
        } catch (IOException exception) {
            return "<failed to read yt-dlp log: " + exception.getMessage() + ">";
        }
        return String.join(System.lineSeparator(), tail);
    }
}
