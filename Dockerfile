# =============================================================================
# Middleware (Spring Boot 3 / Java 21) - multi-stage production image
#
# Stage 1 resolves dependencies in a cacheable layer, then builds the jar.
# Stage 2 extracts Spring Boot's layered jar so dependency layers are shared
# between builds and only the application layer changes on a code-only commit.
# Stage 3 is a slim JRE running as an unprivileged user.
# =============================================================================

# ---------- Stage 1: build ----------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependency resolution is cached independently of the source tree.
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline

COPY src ./src
RUN mvn -B -ntp clean package -DskipTests \
    && java -Djarmode=layertools -jar target/middleware.jar extract --destination /build/layers

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21.0.11_10-jre-alpine-3.23 AS runtime

# curl is required by the container/Kubernetes health probes.
RUN apk add --no-cache curl tzdata \
    && addgroup -g 10001 -S appgroup \
    && adduser -u 10001 -S appuser -G appgroup \
    && mkdir -p /var/lib/ai-interview/resumes \
    && chown -R appuser:appgroup /var/lib/ai-interview

WORKDIR /app

# Ordered least- to most-frequently-changed for optimal layer reuse.
COPY --from=build --chown=appuser:appgroup /build/layers/dependencies/ ./
COPY --from=build --chown=appuser:appgroup /build/layers/spring-boot-loader/ ./
COPY --from=build --chown=appuser:appgroup /build/layers/snapshot-dependencies/ ./
COPY --from=build --chown=appuser:appgroup /build/layers/application/ ./

USER 10001:10001

ENV SERVER_PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health/readiness" || exit 1

# Exec form: the JVM is PID 1 and receives SIGTERM directly, which triggers
# Spring Boot's graceful shutdown.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
