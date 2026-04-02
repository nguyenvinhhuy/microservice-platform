# Notification Service

Event-driven notification service for email, SMS, push notifications, and notification history.

## Features
- Kafka consumers for platform events with manual acknowledgment.
- Consumer idempotency via `processed_events`.
- Exponential backoff retry with DLQ publishing after retries are exhausted.
- Channel strategy abstraction: Email, SMS, Push.
- Thymeleaf template rendering for email templates under `resources/templates/`.
- Notification history persisted to PostgreSQL for auditing and user history APIs.
- Prometheus metrics and JSON structured logging with `traceId`/`correlationId` propagation.

## Building
```bash
mvn clean package
```

## Running Locally
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8004"
```

## Docker Build
```bash
docker build -t notification-service:latest .
```

## Configuration
- `SERVER_PORT`: `8004`
- `DB_URL`: `jdbc:postgresql://localhost:5432/notification_db`
- `KAFKA_BOOTSTRAP_SERVERS`: `localhost:9092`
- `KEYCLOAK_ISSUER_URI`: `http://keycloak:8080/realms/microservice-platform`
- `NOTIFICATION_DLQ_TOPIC`: `notification.events.dlq`
- `NOTIFICATION_RETRY_MAX_ATTEMPTS`: `5`
- `NOTIFICATION_CHANNEL_EMAIL_ENABLED`: `true`
- `NOTIFICATION_EMAIL_SMTP_ENABLED`: `false` (when `false`, email delivery is simulated via logs)

## API Endpoints
- `GET /notifications?limit=50` - List recent notifications for the authenticated tenant+user.

## Health Check
- `http://localhost:8004/actuator/health`

