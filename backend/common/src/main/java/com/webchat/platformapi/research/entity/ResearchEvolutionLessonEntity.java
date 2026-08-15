package com.webchat.platformapi.research.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_evolution_lesson")
public class ResearchEvolutionLessonEntity {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "stage_name", nullable = false, length = 50)
    private String stageName;

    @Column(name = "stage_number", nullable = false)
    private int stageNumber;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 10)
    private String severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "fix_applied", columnDefinition = "TEXT")
    private String fixApplied;

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

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public UUID getRunId() { return runId; }
    public void setRunId(UUID runId) { this.runId = runId; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public int getStageNumber() { return stageNumber; }
    public void setStageNumber(int stageNumber) { this.stageNumber = stageNumber; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFixApplied() { return fixApplied; }
    public void setFixApplied(String fixApplied) { this.fixApplied = fixApplied; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
