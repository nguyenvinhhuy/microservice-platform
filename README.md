# Microservice Platform - Complete Stack

🚀 Enterprise-grade microservice platform với **8 Spring Boot services** + **Angular 21 Frontend** + **Keycloak Identity** + **PostgreSQL (per-service)** + **Kafka** + **Redis Cache** + **MinIO Storage** + **Jenkins CI/CD** + **Prometheus & Grafana Monitoring**

## 📊 Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    📱 FRONTEND LAYER                            │
│  Angular 21 (Tailwind CSS) + Nginx                 [Port 8080] │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                  🔐 SECURITY LAYER (Keycloak)                   │
│  OAuth 2.0 / OpenID Connect                        [Port 8180] │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                    🏭 MICROSERVICES                             │
│  ├─ Gateway Service       (8000)  ✓ Route & Auth              │
│  ├─ User Service          (8001)  ✓ User Management            │
│  ├─ Order Service         (8002)  ✓ Order Processing + gRPC   │
│  ├─ File Service          (8003)  ✓ File Management + MinIO    │
│  ├─ Notification Service  (8004)  ✓ Email & Messaging         │
│  ├─ Audit Log Service     (8005)  ✓ Event Logging             │
│  └─ (Extensible)          (800x)  ✓ Add more services...      │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌─────────┬──────────────┼──────────────┬──────────────┐
│         │              │              │              │
▼         ▼              ▼              ▼              ▼
📦 Cache  📨 Messaging  💾 Database    📁 Storage    🔍 Search
Redis    Kafka         PostgreSQL    MinIO         (Optional)
         Zookeeper     (per-service)
```

## 🎯 Services Breakdown

### 1️⃣ Frontend Services
| Service | Port | Tech | Purpose |
|---------|------|------|---------|
| **Angular FE** | 8080 | Angular 21 + Tailwind | Web UI |
| **Nginx** | - | Nginx | Reverse Proxy & Static Files |

### 2️⃣ Security & Identity
| Service | Port | Tech | Purpose |
|---------|------|------|---------|
| **Keycloak** | 8180 | OAuth 2.0 | IAM & SSO |
| **Keycloak DB** | 5433 | PostgreSQL 16 | Keycloak Database |

### 3️⃣ Spring Boot Microservices (7 Services)
| Service | Port | Database | Purpose |
|---------|------|----------|---------|
| **Gateway Service** | 8000 | gateway-db | API Gateway & Routing |
| **User Service** | 8001 | user-db | User Management |
| **Order Service** | 8002 | order-db | Order Processing (gRPC enabled) |
| **File Service** | 8003 | file-db | File Management + MinIO |
| **Notification Service** | 8004 | notification-db | Email & In-app Notifications |
| **Audit Log Service** | 8005 | audit-db | Event Logging & Auditing |
| **Custom Service** | 800x | custom-db | Your custom service |

### 4️⃣ Data Layer (Per-Service Databases)
- **gateway-db** (5434) - Gateway routing data
- **user-db** (5435) - User accounts & profiles
- **order-db** (5436) - Order transactions
- **file-db** (5437) - File metadata
- **notification-db** (5438) - Notification history
- **audit-db** (5439) - Audit logs

### 5️⃣ Messaging & Streaming
| Service | Port | Purpose |
|---------|------|---------|
| **Kafka** | 9092 | Event Streaming & Pub/Sub |
| **Zookeeper** | 2181 | Kafka Coordination |

### 6️⃣ Cache & Storage
| Service | Port | Purpose |
|---------|------|---------|
| **Redis** | 6379 | In-Memory Cache (password protected) |
| **MinIO** | 9000 | S3-Compatible Object Storage |
| **MinIO Console** | 9001 | MinIO Management UI |

### 7️⃣ DevOps & CI/CD
| Service | Port | Purpose |
|---------|------|---------|
| **Docker Registry** | 5000 | Private Docker Registry |
| **Jenkins** | 8088 | CI/CD Pipeline |
| **Jenkins Slave** | 50000 | Jenkins Agent |

### 8️⃣ Observability & Monitoring
| Service | Port | Purpose |
|---------|------|---------|
| **Prometheus** | 9090 | Metrics Collection |
| **Grafana** | 3000 | Visualization & Dashboards |

## 📋 Stack Technology

**Frontend:**
- Angular 21 (Latest)
- Tailwind CSS
- RxJS
- TypeScript 5.9+

**Backend:**
- Spring Boot 3.x
- Spring Cloud (Gateway, Feign, etc.)
- Spring Data JPA
- Spring Kafka
- gRPC (for Order Service)

**Infrastructure:**
- Docker & Docker Compose
- Kubernetes (production)
- Keycloak (Identity)
- PostgreSQL 16 (databases)
- Redis 7 (cache)
- Kafka 7.5 (streaming)
- MinIO (storage)

**DevOps:**
- Jenkins (CI/CD)
- Docker Registry
- Prometheus (monitoring)
- Grafana (visualization)

## 🚀 Quick Start

### Prerequisites
```bash
✓ Docker & Docker Compose 2.0+
✓ Node.js 22+ (for Angular development)
✓ Java 17+ (for Spring Boot development)
✓ Git
```

### 1. Initialize Environment
```powershell
# Windows PowerShell
Copy-Item ".env.example" ".env"
# Edit .env with your credentials
```

```bash
# Linux/macOS
cp .env.example .env
# Edit .env with your credentials
```

### 2. Start All Services
```bash
docker-compose up -d
```

### 3. Wait for Services to Initialize
```bash
# Check status
docker-compose ps

