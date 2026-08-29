-- =============================================================================
-- V1 - Core schema owned by the middleware service.
--
-- Conventions:
--   * Primary keys are application-generated UUIDs (no pgcrypto dependency, so
--     the runtime DB user needs no extension-creation privilege on RDS).
--   * All timestamps are timestamptz stored in UTC.
--   * `version` backs JPA optimistic locking.
--   * Enumerations are varchar + CHECK constraint: readable in psql, and a new
--     value is a migration rather than a native type alteration.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- users: platform accounts. Roles drive Spring Security authorization.
-- -----------------------------------------------------------------------------
CREATE TABLE users (
    id            uuid         NOT NULL,
    email         varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    full_name     varchar(150) NOT NULL,
    role          varchar(20)  NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    last_login_at timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    version       bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'INTERVIEWER', 'CANDIDATE'))
);

CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_enabled ON users (enabled) WHERE enabled = true;

COMMENT ON TABLE users IS 'Platform accounts; password_hash is BCrypt.';

-- -----------------------------------------------------------------------------
-- candidates: people being interviewed.
-- -----------------------------------------------------------------------------
CREATE TABLE candidates (
    id                  uuid         NOT NULL,
    first_name          varchar(80)  NOT NULL,
    last_name           varchar(80)  NOT NULL,
    email               varchar(255) NOT NULL,
    phone               varchar(30),
    current_company     varchar(150),
    current_position    varchar(150),
    years_of_experience numeric(4, 1) NOT NULL DEFAULT 0,
    primary_skill       varchar(100) NOT NULL,
    location            varchar(120),
    status              varchar(20)  NOT NULL DEFAULT 'NEW',
    notes               text,
    created_by          uuid,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_candidates PRIMARY KEY (id),
    CONSTRAINT uq_candidates_email UNIQUE (email),
    CONSTRAINT fk_candidates_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_candidates_status CHECK (status IN
        ('NEW', 'SCREENING', 'INTERVIEWING', 'OFFERED', 'HIRED', 'REJECTED', 'ON_HOLD')),
    CONSTRAINT ck_candidates_experience CHECK (years_of_experience >= 0 AND years_of_experience <= 60)
);

CREATE INDEX idx_candidates_status ON candidates (status);
CREATE INDEX idx_candidates_primary_skill ON candidates (primary_skill);
CREATE INDEX idx_candidates_created_at ON candidates (created_at DESC);
-- Supports the case-insensitive search in CandidateRepository.
CREATE INDEX idx_candidates_last_name_lower ON candidates (lower(last_name));
CREATE INDEX idx_candidates_first_name_lower ON candidates (lower(first_name));
CREATE INDEX idx_candidates_email_lower ON candidates (lower(email));

-- -----------------------------------------------------------------------------
-- resumes: file metadata only. Bytes live in local disk (dev) or S3 (prod);
-- storage_type records which backend produced storage_key.
-- -----------------------------------------------------------------------------
CREATE TABLE resumes (
    id                uuid         NOT NULL,
    candidate_id      uuid         NOT NULL,
    original_filename varchar(255) NOT NULL,
    storage_type      varchar(20)  NOT NULL,
    storage_key       varchar(512) NOT NULL,
    content_type      varchar(120) NOT NULL,
    size_bytes        bigint       NOT NULL,
    checksum_sha256   varchar(64)  NOT NULL,
    uploaded_by       uuid,
    uploaded_at       timestamptz  NOT NULL DEFAULT now(),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_resumes PRIMARY KEY (id),
    CONSTRAINT fk_resumes_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_resumes_uploaded_by FOREIGN KEY (uploaded_by)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_resumes_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_resumes_storage_type CHECK (storage_type IN ('LOCAL', 'S3')),
    CONSTRAINT ck_resumes_size CHECK (size_bytes > 0)
);

CREATE INDEX idx_resumes_candidate_id ON resumes (candidate_id);
CREATE INDEX idx_resumes_uploaded_at ON resumes (uploaded_at DESC);

