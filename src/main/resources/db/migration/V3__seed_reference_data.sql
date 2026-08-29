-- =============================================================================
-- V3 - Seed data.
--
-- Idempotent (ON CONFLICT DO NOTHING) and timestamp-relative so the dashboard
-- always shows a realistic mix of past and upcoming interviews regardless of
-- when the environment is provisioned.
--
-- Passwords are BCrypt hashes of the credentials documented in
-- docs/SETUP.md. Rotate or delete these accounts before any real deployment;
-- the Helm chart exposes `seed.enabled=false` to skip this migration in prod
-- via the `flyway.skipSeed` placeholder documented in DEPLOYMENT.md.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Users
--   admin@aiinterview.local           / Admin@12345        (ADMIN)
--   priya.sharma@aiinterview.local    / Interviewer@12345  (INTERVIEWER)
--   arjun.mehta@aiinterview.local     / Interviewer@12345  (INTERVIEWER)
--   neha.gupta@example.com            / Candidate@12345    (CANDIDATE)
-- -----------------------------------------------------------------------------
INSERT INTO users (id, email, password_hash, full_name, role, enabled, created_at, updated_at)
VALUES
    ('11111111-1111-1111-1111-111111111101',
     'admin@aiinterview.local',
     '$2b$10$xGzaCJbQ8IVfrKY3Kd8FF.vdiL3P8COlLzpDDacCZyQiHzpei9fny',
     'Platform Administrator', 'ADMIN', true, now() - interval '90 days', now() - interval '90 days'),
    ('11111111-1111-1111-1111-111111111102',
     'priya.sharma@aiinterview.local',
     '$2b$10$D1LNHAro1BOQ7DFcQaF95On1tRgr7SCIGG8j5rqSrnLUNSkKcFtzK',
     'Priya Sharma', 'INTERVIEWER', true, now() - interval '80 days', now() - interval '80 days'),
    ('11111111-1111-1111-1111-111111111103',
     'arjun.mehta@aiinterview.local',
     '$2b$10$ivP7jaoYguFY3fxL4wH9eOdkm0EDzMdE6zX3kqEehQe32zGOzQP0m',
     'Arjun Mehta', 'INTERVIEWER', true, now() - interval '75 days', now() - interval '75 days'),
    ('11111111-1111-1111-1111-111111111104',
     'neha.gupta@example.com',
     '$2b$10$NzEJ71ZX6R8txzhKE6S78uOF9WZrrZCvAOyFMjgB2CDJyDzh2GMxy',
     'Neha Gupta', 'CANDIDATE', true, now() - interval '30 days', now() - interval '30 days')
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Candidates
-- -----------------------------------------------------------------------------
INSERT INTO candidates (id, first_name, last_name, email, phone, current_company, current_position,
                        years_of_experience, primary_skill, location, status, notes, created_by,
                        created_at, updated_at)
