FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /workspace

COPY . .
RUN mvn -q -pl product-view-service -am dependency:go-offline -B
RUN mvn -q -pl product-view-service -am clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl
RUN addgroup -g 1000 appgroup && adduser -D -u 1000 -G appgroup appuser

WORKDIR /app
COPY --from=builder /workspace/product-view-service/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:${SERVER_PORT:-8010}/actuator/health || exit 1

USER appuser
EXPOSE 8010
ENV SERVER_PORT=8010
ENTRYPOINT ["java", "-jar", "app.jar"]

