# Order Service

Order Processing Service with gRPC Support

## Features
- Order creation and management
- gRPC server for inter-service communication
- Kafka event streaming
- Redis caching
- Order payment processing
- Order tracking

## Building
```bash
mvn clean package
```

## Running Locally
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8002"
```

## Docker Build
```bash
docker build -t order-service:latest .
```

## Configuration
- `SERVER_PORT`: 8002
- `SPRING_DATASOURCE_URL`: jdbc:postgresql://localhost:5436/order_db
- `GRPC_SERVER_PORT`: 50051

## API Endpoints
- `GET /api/orders` - List orders
- `GET /api/orders/{id}` - Get order details
- `POST /api/orders` - Create order
- `PUT /api/orders/{id}/status` - Update order status

## gRPC Service
- Listening on port 50051
- Service definition in `proto/` directory

## Health Check
- `http://localhost:8002/actuator/health`

