# Audit Log Service

Event Logging and Auditing Service

## Features
- Event logging and persistence
- Kafka consumer for all events
- Audit trail tracking
- User activity logging
- System action logging
- Search and filtering

## Building
```bash
mvn clean package
```

## Running Locally
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8005"
```

## Docker Build
```bash
docker build -t audit-log-service:latest .
```

## Configuration
- `SERVER_PORT`: 8005
- `SPRING_DATASOURCE_URL`: jdbc:postgresql://localhost:5439/audit_db
- `KAFKA_BOOTSTRAP_SERVERS`: kafka:29092

## API Endpoints
- `GET /api/audit-logs` - List audit logs
- `GET /api/audit-logs/{id}` - Get audit log details
- `GET /api/audit-logs/user/{userId}` - Get user audit logs
- `GET /api/audit-logs/search` - Search audit logs

## Event Consumption
- Listens to all Kafka topics for audit events
- Records user activities
- Tracks system changes

## Health Check
- `http://localhost:8005/actuator/health`

## Database
Dedicated PostgreSQL on port 5439 for audit logs

