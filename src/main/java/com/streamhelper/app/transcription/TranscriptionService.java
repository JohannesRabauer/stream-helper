package com.streamhelper.app.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhelper.app.config.StreamHelperProperties;
import com.streamhelper.app.config.TranscriptionProvider;
import com.streamhelper.app.model.TranscriptEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranscriptionService {

    private static final Logger logger = LoggerFactory.getLogger(TranscriptionService.class);
    private static final long OPENAI_MAX_AUDIO_BYTES = 25L * 1024L * 1024L;
    private static final int OPENAI_CHUNK_SECONDS = 540;
    private static final Duration FFMPEG_SPLIT_TIMEOUT = Duration.ofMinutes(20);

    private final StreamHelperProperties properties;
    private final YouTubeAudioDownloader downloader;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public TranscriptionService(
            StreamHelperProperties properties, YouTubeAudioDownloader downloader, ObjectMapper objectMapper) {
        this.properties = properties;
        this.downloader = downloader;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public List<TranscriptEntry> transcribeUpload(MultipartFile file, String language, boolean diarize) {
        String filename = file.getOriginalFilename() == null ? "audio" : file.getOriginalFilename();
        logger.info(
                "Starting upload transcription: provider={}, filename={}, sizeBytes={}, language={}, diarize={}",
                properties.getTranscription().getProvider(),
                filename,
                file.getSize(),
                language,
                diarize);
        if (properties.getTranscription().getProvider() == TranscriptionProvider.OPENAI) {
            Path tempFile = null;
            try {
                tempFile = Files.createTempFile("stream-helper-upload-", suffixFromFilename(filename));
                file.transferTo(tempFile);
                List<TranscriptEntry> entries = transcribeOpenAiFromPath(tempFile, filename, language);
                logger.info(
                        "Finished upload transcription: filename={}, entries={}, transcriptChars={}",
                        filename,
                        entries.size(),
                        countTranscriptChars(entries));
                return entries;
            } catch (IOException | IllegalStateException exception) {
                logger.error("Failed reading uploaded file bytes: filename={}", filename, exception);
                throw new TranscriptionException("Failed to read uploaded file", exception);
            } finally {
                deleteRecursively(tempFile);
            }
        }
        try {
            List<TranscriptEntry> entries = transcribeBytes(file.getBytes(), filename, language, diarize);
            logger.info(
                    "Finished upload transcription: filename={}, entries={}, transcriptChars={}",
                    filename,
                    entries.size(),
                    countTranscriptChars(entries));
            return entries;
        } catch (IOException exception) {
            logger.error("Failed reading uploaded file bytes: filename={}", filename, exception);
            throw new TranscriptionException("Failed to read uploaded file", exception);
        }
    }

    public List<TranscriptEntry> transcribeYoutube(String youtubeUrl, String language, boolean diarize) {
        logger.info(
                "Starting YouTube transcription: provider={}, url={}, language={}, diarize={}",
                properties.getTranscription().getProvider(),
                youtubeUrl,
                language,
                diarize);
        Path audio = downloader.downloadAudio(youtubeUrl);
        try {
            long size = Files.size(audio);
            List<TranscriptEntry> entries;
            if (properties.getTranscription().getProvider() == TranscriptionProvider.OPENAI) {
                entries = transcribeOpenAiFromPath(audio, audio.getFileName().toString(), language);
            } else {
                byte[] bytes = Files.readAllBytes(audio);
                entries = transcribeBytes(bytes, audio.getFileName().toString(), language, diarize);
            }
            logger.info(
                    "Finished YouTube transcription: url={}, audioFile={}, sizeBytes={}, entries={}, transcriptChars={}",
                    youtubeUrl,
                    audio.getFileName(),
                    size,
                    entries.size(),
                    countTranscriptChars(entries));
            return entries;
        } catch (IOException exception) {
            logger.error("Failed reading downloaded audio: url={}, path={}", youtubeUrl, audio, exception);
            throw new TranscriptionException("Failed to read downloaded audio", exception);
        }
    }

    private List<TranscriptEntry> transcribeOpenAiFromPath(Path audioPath, String filename, String language) throws IOException {
        long sizeBytes = Files.size(audioPath);
        if (sizeBytes <= OPENAI_MAX_AUDIO_BYTES) {
            logger.info("OpenAI transcription fits single request: filename={}, sizeBytes={}", filename, sizeBytes);
            return callOpenAi(Files.readAllBytes(audioPath), filename, language);
        }

        logger.info(
                "OpenAI transcription requires chunking: filename={}, sizeBytes={}, limitBytes={}, chunkSeconds={}",
                filename,
                sizeBytes,
                OPENAI_MAX_AUDIO_BYTES,
                OPENAI_CHUNK_SECONDS);

        Path chunkDir = splitAudioForOpenAi(audioPath);
        try (var files = Files.list(chunkDir)) {
            List<Path> chunks = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("chunk-"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            if (chunks.isEmpty()) {
                throw new TranscriptionException("ffmpeg chunking produced no audio chunks");
            }

            List<TranscriptEntry> merged = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                Path chunk = chunks.get(index);
                byte[] chunkBytes = Files.readAllBytes(chunk);
                double offsetSeconds = (double) index * OPENAI_CHUNK_SECONDS;
                List<TranscriptEntry> chunkEntries = callOpenAi(chunkBytes, chunk.getFileName().toString(), language);
                merged.addAll(offsetEntries(chunkEntries, offsetSeconds));
                logger.info(
                        "OpenAI chunk transcribed: index={}/{}, chunkFile={}, sizeBytes={}, chunkEntries={}, offsetSeconds={}",
                        index + 1,
                        chunks.size(),
                        chunk.getFileName(),
                        chunkBytes.length,
                        chunkEntries.size(),
                        offsetSeconds);
            }

            logger.info("OpenAI chunked transcription merged: chunks={}, mergedEntries={}", chunks.size(), merged.size());
            return merged;
        } finally {
            deleteRecursively(chunkDir);
        }
    }

    private List<TranscriptEntry> transcribeBytes(byte[] data, String filename, String language, boolean diarize) {
        logger.info(
                "Routing transcription request: provider={}, filename={}, payloadBytes={}, language={}, diarize={}",
                properties.getTranscription().getProvider(),
                filename,
                data.length,
                language,
                diarize);
        return switch (properties.getTranscription().getProvider()) {
            case OPENAI -> callOpenAi(data, filename, language);
            case WHISPER_LOCAL -> callWhisperLocal(data, filename, language, diarize);
        };
    }

    private List<TranscriptEntry> callOpenAi(byte[] data, String filename, String language) {
        String apiKey = properties.getTranscription().getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new TranscriptionException("OpenAI transcription requires API key");
        }
        enforceOpenAiSizeLimit(data.length, filename, "request");
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add("model", properties.getTranscription().getOpenai().getModel());
        multipart.add("response_format", "verbose_json");
        multipart.add("timestamp_granularities[]", "segment");
        if (language != null && !language.isBlank()) {
            multipart.add("language", language);
        }
        multipart.add(
                "file",
                new ByteArrayResource(data) {
                    @Override
                    public String getFilename() {
                        return filename == null ? "audio.mp3" : filename;
                    }
                });
        long startNanos = System.nanoTime();
        String uri = properties.getTranscription().getOpenai().getBaseUrl() + "/v1/audio/transcriptions";
        logger.info(
                "Calling OpenAI transcription: uri={}, model={}, filename={}, payloadBytes={}, languageProvided={}",
                uri,
                properties.getTranscription().getOpenai().getModel(),
                filename,
                data.length,
                language != null && !language.isBlank());
        try {
            JsonNode node = restClient.post()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .body(JsonNode.class);
            List<TranscriptEntry> entries = parseVerboseTranscript(node);
            logger.info(
                    "OpenAI transcription completed: filename={}, entries={}, durationMs={}",
                    filename,
                    entries.size(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return entries;
        } catch (HttpStatusCodeException exception) {
            String details = extractOpenAiErrorDetails(exception.getResponseBodyAsString());
            logger.error(
                    "OpenAI transcription HTTP failure: status={}, uri={}, details={}, responseBodySnippet={}",
                    exception.getStatusCode().value(),
                    uri,
                    details,
                    abbreviate(exception.getResponseBodyAsString(), 800),
                    exception);
            throw new TranscriptionException("OpenAI transcription failed: " + details, exception);
        } catch (Exception exception) {
            logger.error("OpenAI transcription failed unexpectedly: uri={}, filename={}", uri, filename, exception);
            throw new TranscriptionException("OpenAI transcription failed: " + exception.getMessage(), exception);
        }
    }

    private List<TranscriptEntry> callWhisperLocal(byte[] data, String filename, String language, boolean diarize) {
        MultiValueMap<String, Object> multipart = new LinkedMultiValueMap<>();
        multipart.add(
                "audio_file",
                new ByteArrayResource(data) {
                    @Override
                    public String getFilename() {
                        return filename == null ? "audio.mp3" : filename;
                    }
                });
        try {
            String uri = properties.getTranscription().getWhisperBaseUrl() + "/asr?output=json&task=transcribe";
            if (language != null && !language.isBlank()) {
                uri += "&language=" + language;
            }
            if (diarize) {
                uri += "&diarize=true";
            }
            long startNanos = System.nanoTime();
            logger.info(
                    "Calling local Whisper transcription: uri={}, filename={}, payloadBytes={}, language={}, diarize={}",
                    uri,
                    filename,
                    data.length,
                    language,
                    diarize);
            String body = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            List<TranscriptEntry> entries = parseVerboseTranscript(node);
            logger.info(
                    "Local Whisper transcription completed: filename={}, entries={}, durationMs={}",
                    filename,
                    entries.size(),
                    (System.nanoTime() - startNanos) / 1_000_000L);
            return entries;
        } catch (Exception exception) {
            logger.error("Local Whisper transcription failed: filename={}", filename, exception);
            throw new TranscriptionException("Local Whisper transcription failed", exception);
        }
    }

    private List<TranscriptEntry> parseVerboseTranscript(JsonNode node) {
        List<TranscriptEntry> entries = new ArrayList<>();
        JsonNode segments = node.path("segments");
        if (!segments.isArray()) {
            String text = node.path("text").asText("");
            if (!text.isBlank()) {
                entries.add(new TranscriptEntry(0, 0, "Unknown", text));
                logger.info("Parsed transcript from plain text response with 1 fallback segment");
            }
            return entries;
        }
        for (JsonNode segment : segments) {
            double start = segment.path("start").asDouble(0);
            double end = segment.path("end").asDouble(start);
            String speaker = segment.path("speaker").asText("Unknown");
            String text = segment.path("text").asText("").strip();
            if (!text.isBlank()) {
                entries.add(new TranscriptEntry(start, end, speaker, text));
            }
        }
        logger.info("Parsed verbose transcript response: segments={}", entries.size());
        return entries;
    }

    static String extractOpenAiErrorDetails(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "no error details returned by OpenAI";
        }
        try {
            JsonNode root = new ObjectMapper().readTree(responseBody);
            String message = root.path("error").path("message").asText("");
            if (!message.isBlank()) {
                return message.strip();
            }
            String fallback = root.path("message").asText("");
            if (!fallback.isBlank()) {
                return fallback.strip();
            }
        } catch (Exception ignored) {
            // Preserve raw response fallback below.
        }
        return abbreviate(responseBody.strip(), 240);
    }

    private Path splitAudioForOpenAi(Path inputAudio) {
        Path chunkDir = null;
        try {
            chunkDir = Files.createTempDirectory("stream-helper-openai-chunks-");
            Path ffmpegLog = chunkDir.resolve("ffmpeg-split.log");
            Path outputPattern = chunkDir.resolve("chunk-%03d.mp3");

            ProcessBuilder processBuilder = new ProcessBuilder(
                    properties.getCommands().getFfmpegCommand(),
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-i",
                    inputAudio.toString(),
                    "-vn",
                    "-ac",
                    "1",
                    "-ar",
                    "16000",
                    "-codec:a",
                    "libmp3lame",
                    "-b:a",
                    "48k",
                    "-f",
                    "segment",
                    "-segment_time",
                    String.valueOf(OPENAI_CHUNK_SECONDS),
                    outputPattern.toString());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(ffmpegLog.toFile());

            logger.info(
                    "Starting ffmpeg chunking for OpenAI: input={}, outputPattern={}, chunkSeconds={}, log={}",
                    inputAudio,
                    outputPattern,
                    OPENAI_CHUNK_SECONDS,
                    ffmpegLog);

            Process process = processBuilder.start();
            boolean finished = process.waitFor(FFMPEG_SPLIT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new TranscriptionException("ffmpeg timed out while splitting audio for OpenAI. Log tail: "
                        + readTail(ffmpegLog, 40));
            }
            if (process.exitValue() != 0) {
                throw new TranscriptionException("ffmpeg failed while splitting audio for OpenAI (exit code "
                        + process.exitValue() + "). Log tail: " + readTail(ffmpegLog, 40));
            }

            logger.info("ffmpeg chunking completed: input={}, chunkDir={}", inputAudio, chunkDir);
            return chunkDir;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TranscriptionException("Interrupted while splitting audio for OpenAI", exception);
        } catch (IOException exception) {
            throw new TranscriptionException("Failed to split audio for OpenAI transcription", exception);
        } catch (RuntimeException exception) {
            deleteRecursively(chunkDir);
            throw exception;
        }
    }

    private void enforceOpenAiSizeLimit(long sizeBytes, String filename, String source) {
        if (sizeBytes <= OPENAI_MAX_AUDIO_BYTES) {
            return;
        }
        String message = "OpenAI transcription file too large (%s MiB). Limit is 25 MiB for /v1/audio/transcriptions. File: %s (source=%s)."
                .formatted(toMiB(sizeBytes), filename, source);
        logger.warn(message);
        throw new TranscriptionException(message);
    }

    private static String toMiB(long bytes) {
        return String.format(Locale.ROOT, "%.2f", bytes / (1024.0 * 1024.0));
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private List<TranscriptEntry> offsetEntries(List<TranscriptEntry> entries, double offsetSeconds) {
        if (offsetSeconds <= 0) {
            return entries;
        }
        List<TranscriptEntry> shifted = new ArrayList<>(entries.size());
        for (TranscriptEntry entry : entries) {
            shifted.add(new TranscriptEntry(
                    entry.startSeconds() + offsetSeconds,
                    entry.endSeconds() + offsetSeconds,
                    entry.speaker(),
                    entry.text()));
        }
        return shifted;
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
            return "<failed to read log: " + exception.getMessage() + ">";
        }
        return String.join(System.lineSeparator(), tail);
    }

    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException exception) {
                    logger.warn("Failed to delete temporary file: {}", entry, exception);
                }
            });
        } catch (IOException exception) {
            logger.warn("Failed to walk temporary path for deletion: {}", path, exception);
        }
    }

    private static String suffixFromFilename(String filename) {
        if (filename == null) {
            return ".bin";
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return ".bin";
        }
        String suffix = filename.substring(dotIndex);
        if (!suffix.matches("\\.[A-Za-z0-9]{1,8}")) {
            return ".bin";
        }
        return suffix;
    }

    private static int countTranscriptChars(List<TranscriptEntry> entries) {
        int chars = 0;
        for (TranscriptEntry entry : entries) {
            chars += entry.text() == null ? 0 : entry.text().length();
        }
        return chars;
    }

    public String toPlainTranscript(List<TranscriptEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            builder.append("[%s - %s] %s: %s%n"
                    .formatted(
                            formatTimestamp(entry.startSeconds()),
                            formatTimestamp(entry.endSeconds()),
                            entry.speaker(),
                            entry.text()));
        }
        return builder.toString();
    }

    public String formatTimestamp(double secondsValue) {
        int totalSeconds = (int) Math.max(0, Math.floor(secondsValue));
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return "%02d:%02d:%02d".formatted(hours, minutes, seconds);
        }
        return "%02d:%02d".formatted(minutes, seconds);
    }
}
