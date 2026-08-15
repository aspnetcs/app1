package com.webchat.platformapi.research.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_paper")
public class ResearchPaperEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(length = 300)
    private String title;

    @Column(name = "abstract_text", columnDefinition = "TEXT")
    private String abstractText;

    @Column(name = "content_key", length = 500)
    private String contentKey;

    @Column(name = "bibtex_key", length = 500)
    private String bibtexKey;

    @Column(name = "latex_key", length = 500)
    private String latexKey;

    @Column(name = "pdf_key", length = 500)
    private String pdfKey;

    @Column(name = "quality_score")
    private Double qualityScore;

    @Column(name = "quality_json", columnDefinition = "JSONB")
    private String qualityJson;

    @Column
    private int iteration = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    // -- Getters and Setters --

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }

    public String getContentKey() { return contentKey; }
    public void setContentKey(String contentKey) { this.contentKey = contentKey; }

    public String getBibtexKey() { return bibtexKey; }
    public void setBibtexKey(String bibtexKey) { this.bibtexKey = bibtexKey; }

    public String getLatexKey() { return latexKey; }
    public void setLatexKey(String latexKey) { this.latexKey = latexKey; }

    public String getPdfKey() { return pdfKey; }
    public void setPdfKey(String pdfKey) { this.pdfKey = pdfKey; }

    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }

    public String getQualityJson() { return qualityJson; }
    public void setQualityJson(String qualityJson) { this.qualityJson = qualityJson; }

    public int getIteration() { return iteration; }
    public void setIteration(int iteration) { this.iteration = iteration; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
