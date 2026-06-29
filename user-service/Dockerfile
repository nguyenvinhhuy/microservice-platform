FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /workspace

COPY . .
RUN mvn -q -f event-contract/pom.xml dependency:go-offline -B
RUN mvn -q -f event-infra/pom.xml dependency:go-offline -B
RUN mvn -q -f user-service/pom.xml dependency:go-offline -B
RUN mvn -q -f event-contract/pom.xml clean install -DskipTests
RUN mvn -q -f event-infra/pom.xml clean install -DskipTests
RUN mvn -q -f user-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl
RUN addgroup -g 1000 appgroup && adduser -D -u 1000 -G appgroup appuser

WORKDIR /app
COPY --from=builder /workspace/user-service/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

ENV SERVER_PORT=8001
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25 -XX:+UseZGC -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:${SERVER_PORT}/actuator/health/liveness || exit 1

USER appuser
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]

