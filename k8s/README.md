# Kubernetes Deployment Guide

Tài liệu này là **playbook triển khai local trên `kind`** cho bộ manifest hiện tại trong thư mục `k8s/`. Mục tiêu của file này là giúp bạn biết **chạy gì trước, chạy gì sau, service nào đang dùng được, và service nào chưa nằm trong flow mặc định**.

## 1. Phạm vi áp dụng

- Áp dụng cho **local-kind flow** trong repo này.
- `k8s/00-05` là lớp hạ tầng nền.
- `k8s/logging/` là stack riêng trong namespace `logging`.
- Flow mặc định trong `k8s/` ưu tiên chuỗi backend chính trước, rồi mới tới read-model, utility, gateway, và frontend.
- `07-user-service.yaml` và `11-file-service.yaml` được giữ lại để traceability, nhưng **không nằm trong flow local-kind mặc định**.

## 2. Workload nào đang nằm trong flow mặc định

### Đã đưa vào flow local-kind

- `product-service`
- `inventory-service`
- `payment-service`
- `order-service`
- `notification-service`
- `audit-log-service`
- `product-view-service`
- `order-view-service`
- `dlq-replayer-service`
- `gateway-service`
- `angular-fe`

### Không nằm trong flow mặc định

- `user-service`: module legacy, chưa coi là path local-kind đã verify end-to-end.
- `file-service`: module legacy, còn phụ thuộc `MinIO`, chưa coi là path local-kind đã verify end-to-end.

## 3. Thứ tự file trong `k8s/`

### Infrastructure

```text
00-namespace.yaml
01-configmap-secret.yaml
02-postgres.yaml
03-redis.yaml
05-kafka.yaml
```

### Default local-kind flow

```text
06-product-service.yaml
08-inventory-service.yaml
09-payment-service.yaml
10-order-service.yaml
12-notification-service.yaml
13-audit-log-service.yaml
14-product-view-service.yaml
15-order-view-service.yaml
16-dlq-replayer-service.yaml
17-gateway-service.yaml
18-angular-fe.yaml
```

### Legacy / optional manifests

```text
07-user-service.yaml
11-file-service.yaml
```

## 4. Dependency ngoài cụm cần biết

| Thành phần | Service liên quan | Ghi chú |
| --- | --- | --- |
| Keycloak / JWT issuer | `gateway-service`, `notification-service` | Cần issuer phù hợp nếu muốn chạy ổn định đầy đủ. |
| MinIO | `file-service` | Chưa nằm trong flow mặc định. |
| Logging stack | Toàn nền tảng | Nằm riêng trong `k8s/logging/`. |

## 5. Trình tự triển khai local-kind

### Bước 0 - Chuẩn bị cluster

```powershell
docker start micro-cluster-control-plane micro-cluster-worker micro-cluster-worker2
kubectl get nodes
kubectl config set-context --current --namespace=microservice-platform
```

Nếu Elasticsearch trong logging báo lỗi `vm.max_map_count`:

```powershell
wsl -d docker-desktop -u root sysctl -w vm.max_map_count=262144
```

### Bước 1 - Apply hạ tầng nền

```powershell
kubectl apply -f .\k8s\00-namespace.yaml
kubectl apply -f .\k8s\01-configmap-secret.yaml
kubectl apply -f .\k8s\02-postgres.yaml
kubectl apply -f .\k8s\03-redis.yaml
kubectl apply -f .\k8s\05-kafka.yaml
```

Chỉ rollout service phụ thuộc event sau khi Kafka/Zookeeper đã ổn định.

### Bước 2 - Logging stack (tùy chọn)

```powershell
kubectl apply -f .\k8s\logging\00-logging-namespace.yaml
kubectl apply -f .\k8s\logging\01-elasticsearch-config.yaml
kubectl apply -f .\k8s\logging\02-elasticsearch.yaml
kubectl apply -f .\k8s\logging\03-kibana.yaml
kubectl apply -f .\k8s\logging\04-fluentd-rbac.yaml
kubectl apply -f .\k8s\logging\05-fluentd-config.yaml
kubectl apply -f .\k8s\logging\06-fluentd-daemonset.yaml
```

### Bước 3 - Build và nạp local images

#### Nhóm build từ root context

```powershell
$rootContextServices = @(
  'product-service',
  'inventory-service',
  'payment-service',
  'order-service',
  'product-view-service',
  'order-view-service',
  'dlq-replayer-service'
)

foreach ($service in $rootContextServices) {
  docker build --no-cache -t "${service}:latest" -f "$service/Dockerfile" .
  kind load docker-image "${service}:latest" --name micro-cluster
}
```