-- -----------------------------------------------------------------------------
-- interviews: a scheduled interview of one candidate by one interviewer.
-- -----------------------------------------------------------------------------
CREATE TABLE interviews (
    id               uuid         NOT NULL,
    candidate_id     uuid         NOT NULL,
    interviewer_id   uuid,
    title            varchar(180) NOT NULL,
    role_title       varchar(150) NOT NULL,
    experience_level varchar(20)  NOT NULL,
    round_number     integer      NOT NULL DEFAULT 1,
    scheduled_at     timestamptz  NOT NULL,
    duration_minutes integer      NOT NULL DEFAULT 60,
    status           varchar(20)  NOT NULL DEFAULT 'SCHEDULED',
    focus_skills     varchar(500) NOT NULL,
    created_by       uuid,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_interviews PRIMARY KEY (id),
    CONSTRAINT fk_interviews_candidate FOREIGN KEY (candidate_id)
        REFERENCES candidates (id) ON DELETE CASCADE,
    CONSTRAINT fk_interviews_interviewer FOREIGN KEY (interviewer_id)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_interviews_created_by FOREIGN KEY (created_by)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_interviews_status CHECK (status IN
        ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_interviews_level CHECK (experience_level IN
        ('JUNIOR', 'MID', 'SENIOR', 'LEAD')),
    CONSTRAINT ck_interviews_round CHECK (round_number BETWEEN 1 AND 10),
    CONSTRAINT ck_interviews_duration CHECK (duration_minutes BETWEEN 15 AND 480)
);

CREATE INDEX idx_interviews_candidate_id ON interviews (candidate_id);
CREATE INDEX idx_interviews_interviewer_id ON interviews (interviewer_id);
CREATE INDEX idx_interviews_status ON interviews (status);
CREATE INDEX idx_interviews_scheduled_at ON interviews (scheduled_at DESC);
-- Covers the dashboard "pending interviews by interviewer" query.
CREATE INDEX idx_interviews_status_scheduled_at ON interviews (status, scheduled_at DESC);

-- -----------------------------------------------------------------------------
-- interview_questions: the question set attached to an interview, whether
-- AI-generated or added manually by an interviewer.
-- -----------------------------------------------------------------------------
CREATE TABLE interview_questions (
    id              uuid        NOT NULL,
    interview_id    uuid        NOT NULL,
    sequence_no     integer     NOT NULL,
    question_text   text        NOT NULL,
    category        varchar(60) NOT NULL,
    difficulty      varchar(20) NOT NULL,
    expected_answer text,
    source          varchar(20) NOT NULL DEFAULT 'AI',
    external_set_id uuid,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_interview_questions PRIMARY KEY (id),
    CONSTRAINT fk_interview_questions_interview FOREIGN KEY (interview_id)
        REFERENCES interviews (id) ON DELETE CASCADE,
    CONSTRAINT uq_interview_questions_sequence UNIQUE (interview_id, sequence_no),
    CONSTRAINT ck_interview_questions_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT ck_interview_questions_source CHECK (source IN ('AI', 'MANUAL')),
    CONSTRAINT ck_interview_questions_sequence CHECK (sequence_no > 0)
);

CREATE INDEX idx_interview_questions_interview_id ON interview_questions (interview_id);

-- -----------------------------------------------------------------------------
-- interview_results: exactly one scorecard per interview.
-- -----------------------------------------------------------------------------
CREATE TABLE interview_results (
    id                    uuid          NOT NULL,
    interview_id          uuid          NOT NULL,
    technical_score       numeric(4, 1) NOT NULL,
    communication_score   numeric(4, 1) NOT NULL,
    problem_solving_score numeric(4, 1) NOT NULL,
    overall_score         numeric(4, 1) NOT NULL,
    recommendation        varchar(20)   NOT NULL,
    strengths             text,
    improvements          text,
    feedback              text          NOT NULL,
    submitted_by          uuid,
    submitted_at          timestamptz   NOT NULL DEFAULT now(),
    created_at            timestamptz   NOT NULL DEFAULT now(),
    updated_at            timestamptz   NOT NULL DEFAULT now(),
    version               bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_interview_results PRIMARY KEY (id),
    CONSTRAINT uq_interview_results_interview UNIQUE (interview_id),
    CONSTRAINT fk_interview_results_interview FOREIGN KEY (interview_id)
        REFERENCES interviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_interview_results_submitted_by FOREIGN KEY (submitted_by)
        REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT ck_interview_results_recommendation CHECK (recommendation IN
        ('STRONG_HIRE', 'HIRE', 'HOLD', 'NO_HIRE')),
    CONSTRAINT ck_interview_results_technical CHECK (technical_score BETWEEN 0 AND 10),
    CONSTRAINT ck_interview_results_communication CHECK (communication_score BETWEEN 0 AND 10),
    CONSTRAINT ck_interview_results_problem_solving CHECK (problem_solving_score BETWEEN 0 AND 10),
    CONSTRAINT ck_interview_results_overall CHECK (overall_score BETWEEN 0 AND 10)
);

CREATE INDEX idx_interview_results_recommendation ON interview_results (recommendation);
CREATE INDEX idx_interview_results_submitted_at ON interview_results (submitted_at DESC);

-- -----------------------------------------------------------------------------
-- revoked_tokens: server-side JWT invalidation for logout. Persisted rather
-- than in-memory so it works across replicas and pod restarts.
-- Rows are pruned by a scheduled task once `expires_at` has passed.
-- -----------------------------------------------------------------------------
CREATE TABLE revoked_tokens (
    jti        varchar(64) NOT NULL,
    user_id    uuid,
    token_type varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_revoked_tokens PRIMARY KEY (jti),
    CONSTRAINT fk_revoked_tokens_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_revoked_tokens_type CHECK (token_type IN ('ACCESS', 'REFRESH'))
);

CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens (expires_at);
CREATE INDEX idx_revoked_tokens_user_id ON revoked_tokens (user_id);
