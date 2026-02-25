# Stage 1: Build
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build
COPY . .
RUN ./gradlew :api:bootJar --no-daemon -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

# Create non-root user
RUN addgroup -S vatgroup && adduser -S vatuser -G vatgroup

WORKDIR /app
COPY --from=builder /build/api/build/libs/*.jar app.jar

USER vatuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Dspring.threads.virtual.enabled=true", \
  "-jar", "app.jar"]
