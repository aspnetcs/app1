-- Research Assistant module tables
-- Phase 1: Foundation

-- 1. Research Project: user's research workspace
CREATE TABLE research_project (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    name            VARCHAR(200) NOT NULL,
    topic           TEXT NOT NULL,
    domains         TEXT[],
    mode            VARCHAR(20) DEFAULT 'semi-auto',
    status          VARCHAR(20) DEFAULT 'draft',
    config_json     JSONB,
    created_at      TIMESTAMPTZ DEFAULT now(),
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- 2. Research Run: one pipeline execution
CREATE TABLE research_run (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES research_project(id),
    run_number      INT NOT NULL,
    current_stage   INT DEFAULT 1,
    status          VARCHAR(20) DEFAULT 'pending',
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    iteration       INT DEFAULT 1,
    quality_score   DOUBLE PRECISION,
    summary_json    JSONB,
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- 3. Research Stage Log: result of each stage execution
CREATE TABLE research_stage_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID NOT NULL REFERENCES research_run(id),
    stage_number    INT NOT NULL,
    stage_name      VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    elapsed_ms      BIGINT,
    input_json      JSONB,
    output_json     JSONB,
    artifacts_json  JSONB,
    error_message   TEXT,
    decision        VARCHAR(20),
    tokens_used     INT DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- 4. Research Paper: generated paper artifacts
CREATE TABLE research_paper (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id          UUID NOT NULL REFERENCES research_run(id),
    title           VARCHAR(300),
    abstract_text   TEXT,
    content_key     VARCHAR(500),
    bibtex_key      VARCHAR(500),
    latex_key       VARCHAR(500),
    pdf_key         VARCHAR(500),
    quality_score   DOUBLE PRECISION,
    quality_json    JSONB,
    iteration       INT DEFAULT 1,
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- 5. Evolution Lessons: cross-run learning
CREATE TABLE research_evolution_lesson (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES research_project(id),
    run_id          UUID REFERENCES research_run(id),
    stage_name      VARCHAR(50) NOT NULL,
    stage_number    INT NOT NULL,
    category        VARCHAR(20) NOT NULL,
    severity        VARCHAR(10) NOT NULL,
    description     TEXT NOT NULL,
    fix_applied     TEXT,
    created_at      TIMESTAMPTZ DEFAULT now()
);

-- Indexes
CREATE INDEX idx_rp_user ON research_project(user_id);
CREATE INDEX idx_rp_status ON research_project(status);
CREATE INDEX idx_rr_project ON research_run(project_id);
CREATE INDEX idx_rr_status ON research_run(status);
CREATE INDEX idx_rsl_run ON research_stage_log(run_id);
CREATE INDEX idx_rsl_stage ON research_stage_log(run_id, stage_number);
CREATE INDEX idx_rpaper_run ON research_paper(run_id);
CREATE INDEX idx_rel_project ON research_evolution_lesson(project_id);
CREATE INDEX idx_rel_stage ON research_evolution_lesson(project_id, stage_name);