VALUES
    ('22222222-2222-2222-2222-222222222201', 'Neha', 'Gupta', 'neha.gupta@example.com',
     '+91-98200-11201', 'Infobell Systems', 'Senior DevOps Engineer', 7.5, 'Kubernetes',
     'Bengaluru, IN', 'INTERVIEWING', 'Strong EKS and Helm background. Referred by Priya.',
     '11111111-1111-1111-1111-111111111101', now() - interval '28 days', now() - interval '3 days'),
    ('22222222-2222-2222-2222-222222222202', 'Rahul', 'Verma', 'rahul.verma@example.com',
     '+91-98200-11202', 'Cloudwerx', 'Platform Engineer', 5.0, 'Terraform',
     'Pune, IN', 'INTERVIEWING', 'Deep IaC experience; wants a larger platform team.',
     '11111111-1111-1111-1111-111111111101', now() - interval '24 days', now() - interval '5 days'),
    ('22222222-2222-2222-2222-222222222203', 'Ananya', 'Iyer', 'ananya.iyer@example.com',
     '+91-98200-11203', 'FinEdge', 'Backend Engineer', 3.5, 'Java',
     'Chennai, IN', 'SCREENING', 'Spring Boot and Kafka. Notice period 30 days.',
     '11111111-1111-1111-1111-111111111102', now() - interval '18 days', now() - interval '2 days'),
    ('22222222-2222-2222-2222-222222222204', 'Vikram', 'Singh', 'vikram.singh@example.com',
     '+91-98200-11204', 'Nimbus Retail', 'SRE II', 6.0, 'Observability',
     'Gurugram, IN', 'NEW', 'Prometheus/Grafana/Loki owner at current employer.',
     '11111111-1111-1111-1111-111111111102', now() - interval '9 days', now() - interval '9 days'),
    ('22222222-2222-2222-2222-222222222205', 'Meera', 'Nair', 'meera.nair@example.com',
     '+91-98200-11205', 'DataPeak', 'Python Developer', 4.0, 'Python',
     'Kochi, IN', 'INTERVIEWING', 'FastAPI and async pipelines.',
     '11111111-1111-1111-1111-111111111103', now() - interval '7 days', now() - interval '1 day'),
    ('22222222-2222-2222-2222-222222222206', 'Karthik', 'Reddy', 'karthik.reddy@example.com',
     '+91-98200-11206', 'Orbit Health', 'Frontend Engineer', 2.5, 'React',
     'Hyderabad, IN', 'ON_HOLD', 'Reopen when the web team headcount is approved.',
     '11111111-1111-1111-1111-111111111103', now() - interval '5 days', now() - interval '4 days')
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Interviews: two completed, two scheduled, one in progress, one cancelled.
-- -----------------------------------------------------------------------------
INSERT INTO interviews (id, candidate_id, interviewer_id, title, role_title, experience_level,
                        round_number, scheduled_at, duration_minutes, status, focus_skills,
                        created_by, created_at, updated_at)
VALUES
    ('33333333-3333-3333-3333-333333333301', '22222222-2222-2222-2222-222222222201',
     '11111111-1111-1111-1111-111111111102', 'DevOps Round 1 - Kubernetes Deep Dive',
     'Senior DevOps Engineer', 'SENIOR', 1, now() - interval '10 days', 60, 'COMPLETED',
     'Kubernetes,Helm,EKS,ArgoCD', '11111111-1111-1111-1111-111111111101',
     now() - interval '20 days', now() - interval '10 days'),
    ('33333333-3333-3333-3333-333333333302', '22222222-2222-2222-2222-222222222202',
     '11111111-1111-1111-1111-111111111103', 'Platform Round 1 - Infrastructure as Code',
     'Platform Engineer', 'MID', 1, now() - interval '6 days', 60, 'COMPLETED',
     'Terraform,AWS,CI/CD', '11111111-1111-1111-1111-111111111101',
     now() - interval '15 days', now() - interval '6 days'),
    ('33333333-3333-3333-3333-333333333303', '22222222-2222-2222-2222-222222222201',
     '11111111-1111-1111-1111-111111111103', 'DevOps Round 2 - Production Troubleshooting',
     'Senior DevOps Engineer', 'SENIOR', 2, now() + interval '2 days', 75, 'SCHEDULED',
     'Kubernetes,Prometheus,Incident Response', '11111111-1111-1111-1111-111111111101',
     now() - interval '3 days', now() - interval '3 days'),
    ('33333333-3333-3333-3333-333333333304', '22222222-2222-2222-2222-222222222203',
     '11111111-1111-1111-1111-111111111102', 'Backend Round 1 - Java and Spring Boot',
     'Backend Engineer', 'MID', 1, now() + interval '5 days', 60, 'SCHEDULED',
     'Java,Spring Boot,PostgreSQL', '11111111-1111-1111-1111-111111111102',
     now() - interval '2 days', now() - interval '2 days'),
    ('33333333-3333-3333-3333-333333333305', '22222222-2222-2222-2222-222222222205',
     '11111111-1111-1111-1111-111111111103', 'Python Round 1 - FastAPI and Async',
     'Python Developer', 'MID', 1, now() - interval '30 minutes', 60, 'IN_PROGRESS',
     'Python,FastAPI,SQLAlchemy', '11111111-1111-1111-1111-111111111103',
     now() - interval '4 days', now() - interval '30 minutes'),
    ('33333333-3333-3333-3333-333333333306', '22222222-2222-2222-2222-222222222206',
     '11111111-1111-1111-1111-111111111102', 'Frontend Round 1 - React Fundamentals',
     'Frontend Engineer', 'JUNIOR', 1, now() + interval '9 days', 45, 'CANCELLED',
     'React,JavaScript,CSS', '11111111-1111-1111-1111-111111111103',
     now() - interval '4 days', now() - interval '1 day')
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- AI question sets (owned by the AI service, seeded here for a populated demo).
-- -----------------------------------------------------------------------------
INSERT INTO ai_question_sets (id, interview_id, request_id, role_title, experience_level, skills,
                              question_count, provider, model, status, prompt_tokens,
                              completion_tokens, latency_ms, created_at)