# View logs
docker-compose logs -f
```

### 4. Access Services

| Service | URL | Credentials |
|---------|-----|-------------|
| **Angular Frontend** | http://localhost:8080 | - |
| **Gateway API** | http://localhost:8000/swagger-ui | - |
| **Keycloak** | http://localhost:8180/admin | admin/admin123 |
| **MinIO Console** | http://localhost:9001 | minioadmin/minioadmin |
| **Jenkins** | http://localhost:8088 | admin/admin123 |
| **Prometheus** | http://localhost:9090 | - |
| **Grafana** | http://localhost:3000 | admin/admin123 |

## 📁 Project Structure

```
microservice-platform/
├── angular-fe/                          # Angular 21 Frontend
│   ├── src/
│   ├── Dockerfile                       # Multi-stage build
│   ├── nginx.conf                       # Nginx config
│   └── package.json
│
├── gateway-service/                     # Spring Boot - API Gateway
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── user-service/                        # Spring Boot - User Management
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── order-service/                       # Spring Boot - Order Processing (gRPC)
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── file-service/                        # Spring Boot - File Management
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── notification-service/                # Spring Boot - Notifications
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── audit-log-service/                   # Spring Boot - Audit Logging
│   ├── src/
│   ├── pom.xml
│   └── Dockerfile
│
├── k8s/                                 # Kubernetes Manifests
│   ├── 00-namespace.yaml
│   ├── 01-configmap-secret.yaml
│   ├── 02-postgres.yaml
│   ├── 03-redis.yaml
│   ├── 05-kafka.yaml
│   └── 06-services.yaml
│
├── docker-compose.yml                   # Local development stack
├── Jenkinsfile                          # CI/CD pipeline
├── prometheus.yml                       # Prometheus config
├── .env.example                         # Environment template
├── README.md                            # This file
└── scripts/
    ├── dev.sh                          # Linux/macOS helper
    └── dev.ps1                         # Windows helper
```

## 🐳 Docker Compose Commands

### Start All Services
```bash
docker-compose up -d
```

### Stop All Services
```bash
docker-compose down
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f gateway-service
docker-compose logs -f user-service
docker-compose logs -f kafka
```

### Remove Volumes (Reset Data)
```bash
docker-compose down -v
```

### Rebuild Services
```bash
docker-compose up -d --build
```

## 🔧 Development Workflow

### Adding a New Spring Boot Service

1. **Create service directory**
```bash
mkdir my-new-service
cd my-new-service
```

2. **Create Spring Boot project**
```bash
mvn archetype:generate -DgroupId=com.microservice \
  -DartifactId=my-new-service -DarchetypeArtifactId=maven-archetype-quickstart
```

3. **Create Dockerfile**
```dockerfile
FROM maven:3.9-eclipse-temurin-17 as builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM openjdk:17-alpine
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8006
ENTRYPOINT ["java","-jar","/app.jar"]
```

4. **Add to docker-compose.yml**
```yaml
my-new-service:
  container_name: my-new-service
  build:
    context: ./my-new-service
  ports:
    - "8006:8006"
  environment:
    SERVER_PORT: 8006
    SPRING_DATASOURCE_URL: jdbc:postgresql://my-new-db:5432/my_new_db
    # ... other env vars
  depends_on:
    - my-new-db
  networks:
    - app-network
```

5. **Create database**
```yaml
my-new-db:
  container_name: my-new-db
  image: postgres:16-alpine
  environment:
    POSTGRES_DB: my_new_db
    POSTGRES_USER: myuser
    POSTGRES_PASSWORD: mypassword
  ports:
    - "5440:5432"
  volumes:
    - my_new_db_data:/var/lib/postgresql/data
  networks:
    - app-network
