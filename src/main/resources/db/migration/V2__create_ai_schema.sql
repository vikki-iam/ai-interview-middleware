-- =============================================================================
-- V2 - Tables owned by the Python AI service (SQLAlchemy models in
-- backend/app/domain/models.py map to these).
--
-- Flyway owns DDL for the entire database so there is exactly one schema
-- authority and one migration history, even though two services read/write it.
-- The AI service never runs create_all() outside its own test suite.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- ai_question_sets: one row per generation request, including failures, so the
-- AI spend and error rate are auditable from SQL.
-- -----------------------------------------------------------------------------
CREATE TABLE ai_question_sets (
    id                uuid         NOT NULL,
    interview_id      uuid,
    request_id        varchar(64)  NOT NULL,
    role_title        varchar(150) NOT NULL,
    experience_level  varchar(20)  NOT NULL,
    skills            varchar(500) NOT NULL,
    question_count    integer      NOT NULL,
    provider          varchar(30)  NOT NULL,
    model             varchar(80),
    status            varchar(20)  NOT NULL,
    prompt_tokens     integer,
    completion_tokens integer,
    latency_ms        integer,
    error_message     text,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_ai_question_sets PRIMARY KEY (id),
    CONSTRAINT fk_ai_question_sets_interview FOREIGN KEY (interview_id)
        REFERENCES interviews (id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_question_sets_status CHECK (status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_ai_question_sets_provider CHECK (provider IN ('mock', 'openai')),
    CONSTRAINT ck_ai_question_sets_count CHECK (question_count >= 0 AND question_count <= 50),
    CONSTRAINT ck_ai_question_sets_level CHECK (experience_level IN ('JUNIOR', 'MID', 'SENIOR', 'LEAD'))
);

CREATE INDEX idx_ai_question_sets_interview_id ON ai_question_sets (interview_id);
CREATE INDEX idx_ai_question_sets_created_at ON ai_question_sets (created_at DESC);
CREATE INDEX idx_ai_question_sets_status ON ai_question_sets (status);
-- Deliberately NOT unique. request_id is the inbound X-Request-Id correlation value, and the
-- middleware retries a failed generation under the same correlation id, so one id legitimately
-- covers a FAILED attempt followed by a SUCCEEDED one. The index exists to make "show me every
-- attempt for this request" fast during an incident.
CREATE INDEX idx_ai_question_sets_request_id ON ai_question_sets (request_id);

-- -----------------------------------------------------------------------------
-- ai_generated_questions: the questions belonging to a set.
-- -----------------------------------------------------------------------------
CREATE TABLE ai_generated_questions (
    id               uuid        NOT NULL,
    question_set_id  uuid        NOT NULL,
    sequence_no      integer     NOT NULL,
    question_text    text        NOT NULL,
    category         varchar(60) NOT NULL,
    difficulty       varchar(20) NOT NULL,
    expected_answer  text,
    evaluation_hint  text,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_ai_generated_questions PRIMARY KEY (id),
    CONSTRAINT fk_ai_generated_questions_set FOREIGN KEY (question_set_id)
        REFERENCES ai_question_sets (id) ON DELETE CASCADE,
    CONSTRAINT uq_ai_generated_questions_sequence UNIQUE (question_set_id, sequence_no),
    CONSTRAINT ck_ai_generated_questions_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT ck_ai_generated_questions_sequence CHECK (sequence_no > 0)
);

CREATE INDEX idx_ai_generated_questions_set_id ON ai_generated_questions (question_set_id);

-- -----------------------------------------------------------------------------
-- ai_evaluations: AI-assisted scoring of a candidate's answers.
-- -----------------------------------------------------------------------------
CREATE TABLE ai_evaluations (
    id              uuid          NOT NULL,
    interview_id    uuid,
    question_set_id uuid,
    request_id      varchar(64)   NOT NULL,
    answers         jsonb         NOT NULL,
    per_question    jsonb         NOT NULL,
    overall_score   numeric(4, 1) NOT NULL,
    recommendation  varchar(20)   NOT NULL,
    summary         text          NOT NULL,
    provider        varchar(30)   NOT NULL,
    model           varchar(80),
    latency_ms      integer,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT pk_ai_evaluations PRIMARY KEY (id),
    CONSTRAINT fk_ai_evaluations_interview FOREIGN KEY (interview_id)
        REFERENCES interviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_evaluations_set FOREIGN KEY (question_set_id)
        REFERENCES ai_question_sets (id) ON DELETE SET NULL,
    CONSTRAINT ck_ai_evaluations_overall CHECK (overall_score BETWEEN 0 AND 10),
    CONSTRAINT ck_ai_evaluations_recommendation CHECK (recommendation IN
        ('STRONG_HIRE', 'HIRE', 'HOLD', 'NO_HIRE'))
);

CREATE INDEX idx_ai_evaluations_interview_id ON ai_evaluations (interview_id);
CREATE INDEX idx_ai_evaluations_created_at ON ai_evaluations (created_at DESC);
