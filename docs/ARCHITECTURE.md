# Microservice Platform - Service Dependency & Health Check Guide

## 📊 Service Dependencies Map

```
┌─────────────────────────────────────────────────────────────┐
│                  FRONTEND LAYER                             │
│  Angular FE (8080)                                          │
└─────────────────┬───────────────────────────────────────────┘
                  │ depends_on: [gateway-service, keycloak]
                  │
┌─────────────────▼───────────────────────────────────────────┐
│              SECURITY LAYER (Keycloak)                      │
│  Keycloak (8180) ◄─ Keycloak DB (5433)                     │
└─────────────────┬───────────────────────────────────────────┘
                  │ depends_on: [keycloak-db]
                  │
┌─────────────────▼───────────────────────────────────────────┐
│            API GATEWAY LAYER                                │
│  Gateway Service (8000) ◄─ gateway-db, Redis, Kafka        │
└─────────────────┬───────────────────────────────────────────┘
                  │ depends_on: [gateway-db, redis, kafka]
                  │
        ┌─────────┴─────────┬──────────┬──────────┐
        │                   │          │          │
┌───────▼──────┐  ┌──────────▼──┐  ┌──▼────────┐│
│ User Service │  │Order Service│  │File Svc.. ││
│   (8001)     │  │  (8002)     │  │ (8003)    ││
└──────────────┘  └─────────────┘  └───────────┘│
   ◄─ user-db       ◄─ order-db       ◄─ file-db ◄─────┐
   ◄─ Redis         ◄─ Redis          ◄─ MinIO   │     │
   ◄─ Kafka         ◄─ Kafka          ◄─ Kafka   │     │
                                      └──────────┘     │
                                                        │
┌─────────────────────────────────────────────────────▼─┐
│         NOTIFICATION & AUDIT SERVICES                 │
│  ┌──────────────────┐  ┌──────────────────┐         │
│  │ Notification     │  │ Audit Log        │         │
│  │ Service (8004)   │  │ Service (8005)   │         │
│  └──────────────────┘  └──────────────────┘         │
│     ◄─ notification-db   ◄─ audit-db                │
│     ◄─ Kafka            ◄─ Kafka                    │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│         SHARED INFRASTRUCTURE                        │
│  ┌──────────┐  ┌────────┐  ┌─────────┐  ┌─────────┐│
│  │ Redis    │  │ Kafka  │               │ MinIO   ││
│  │ (6379)   │  │ (9092) │               │ (9000)  ││
│  └──────────┘  └────────┘  └─────────┘  └─────────┘│
│                                                      │
│  ┌────────────────────────────────────────┐        │
│  │ Zookeeper (2181) - Kafka coordination  │        │
│  └────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│         DEVOPS & OBSERVABILITY                      │
│  ┌──────────┐  ┌─────────┐  ┌────────────┐        │
│  │ Jenkins  │  │ Registry│  │ Prometheus │        │
│  │ (8088)   │  │ (5000)  │  │ (9090)     │        │
│  └──────────┘  └─────────┘  └────────────┘        │
│                                                     │
│  ┌──────────────┐                                 │
│  │ Grafana      │ ◄─ Prometheus                  │
│  │ (3000)       │                                │
│  └──────────────┘                                │
└─────────────────────────────────────────────────────┘
```

## 🚀 Startup Order (Important!)

Services must start in this order to ensure dependencies are met:

### Phase 1: Foundation (Databases & Infrastructure)
1. **zookeeper** - Kafka requires this
2. **keycloak-db** - PostgreSQL for Keycloak
3. **gateway-db, user-db, order-db, file-db, notification-db, audit-db** - Service databases
4. **redis** - Cache layer
5. **minio** - Object storage

### Phase 2: Message & Identity
6. **kafka** - Event streaming (depends on zookeeper)
7. **keycloak** - Identity provider (depends on keycloak-db)

### Phase 3: Microservices
9. **gateway-service** - API Gateway (depends on gateway-db, redis, kafka, keycloak)
10. **user-service** - User management
11. **order-service** - Order processing
12. **file-service** - File management
13. **notification-service** - Notifications
14. **audit-log-service** - Audit logging

### Phase 4: Frontend
15. **angular-fe** - Web UI (depends on gateway-service, keycloak)

### Phase 5: DevOps & Monitoring
16. **docker-registry** - Docker registry (independent)
17. **jenkins** - CI/CD pipeline (independent)
18. **prometheus** - Metrics collection (independent)
19. **grafana** - Visualization (depends on prometheus)

## ✅ Health Checks Configuration

All services have health checks configured:

### HTTP Health Checks (Spring Boot)
```bash
# Check service health
curl http://localhost:8000/actuator/health
curl http://localhost:8001/actuator/health
curl http://localhost:8002/actuator/health
```

### Database Health Checks
```bash
# PostgreSQL
pg_isready -U user -h localhost

# Redis
redis-cli -a password ping

# Kafka (manual check)
kafka-broker-api-versions.sh --bootstrap-server kafka:29092
```

### Health Check Intervals

| Service | Interval | Timeout | Retries | Start Period |
|---------|----------|---------|---------|--------------|
| gateway-service | 30s | 10s | 3 | 40s |
| user-service | 30s | 10s | 3 | 40s |
| order-service | 30s | 10s | 3 | 40s |
| file-service | 30s | 10s | 3 | 40s |
| notification-service | 30s | 10s | 3 | 40s |
| audit-log-service | 30s | 10s | 3 | 40s |
| keycloak | 30s | 10s | 5 | 60s |
| PostgreSQL (all) | 10s | 5s | 5 | - |
| Redis | 10s | 3s | 5 | - |
| MinIO | 30s | 20s | 3 | - |
| Prometheus | 30s | 10s | 3 | - |
| Grafana | 30s | 10s | 3 | - |

