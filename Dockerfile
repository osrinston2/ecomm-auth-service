# Multi-stage Dockerfile for ecomm-auth-service (Java 18)
# Stage 1: Build
FROM docker.io/library/eclipse-temurin:18-jdk-alpine AS builder
WORKDIR /workspace

# Copy maven wrapper & pom
COPY pom.xml .
COPY src ./src

# Build release jar skipping tests (tests run in CI pipeline)
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests

# Stage 2: Runtime
FROM docker.io/library/eclipse-temurin:18-jre-alpine
WORKDIR /app

# Add non-root system user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

COPY --from=builder /workspace/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8081

ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

