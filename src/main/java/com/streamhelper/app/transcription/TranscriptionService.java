package com.streamhelper.app.transcription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamhelper.app.config.StreamHelperProperties;
import com.streamhelper.app.model.TranscriptEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranscriptionService {

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
        try {
            return transcribeBytes(file.getBytes(), file.getOriginalFilename(), language, diarize);
        } catch (IOException exception) {
            throw new TranscriptionException("Failed to read uploaded file", exception);
        }
    }

    public List<TranscriptEntry> transcribeYoutube(String youtubeUrl, String language, boolean diarize) {
        Path audio = downloader.downloadAudio(youtubeUrl);
        try {
            byte[] bytes = Files.readAllBytes(audio);
            return transcribeBytes(bytes, audio.getFileName().toString(), language, diarize);
        } catch (IOException exception) {
            throw new TranscriptionException("Failed to read downloaded audio", exception);
        }
    }

    private List<TranscriptEntry> transcribeBytes(byte[] data, String filename, String language, boolean diarize) {
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
        try {
            JsonNode node = restClient.post()
                    .uri(properties.getTranscription().getOpenai().getBaseUrl() + "/v1/audio/transcriptions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .body(JsonNode.class);
            return parseVerboseTranscript(node);
        } catch (Exception exception) {
            throw new TranscriptionException("OpenAI transcription failed", exception);
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
            String body = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipart)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(body == null ? "{}" : body);
            return parseVerboseTranscript(node);
        } catch (Exception exception) {
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
        return entries;
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