VALUES
    ('44444444-4444-4444-4444-444444444401', '33333333-3333-3333-3333-333333333301',
     'seed-req-0000000000000000000000001', 'Senior DevOps Engineer', 'SENIOR',
     'Kubernetes,Helm,EKS,ArgoCD', 3, 'mock', 'mock-deterministic-v1', 'SUCCEEDED',
     0, 0, 12, now() - interval '20 days'),
    ('44444444-4444-4444-4444-444444444402', '33333333-3333-3333-3333-333333333303',
     'seed-req-0000000000000000000000002', 'Senior DevOps Engineer', 'SENIOR',
     'Kubernetes,Prometheus,Incident Response', 3, 'mock', 'mock-deterministic-v1', 'SUCCEEDED',
     0, 0, 9, now() - interval '3 days')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ai_generated_questions (id, question_set_id, sequence_no, question_text, category,
                                    difficulty, expected_answer, evaluation_hint, created_at)
VALUES
    ('45444444-4444-4444-4444-444444444401', '44444444-4444-4444-4444-444444444401', 1,
     'A Deployment rollout is stuck with pods in CrashLoopBackOff after a Helm upgrade. Walk through your diagnosis.',
     'Kubernetes', 'HARD',
     'Inspect rollout status, describe the pod for events, read previous container logs, compare the rendered manifest against the previous revision, then roll back with helm rollback while investigating.',
     'Look for a systematic loop: observe, isolate, compare to last known good, mitigate, then fix.',
     now() - interval '20 days'),
    ('45444444-4444-4444-4444-444444444402', '44444444-4444-4444-4444-444444444401', 2,
     'How do you give a pod on EKS permission to read an AWS Secrets Manager secret without static credentials?',
     'EKS', 'MEDIUM',
     'IAM Roles for Service Accounts: an OIDC provider on the cluster, an IAM role with a trust policy scoped to the service account subject, the role ARN annotated on the ServiceAccount, and the AWS SDK default credential chain picking up the projected token.',
     'Candidate must mention the OIDC trust relationship and that no access keys are involved.',
     now() - interval '20 days'),
    ('45444444-4444-4444-4444-444444444403', '44444444-4444-4444-4444-444444444401', 3,
     'Explain the difference between a readiness probe and a liveness probe, and the failure mode of getting them backwards.',
     'Kubernetes', 'EASY',
     'Readiness gates traffic; liveness restarts the container. A liveness probe that checks dependencies causes restart storms during a downstream outage, while a readiness probe used for liveness leaves dead pods running.',
     'Watch for the restart-storm insight, not just the textbook definition.',
     now() - interval '20 days'),
    ('45444444-4444-4444-4444-444444444404', '44444444-4444-4444-4444-444444444402', 1,
     'Requests to one service show p99 latency of 8 seconds while p50 stays at 40ms. How do you find the cause?',
     'Observability', 'HARD',
     'Separate the tail from the mean: check per-pod metrics for a single bad replica, GC pauses, connection pool saturation, and downstream dependency histograms; correlate with traces for the slow percentile.',
     'Strong answers isolate whether the tail is one replica or all replicas early.',
     now() - interval '3 days'),
    ('45444444-4444-4444-4444-444444444405', '44444444-4444-4444-4444-444444444402', 2,
     'Which PromQL query would you use to alert on a pod restarting repeatedly, and why that shape?',
     'Prometheus', 'MEDIUM',
     'increase(kube_pod_container_status_restarts_total[15m]) > 3 - a rate/increase over a window rather than the raw counter, because the counter only resets on pod recreation.',
     'Candidate should explain why the raw counter is the wrong signal.',
     now() - interval '3 days'),
    ('45444444-4444-4444-4444-444444444406', '44444444-4444-4444-4444-444444444402', 3,
     'A node reports MemoryPressure and pods are being evicted. What do requests and limits have to do with it?',
     'Kubernetes', 'MEDIUM',
     'Eviction order follows QoS class, which is derived from requests and limits. BestEffort goes first, then Burstable over its requests, and Guaranteed last. Missing requests make scheduling and eviction unpredictable.',
     'Look for the QoS class link rather than a generic "add more memory".',
     now() - interval '3 days')
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Interview questions materialised onto the interviews by the middleware.
-- -----------------------------------------------------------------------------
INSERT INTO interview_questions (id, interview_id, sequence_no, question_text, category, difficulty,
                                 expected_answer, source, external_set_id, created_at, updated_at)
