# ai-interview-middleware

Spring Boot 3 / Java 21 API for the AI Interview Platform. Owns authentication,
candidates, resumes, interviews and the database schema.

Extracted from the `ai-interview-platform` monorepo into its own repository.

## Where it sits

```
Browser -> ALB Ingress -+- /     -> frontend   (react-app-k8s)
                        +- /api  -> middleware (this repo)
                                        |
                                        | X-Internal-Api-Key, ClusterIP :8000
                                        v
                                   ai-service (FastAPI)
                                        |
                                        v
                                   PostgreSQL (RDS)
```

## It owns the schema

Flyway runs on startup: `V1__create_core_schema`, `V2__create_ai_schema`,
`V3__seed_reference_data`. **V2 creates the `ai_*` tables the AI service maps**,
so this service must be deployed before the AI service is useful.

## Configuration

No credential lives in this repo, in the image, or in a Kubernetes Secret. In
the cluster the pod reads two AWS Secrets Manager entries over IRSA:

| Secret | Fields |
|---|---|
| `ai-interview/dev/database` | `host`, `port`, `dbname`, `username`, `password` |
| `ai-interview/dev/application` | `jwtSigningKey` (>=32 bytes), `aiServiceApiKey`, `openaiApiKey` |

`aiServiceApiKey` is one value read by both this service and the AI service -
this service sends it, the AI service checks it.

Locally, `cp .env.example .env` and set `APP_SECRETS_PROVIDER=env`.
**Never commit `.env`.**

## Pipelines

| Workflow | Trigger | Does |
|---|---|---|
| `CI middleware` | push, PR | `mvn verify`, helm lint + kubeconform, GitLeaks |
| `Deploy middleware` | after CI passes on `main` | build, Trivy, push to ECR, `helm upgrade --atomic`, verify by logging in, roll back on failure |

Deploys to cluster `ai-interview-1` (ap-south-1), namespace `default`.

## Local build

```bash
mvn -B -ntp verify
docker build -t middleware .
```