#### Nhóm build theo module context

```powershell
docker build --no-cache -t audit-log-service:latest -f audit-log-service/Dockerfile audit-log-service
kind load docker-image audit-log-service:latest --name micro-cluster

docker build --no-cache -t notification-service:latest -f notification-service/Dockerfile notification-service
kind load docker-image notification-service:latest --name micro-cluster

docker build --no-cache -t gateway-service:latest -f gateway-service/Dockerfile gateway-service
kind load docker-image gateway-service:latest --name micro-cluster

docker build --no-cache -t angular-fe:latest -f angular-fe/Dockerfile angular-fe
kind load docker-image angular-fe:latest --name micro-cluster
```

> `user-service` và `file-service` không nằm trong nhóm build mặc định của local-kind flow hiện tại.

### Bước 4 - Apply manifests theo thứ tự

```powershell
kubectl apply -f .\k8s\06-product-service.yaml
kubectl rollout status deployment product-service -n microservice-platform

kubectl apply -f .\k8s\08-inventory-service.yaml
kubectl rollout status deployment inventory-service -n microservice-platform

kubectl apply -f .\k8s\09-payment-service.yaml
kubectl rollout status deployment payment-service -n microservice-platform

kubectl apply -f .\k8s\10-order-service.yaml
kubectl rollout status deployment order-service -n microservice-platform

kubectl apply -f .\k8s\12-notification-service.yaml
kubectl rollout status deployment notification-service -n microservice-platform

kubectl apply -f .\k8s\13-audit-log-service.yaml
kubectl rollout status deployment audit-log-service -n microservice-platform

kubectl apply -f .\k8s\14-product-view-service.yaml
kubectl rollout status deployment product-view-service -n microservice-platform

kubectl apply -f .\k8s\15-order-view-service.yaml
kubectl rollout status deployment order-view-service -n microservice-platform

kubectl apply -f .\k8s\16-dlq-replayer-service.yaml
kubectl rollout status deployment dlq-replayer-service -n microservice-platform

kubectl apply -f .\k8s\17-gateway-service.yaml
kubectl rollout status deployment gateway-service -n microservice-platform

kubectl apply -f .\k8s\18-angular-fe.yaml
kubectl rollout status deployment angular-fe -n microservice-platform
```

### Bước 5 - Legacy manifests nếu thực sự cần

```powershell
kubectl apply -f .\k8s\07-user-service.yaml
kubectl apply -f .\k8s\11-file-service.yaml
```

Chỉ dùng bước này nếu bạn đang tự hoàn thiện runtime của `user-service` hoặc `file-service`.

## 6. Verify nhanh

```powershell
kubectl get namespace
kubectl get pods -n microservice-platform
kubectl get svc -n microservice-platform
kubectl get deployments -n microservice-platform
kubectl get pods -n logging
```

```powershell
kubectl rollout status deployment product-service -n microservice-platform
kubectl rollout status deployment inventory-service -n microservice-platform
kubectl rollout status deployment payment-service -n microservice-platform
kubectl rollout status deployment order-service -n microservice-platform
kubectl rollout status deployment gateway-service -n microservice-platform
kubectl rollout status deployment angular-fe -n microservice-platform
```

```powershell
kubectl logs -n microservice-platform deployment/product-service --tail=100
kubectl logs -n microservice-platform deployment/inventory-service --tail=100
kubectl logs -n microservice-platform deployment/payment-service --tail=100
kubectl logs -n microservice-platform deployment/order-service --tail=100
kubectl get events -n microservice-platform --sort-by='.lastTimestamp'
```

## 7. Ghi chú vận hành

- `kubectl apply -f .` không phải flow triển khai chính của repo này.
- Với service build local, luôn làm đủ chuỗi: **build -> kind load -> apply -> rollout status**.
- Nếu vừa sửa entity, schema, hoặc dependency boot quan trọng, ưu tiên `--no-cache` để tránh pod chạy lại image cũ trong node cache của kind.

## 8. Cleanup

```powershell
kubectl delete namespace microservice-platform
```

Nếu muốn xóa luôn cụm `kind`:

```powershell
kind delete cluster --name micro-cluster
```

## 9. File liên quan

- `k8s/logging/README.md`: hướng dẫn riêng cho logging stack.
- `k8s/06-product-service.yaml`: backend rollout đầu tiên trong flow mặc định.
- `k8s/17-gateway-service.yaml`: gateway deploy sau khi backend core đã sẵn sàng.
- `k8s/18-angular-fe.yaml`: frontend deploy cuối cùng trong flow local-kind.
