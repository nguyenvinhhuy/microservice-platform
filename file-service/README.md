# File Service

Production-grade tenant-aware file lifecycle service for direct uploads, pre-signed uploads, downloads, metadata, quota enforcement, and outbox-backed event publishing.

## Key Capabilities
- Direct multipart uploads through `file-service`.
- Pre-signed upload reservation and confirmation flow.
- Native multipart upload initiation, part presigning, completion, abort, and stale-session cleanup.
- Pre-signed download URL generation and streaming downloads.
- Short-lived download ticket issuance and ticket-backed streaming downloads.
- Tenant-aware metadata persistence in PostgreSQL.
- MinIO-backed object storage using an S3-compatible client.
- PostgreSQL-backed REST idempotency for confirm/delete commands.
- Redis-backed caching for metadata, quota lookups, and pre-signed upload state.
- Transactional Kafka outbox integration for file lifecycle events.
- JWT-based tenant isolation and internal-service authorization.
- Actuator liveness/readiness plus custom MinIO health contributor.

## Verification
```bash
mvn -q test
mvn -q verify
```

## Local Run
```bash
mvn spring-boot:run
```

## Docker Build
Build from the repository root so shared modules are available in the Docker context.

```bash
docker build -f file-service/Dockerfile -t file-service:latest .
```

## Main Endpoints
- `POST /files/upload`
- `POST /files/presigned-upload`
- `POST /files/multipart/initiate`
- `POST /files/{fileId}/multipart/parts/{partNumber}/presign`
- `POST /files/{fileId}/confirm`
- `POST /files/{fileId}/multipart/complete`
- `GET /files/{fileId}`
- `GET /files`
- `GET /files/{fileId}/presigned-download`
- `POST /files/{fileId}/download-ticket`
- `GET /files/{fileId}/download`
- `GET /files/download-tickets/{token}/download`
- `DELETE /files/{fileId}/multipart`
- `DELETE /files/{fileId}`
- `GET /internal/files/{fileId}`

## Final Hardening Report
- `D:\IntelliJProjects\microservice-platform\FILE_SERVICE_FINAL_HARDENING_REPORT.md`

## Important Configuration
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `MINIO_URL`
- `MINIO_ACCESS_KEY`
- `MINIO_SECRET_KEY`
- `KAFKA_BOOTSTRAP_SERVERS`
- `KEYCLOAK_ISSUER_URI`

## Health
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`
- `GET /actuator/prometheus`

