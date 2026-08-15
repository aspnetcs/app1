package com.webchat.platformapi.knowledge;

import java.util.UUID;

public interface KnowledgeChunkSearchRow {
    UUID getId();
    UUID getBaseId();
    UUID getDocumentId();
    Integer getChunkNo();
    String getChunkText();
    String getMetadataJson();
    Double getSimilarity();
}
