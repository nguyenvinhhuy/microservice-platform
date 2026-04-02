# Gateway Service

API Gateway and Entry Point for the Microservice Platform

## Features
- Spring Cloud Gateway for routing
- Keycloak OAuth 2.0 integration
- Redis caching
- Kafka event publishing
- Request/Response logging
- Circuit breaker patterns

## Building Locally
```bash
mvn clean package
```

## Running Locally
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8000"
```

## Docker Build
```bash
docker build -t gateway-service:latest .
```

## Configuration
See `application.yml` or `.env` for environment variables:
- `SERVER_PORT`: 8000
- `SPRING_DATASOURCE_URL`: PostgreSQL connection
- `SPRING_REDIS_HOST`: Redis host
- `KEYCLOAK_SERVER_URL`: Keycloak server

## API Documentation
- Swagger UI: `http://localhost:8000/swagger-ui.html`
- OpenAPI: `http://localhost:8000/api-docs`

## Health Check
- `http://localhost:8000/actuator/health`

## Metrics
- Prometheus: `http://localhost:8000/actuator/prometheus`

