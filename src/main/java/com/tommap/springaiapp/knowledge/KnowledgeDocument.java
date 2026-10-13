package com.tommap.springaiapp.knowledge;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "knowledge_docs")
class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;
    private String contentHash;
    private Instant uploadedAt;
    private int chunkCount;

    protected KnowledgeDocument() {
    }

    KnowledgeDocument(String filename, String contentHash, Instant uploadedAt, int chunkCount) {
        this.filename = filename;
        this.contentHash = contentHash;
        this.uploadedAt = uploadedAt;
        this.chunkCount = chunkCount;
    }

    Long getId() {
        return id;
    }

    String getFilename() {
        return filename;
    }

    Instant getUploadedAt() {
        return uploadedAt;
    }

    int getChunkCount() {
        return chunkCount;
    }
}