## 🔄 Wait for Dependencies

### Using Docker Compose Depends On

```yaml
services:
  gateway-service:
    depends_on:
      - gateway-db
      - redis
      - kafka
      - keycloak
```

⚠️ **Note:** `depends_on` only waits for container to **start**, not for service to be **ready**.

### Recommended: Wait Script

Create `wait-for-it.sh` in your service:

```bash
#!/bin/bash
# wait-for-it.sh

host=$1
port=$2
shift 2
cmd="$@"

echo "Waiting for $host:$port..."

while ! nc -z "$host" "$port"; do
  sleep 1
done

echo "$host:$port is available"
exec $cmd
```

Use in Dockerfile:

```dockerfile
RUN apt-get install -y netcat-openbsd
COPY wait-for-it.sh /
RUN chmod +x /wait-for-it.sh

ENTRYPOINT ["/wait-for-it.sh", "postgres", "5432", "--"]
CMD ["java", "-jar", "app.jar"]
```

## 📋 Startup Checklist

After running `docker-compose up -d`, verify all services:

```bash
# 1. Check all containers are running
docker-compose ps
# Expected: All containers with "Up" status

# 2. Check healthy status
docker-compose ps | grep healthy
# Expected: All Spring Boot services and key dependencies

# 3. Test database connections
docker-compose exec gateway-db psql -U gateway -d gateway_db -c "\l"
docker-compose exec user-db psql -U user -d user_db -c "\l"

# 4. Test Redis
docker-compose exec redis redis-cli -a redis123 ping
# Expected: PONG

# 5. Test Kafka
docker-compose exec kafka kafka-topics.sh --list --bootstrap-server kafka:29092
# Expected: List of topics (or empty if first run)

# 6. Test Keycloak
curl -s http://localhost:8180/health | jq .
# Expected: status = UP

# 7. Test Gateway Service
curl -s http://localhost:8000/actuator/health | jq .
# Expected: status = UP

# 8. Test Frontend
curl -s http://localhost:8080/ | head -20
# Expected: HTML content

# 9. Test Prometheus
curl -s http://localhost:9090/api/v1/targets | jq '.data.activeTargets | length'
# Expected: Number of targets being scraped
```

## 🆘 Troubleshooting by Service

### Keycloak won't start
```bash
# Check logs
docker-compose logs keycloak

# Verify database is running
docker-compose logs keycloak-db

# Reset (WARNING: deletes data)
docker-compose down keycloak keycloak-db
docker volume rm microservice-platform_keycloak_db_data
docker-compose up -d keycloak-db
sleep 10
docker-compose up -d keycloak
```

### Gateway Service fails
```bash
# Check dependencies
docker-compose logs gateway-service

# Verify database exists
docker-compose exec gateway-db psql -U gateway -l | grep gateway_db

# Test connection string
docker-compose exec gateway-service \
  nc -zv gateway-db 5432
```

### Kafka topics not created
```bash
# Create topic manually
docker-compose exec kafka kafka-topics.sh \
  --create --topic user-events \
  --bootstrap-server kafka:29092 \
  --partitions 3 \
  --replication-factor 1
```

## 📊 Service Startup Time Estimates

| Service | Startup Time | Ready Time |
|---------|--------------|-----------|
| PostgreSQL | 5-10 seconds | 15-20 seconds |
| Redis | 2-3 seconds | 5 seconds |
| Kafka+Zookeeper | 15-20 seconds | 30-40 seconds |
| Keycloak | 30-40 seconds | 60+ seconds |
| Spring Boot | 15-30 seconds | 40-60 seconds |
| Angular Frontend | 5 seconds | 10-15 seconds |

**Total Estimated Startup Time: 2-5 minutes** (including all health checks passing)

## 🔗 Service Ports Reference

```
Frontend:
  Angular        8080  (http://localhost:8080)
  Nginx          80    (internal)

Security:
  Keycloak       8180  (http://localhost:8180)
  Keycloak DB    5433  (internal)

Microservices:
  Gateway        8000  (http://localhost:8000)
  User           8001  (http://localhost:8001)
  Order          8002  (http://localhost:8002)
  File           8003  (http://localhost:8003)
  Notification   8004  (http://localhost:8004)
  Audit Log      8005  (http://localhost:8005)

Databases:
  Gateway DB     5434  (internal)
  User DB        5435  (internal)
  Order DB       5436  (internal)
  File DB        5437  (internal)
  Notification   5438  (internal)
  Audit DB       5439  (internal)

Messaging:
  Kafka          9092  (localhost:9092)
  Zookeeper      2181  (internal)

Cache & Storage:
  Redis          6379  (internal, password protected)
  MinIO          9000  (http://localhost:9000)
  MinIO Console  9001  (http://localhost:9001)

DevOps:
  Docker Registry 5000 (localhost:5000)
  Jenkins        8088  (http://localhost:8088)
  Jenkins Agent  50000 (internal)

Monitoring:
  Prometheus     9090  (http://localhost:9090)
  Grafana        3000  (http://localhost:3000)
```

## 📝 Notes

- All databases use dedicated PostgreSQL instances (Database-Per-Service pattern)
- Each service has its own schema and user
- Kafka handles inter-service events
- Redis is shared for caching and session management
- MinIO provides S3-compatible object storage
- All services communicate through the `app-network` network
- External clients access via specific ports (Frontend, APIs, Management UIs)

