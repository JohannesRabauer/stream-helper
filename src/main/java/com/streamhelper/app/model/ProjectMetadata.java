package com.streamhelper.app.model;

import java.time.OffsetDateTime;

public record ProjectMetadata(
        String id,
        String name,
        String schemaVersion,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {}