```

## 📊 Monitoring & Observability

### Prometheus Metrics
Services expose metrics at `/actuator/prometheus` (Spring Boot Actuator)

```bash
# View metrics for a service
curl http://localhost:8000/actuator/prometheus
```

### Grafana Dashboards
1. Login: http://localhost:3000 (admin/admin123)
2. Add Prometheus data source
3. Import dashboards or create custom ones
4. Monitor:
   - JVM metrics
   - HTTP requests
   - Database connections
   - Cache hit rates
   - Message queue depth

### Prometheus Queries Examples
```promql
# Request rate
rate(http_requests_total[5m])

# Error rate
rate(http_requests_total{status=~"5.."}[5m])

# JVM Memory Usage
jvm_memory_used_bytes / jvm_memory_max_bytes

# Database connections
db_active_connections
```

## 🔐 Security Best Practices

✅ **Implemented:**
- Non-root users in containers
- Health checks for all services
- Resource limits (requests/limits)
- Network isolation (custom bridge)
- Secrets management via .env
- Keycloak OAuth 2.0/OIDC
- HTTPS ready (Ingress config)
- Audit logging

✅ **Recommended for Production:**
- Use Kubernetes Secrets instead of .env
- Enable TLS/SSL for all services
- Use external secret manager (Vault, AWS Secrets)
- Implement rate limiting
- Enable request logging
- Regular security scanning
- Network policies (K8s NetworkPolicy)
- Pod Security Policy

## 🧪 Testing

### Unit Tests
```bash
cd angular-fe
npm test

# Spring Boot
mvn test
```

### Integration Tests
```bash
mvn verify -Pit
```

### Load Testing
```bash
# Using Apache JMeter
jmeter -n -t test-plan.jmx -l results.jtl
```

## ☸️ Kubernetes Deployment

### Prerequisites
```bash
kubectl config current-context
kubectl get nodes
```

### Deploy to K8s
```bash
# Apply configurations
kubectl apply -f k8s/

# Check deployment
kubectl get pods -n microservice-platform
kubectl get svc -n microservice-platform

# View logs
kubectl logs -f deployment/gateway-service -n microservice-platform
```

### Scaling
```bash
# Manual scale
kubectl scale deployment gateway-service --replicas=5 -n microservice-platform

# Check HPA
kubectl get hpa -n microservice-platform
```

## 📝 Environment Variables

All services use environment variables from `.env` file. Copy `.env.example` and customize:

```bash
cp .env.example .env
```

**Key variables:**
- Database credentials (per-service)
- Redis password
- Keycloak admin credentials
- Kafka bootstrap servers
- MinIO credentials
- JWT secret
- Logging level

## 🐛 Troubleshooting

### Service won't start
```bash
# Check logs
docker-compose logs [service-name]

# Check if port is in use
netstat -an | grep 8000

# Rebuild
docker-compose up -d --build [service-name]
```

### Database connection error
```bash
# Test database
docker-compose exec [db-name] psql -U [user] -d [database]

# Check network
docker network ls
docker network inspect app-network
```

### Kafka message lag
```bash
# Check Kafka topics
docker-compose exec kafka kafka-topics.sh --list --bootstrap-server kafka:29092

# Check consumer groups
docker-compose exec kafka kafka-consumer-groups.sh --list --bootstrap-server kafka:29092
```

### Redis connection issues
```bash
# Test Redis
docker-compose exec redis redis-cli -a redis123 ping

# Check memory
docker-compose exec redis redis-cli -a redis123 info memory
```

## 📚 Additional Resources

- [Angular Documentation](https://angular.io)
- [Spring Boot Guide](https://spring.io/guides/gs/spring-boot/)
- [Keycloak Admin Guide](https://www.keycloak.org/docs/latest/)
- [Kafka Documentation](https://kafka.apache.org/documentation/)
- [Kubernetes Docs](https://kubernetes.io/docs/)
- [Prometheus Queries](https://prometheus.io/docs/prometheus/latest/querying/basics/)

## 🤝 Contributing

1. Create feature branch: `git checkout -b feature/amazing-feature`
2. Commit changes: `git commit -m 'Add amazing feature'`
3. Push to branch: `git push origin feature/amazing-feature`
4. Open Pull Request

## 📄 License

MIT License - See LICENSE file for details

## 👨‍💻 Team

Your Team Name / Organization

---

**Last Updated:** 2026-02-11  
**Status:** Production Ready ✅

