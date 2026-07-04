package com.streamhelper.app.transcription;

@FunctionalInterface
public interface TranscriptionProgressListener {

    TranscriptionProgressListener NOOP = (percent, stage, message) -> {};

    void update(int percent, String stage, String message);
}
