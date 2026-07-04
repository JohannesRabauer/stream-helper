package com.streamhelper.app.transcription;

import com.streamhelper.app.config.StreamHelperProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class YouTubeAudioDownloader {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeAudioDownloader.class);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(15);
    private static final String YOUTUBE_EXTRACTOR_ARGS = "youtube:player_client=android,web";

    private final StreamHelperProperties properties;

    public YouTubeAudioDownloader(StreamHelperProperties properties) {
        this.properties = properties;
    }

    public Path downloadAudio(String youtubeUrl) {
        return downloadAudio(youtubeUrl, TranscriptionProgressListener.NOOP);
    }

    public Path downloadAudio(String youtubeUrl, TranscriptionProgressListener progressListener) {
        try {
            Path tempDir = Files.createTempDirectory("stream-helper-ytdlp-");
            Path ytDlpLog = tempDir.resolve("yt-dlp.log");
            List<String> command = buildYtDlpCommand(tempDir, youtubeUrl);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ytDlpLog.toFile());
            progressListener.update(12, "download", "Starting YouTube audio download...");
            logger.info(
                    "Starting yt-dlp download: url={}, command={}, outputTemplate={}, log={}, jsRuntimes=node,deno",
                    youtubeUrl,
                    properties.getCommands().getYtDlpCommand(),
                    tempDir.resolve("%(id)s.%(ext)s"),
                    ytDlpLog);
            Process process = processBuilder.start();
            boolean finished = process.waitFor(DOWNLOAD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                logger.error("yt-dlp timed out after {}: url={}, logTail={}", DOWNLOAD_TIMEOUT, youtubeUrl, readTail(ytDlpLog, 40));
                throw new TranscriptionException("yt-dlp timed out while downloading audio");
            }
            if (process.exitValue() != 0) {
                String logTail = readTail(ytDlpLog, 40);
                logger.error(
                        "yt-dlp failed: url={}, exitCode={}, logTail={}",
                        youtubeUrl,
                        process.exitValue(),
                        logTail);
                throw new TranscriptionException(buildFailureMessage(process.exitValue(), logTail));
            }
            try (var files = Files.list(tempDir)) {
                Path downloaded = files.filter(Files::isRegularFile)
                        .filter(path -> !path.equals(ytDlpLog))
                        .max(Comparator.comparing(path -> path.toFile().lastModified()))
                        .orElseThrow(() -> new TranscriptionException("No downloaded audio file found"));
                progressListener.update(30, "download", "YouTube audio downloaded. Preparing transcription...");
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

    List<String> buildYtDlpCommand(Path tempDir, String youtubeUrl) {
        List<String> command = new ArrayList<>();
        command.add(properties.getCommands().getYtDlpCommand());
        command.add("--no-update");
        command.add("--no-progress");
        command.add("--retries");
        command.add("3");
        command.add("--fragment-retries");
        command.add("3");
        command.add("--retry-sleep");
        command.add("2");
        command.add("--force-ipv4");
        command.add("--js-runtimes");
        command.add("node,deno");
        command.add("--extractor-args");
        command.add(YOUTUBE_EXTRACTOR_ARGS);
        command.add("-x");
        command.add("--audio-format");
        command.add("mp3");
        command.add("-o");
        command.add(tempDir.resolve("%(id)s.%(ext)s").toString());
        command.add(youtubeUrl);
        return command;
    }

    String buildFailureMessage(int exitCode, String logTail) {
        String normalized = logTail == null ? "" : logTail;
        if (normalized.contains("HTTP Error 403")) {
            return "yt-dlp failed with exit code " + exitCode
                    + ": YouTube blocked the download request (HTTP 403). Retry shortly; if it keeps happening, use a different URL or upload the file directly.";
        }
        if (normalized.contains("No supported JavaScript runtime could be found")) {
            return "yt-dlp failed with exit code " + exitCode
                    + ": JavaScript runtime for YouTube extraction is missing. Install/enable Node.js for yt-dlp and retry.";
        }
        return "yt-dlp failed with exit code " + exitCode;
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