SELECT
    ('46444444-4444-4444-4444-4444444444' || lpad(row_number() OVER (ORDER BY q.question_set_id, q.sequence_no)::text, 2, '0'))::uuid,
    s.interview_id,
    q.sequence_no,
    q.question_text,
    q.category,
    q.difficulty,
    q.expected_answer,
    'AI',
    q.question_set_id,
    s.created_at,
    s.created_at
FROM ai_generated_questions q
         JOIN ai_question_sets s ON s.id = q.question_set_id
WHERE s.interview_id IS NOT NULL
ON CONFLICT (interview_id, sequence_no) DO NOTHING;

-- One manually authored question to exercise the MANUAL source path.
INSERT INTO interview_questions (id, interview_id, sequence_no, question_text, category, difficulty,
                                 expected_answer, source, created_at, updated_at)
VALUES
    ('46444444-4444-4444-4444-444444444499', '33333333-3333-3333-3333-333333333304', 1,
     'Describe how you would keep a Spring Boot service responsive while a downstream dependency is timing out.',
     'Java', 'MEDIUM',
     'Bounded connection and read timeouts, a bulkhead or thread pool boundary, a circuit breaker with a fallback, and a readiness signal that does not flap on downstream failure.',
     'MANUAL', now() - interval '2 days', now() - interval '2 days')
ON CONFLICT (interview_id, sequence_no) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Results for the two completed interviews.
-- -----------------------------------------------------------------------------
INSERT INTO interview_results (id, interview_id, technical_score, communication_score,
                               problem_solving_score, overall_score, recommendation, strengths,
                               improvements, feedback, submitted_by, submitted_at, created_at, updated_at)
VALUES
    ('55555555-5555-5555-5555-555555555501', '33333333-3333-3333-3333-333333333301',
     8.5, 8.0, 9.0, 8.5, 'STRONG_HIRE',
     'Excellent Kubernetes debugging instincts; explained IRSA precisely; strong Helm templating knowledge.',
     'Limited exposure to service mesh traffic policies.',
     'Diagnosed the CrashLoopBackOff scenario methodically and reached for a rollback before a hotfix. Ready for round 2 focused on incident response.',
     '11111111-1111-1111-1111-111111111102', now() - interval '10 days',
     now() - interval '10 days', now() - interval '10 days'),
    ('55555555-5555-5555-5555-555555555502', '33333333-3333-3333-3333-333333333302',
     7.0, 7.5, 6.5, 7.0, 'HIRE',
     'Solid Terraform module design and state management discipline.',
     'Needed prompting on drift detection and on multi-account IAM boundaries.',
     'Good practical IaC engineer. Recommend a follow-up round on AWS security boundaries before an offer.',
     '11111111-1111-1111-1111-111111111103', now() - interval '6 days',
     now() - interval '6 days', now() - interval '6 days')
ON CONFLICT (interview_id) DO NOTHING;
