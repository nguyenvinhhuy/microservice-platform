# File Service

File Management Service with MinIO Integration

## Features
- File upload and download
- File metadata management
- S3-compatible storage (MinIO)
- Kafka event publishing
- File versioning
- Access control

## Building
```bash
mvn clean package
```

## Running Locally
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8003"
```

## Docker Build
```bash
docker build -t file-service:latest .
```

## Configuration
- `SERVER_PORT`: 8003
- `SPRING_DATASOURCE_URL`: jdbc:postgresql://localhost:5437/file_db
- `MINIO_URL`: http://localhost:9000
- `MINIO_ACCESS_KEY`: minioadmin
- `MINIO_SECRET_KEY`: minioadmin

## API Endpoints
- `POST /api/files/upload` - Upload file
- `GET /api/files/{id}` - Download file
- `GET /api/files` - List files
- `DELETE /api/files/{id}` - Delete file
- `GET /api/files/{id}/metadata` - Get file metadata

## Storage
Uses MinIO S3-compatible storage (http://localhost:9001)

## Health Check
- `http://localhost:8003/actuator/health`

