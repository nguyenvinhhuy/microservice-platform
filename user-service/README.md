# User Service

User Management and Authentication Service

## Features
- User CRUD operations
- Profile management
- Redis caching
- Kafka event publishing
- Keycloak integration

## Building
```bash
mvn clean package
```

## Running Locally
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8001"
```

## Docker Build
```bash
docker build -t user-service:latest .
```

## Configuration
- `SERVER_PORT`: 8001
- `SPRING_DATASOURCE_URL`: jdbc:postgresql://localhost:5435/user_db
- `SPRING_DATASOURCE_USERNAME`: user
- `SPRING_DATASOURCE_PASSWORD`: user123

## API Endpoints
- `GET /api/users` - List all users
- `GET /api/users/{id}` - Get user by ID
- `POST /api/users` - Create new user
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

## Health Check
- `http://localhost:8001/actuator/health`

## Database
User service uses dedicated PostgreSQL instance on port 5435

