# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copy pom first for dependency layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src src
RUN mvn package -DskipTests -B

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime

# Non-root user for security
RUN groupadd --gid 1001 copmap && \
    useradd --uid 1001 --gid copmap --shell /bin/sh copmap

WORKDIR /app

COPY --from=builder /workspace/target/copmap-backend-*.jar app.jar

RUN chown copmap:copmap app.jar
USER copmap

EXPOSE 8080

# JVM tuning for containerized environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
