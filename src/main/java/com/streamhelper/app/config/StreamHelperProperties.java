package com.streamhelper.app.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "stream-helper")
public class StreamHelperProperties {

    private final Storage storage = new Storage();
    private final Ai ai = new Ai();
    private final Transcription transcription = new Transcription();
    private final Commands commands = new Commands();

    public Storage getStorage() {
        return storage;
    }

    public Ai getAi() {
        return ai;
    }

    public Transcription getTranscription() {
        return transcription;
    }

    public Commands getCommands() {
        return commands;
    }

    public static class Storage {
        @NotNull
        private Path dataDir = Path.of("./data");

        public Path getDataDir() {
            return dataDir;
        }

        public void setDataDir(Path dataDir) {
            this.dataDir = dataDir;
        }
    }

    public static class Ai {
        @NotNull
        private Provider provider = Provider.OLLAMA;
        private int timeoutSeconds = 120;
        private final Ollama ollama = new Ollama();
        private final OpenAi openai = new OpenAi();

        public Provider getProvider() {
            return provider;
        }

        public void setProvider(Provider provider) {
            this.provider = provider;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public Ollama getOllama() {
            return ollama;
        }

        public OpenAi getOpenai() {
            return openai;
        }
    }

    public static class Ollama {
        @NotBlank
        private String baseUrl = "http://localhost:11434";
        @NotBlank
        private String model = "llama3.1:8b";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class OpenAi {
        @NotBlank
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        @NotBlank
        private String chatModel = "gpt-4.1-mini";
        @NotBlank
        private String imageModel = "gpt-image-1";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getChatModel() {
            return chatModel;
        }

        public void setChatModel(String chatModel) {
            this.chatModel = chatModel;
        }

        public String getImageModel() {
            return imageModel;
        }

        public void setImageModel(String imageModel) {
            this.imageModel = imageModel;
        }
    }

    public static class Transcription {
        @NotNull
        private TranscriptionProvider provider = TranscriptionProvider.WHISPER_LOCAL;
        private String whisperBaseUrl = "http://localhost:9000";
        private final OpenAiTranscription openai = new OpenAiTranscription();

        public TranscriptionProvider getProvider() {
            return provider;
        }

        public void setProvider(TranscriptionProvider provider) {
            this.provider = provider;
        }

        public String getWhisperBaseUrl() {
            return whisperBaseUrl;
        }

        public void setWhisperBaseUrl(String whisperBaseUrl) {
            this.whisperBaseUrl = whisperBaseUrl;
        }

        public OpenAiTranscription getOpenai() {
            return openai;
        }
    }

    public static class OpenAiTranscription {
        @NotBlank
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        @NotBlank
        private String model = "whisper-1";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Commands {
        private String ytDlpCommand = "yt-dlp";
        private String ffmpegCommand = "ffmpeg";

        public String getYtDlpCommand() {
            return ytDlpCommand;
        }

        public void setYtDlpCommand(String ytDlpCommand) {
            this.ytDlpCommand = ytDlpCommand;
        }

        public String getFfmpegCommand() {
            return ffmpegCommand;
        }

        public void setFfmpegCommand(String ffmpegCommand) {
            this.ffmpegCommand = ffmpegCommand;
        }
    }
}
