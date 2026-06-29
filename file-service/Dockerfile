FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /workspace

COPY . .
RUN mvn -q -f event-contract/pom.xml install -DskipTests
RUN mvn -q -f event-infra/pom.xml install -DskipTests
RUN mvn -q -f file-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -g 10001 appgroup \
    && adduser -D -u 10001 -G appgroup appuser

WORKDIR /app
COPY --from=builder /workspace/file-service/target/*.jar /app/app.jar
RUN chown -R appuser:appgroup /app

ENV SERVER_PORT=8003
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

USER appuser
EXPOSE 8003

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:${SERVER_PORT}/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

