# syntax=docker/dockerfile:1
# Multi-stage build: Maven builds the jar (tests run in CI, not here), the runtime is a JRE as a non-root user.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package \
 && java -Djarmode=tools -jar target/monthendbuddy-backend-*.jar extract --destination /workspace/extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
WORKDIR /app
# Dependencies change less often than application code, so they get their own layer.
COPY --from=build /workspace/extracted/lib lib
COPY --from=build /workspace/extracted/*.jar app.jar
USER app
# 8081 = API (proxied by nginx), 8082 = actuator (internal only).
EXPOSE 8081 8082
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD curl -fsS http://localhost:8082/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
