# Kubernetes Deployment Guide

## 📋 Deployment Order

Deploy trong thứ tự sau:

### 1. Foundation (Namespace & Config)
```bash
kubectl apply -f 00-namespace.yaml
kubectl apply -f 01-configmap-secret.yaml
```

### 2. Databases
```bash
kubectl apply -f 02-postgres.yaml
kubectl apply -f 03-redis.yaml
```

### 3. Messaging
```bash
kubectl apply -f 05-kafka.yaml
```

### 4. Application Services
```bash
# Core services
kubectl apply -f 07-gateway-service.yaml
kubectl apply -f 08-user-service.yaml
kubectl apply -f 09-order-service.yaml
kubectl apply -f 10-file-service.yaml
kubectl apply -f 11-notification-service.yaml
kubectl apply -f 12-audit-log-service.yaml

# Frontend
kubectl apply -f 06-angular-fe.yaml
```

### Deploy All at Once
```bash
kubectl apply -f .
```

---

## ✅ Verify Deployment

### Check Namespace
```bash
kubectl get namespace
kubectl get namespace microservice-platform
```

### Check Services
```bash
kubectl get services -n microservice-platform
```

### Check Pods
```bash
kubectl get pods -n microservice-platform
kubectl get pods -n microservice-platform -w  # Watch
```

### Check Deployments
```bash
kubectl get deployments -n microservice-platform
```

### Check StatefulSets
```bash
kubectl get statefulsets -n microservice-platform
```

### Check HPA
```bash
kubectl get hpa -n microservice-platform
```

---

## 🔍 Detailed Checks

### Check Pod Status
```bash
kubectl describe pod [pod-name] -n microservice-platform
```

### Check Service
```bash
kubectl describe service gateway-service -n microservice-platform
```

### Check Logs
```bash
# All pods
kubectl logs -f -n microservice-platform --all-containers=true

# Specific pod
kubectl logs -f [pod-name] -n microservice-platform

# Specific container in pod
kubectl logs -f [pod-name] -c [container-name] -n microservice-platform
```

### Logging Docs

- [EFK Logging Design](D:/IntelliJProjects/microservice-platform/docs/efk-logging-design.md:1)
- [Kibana Query Cheatsheet](D:/IntelliJProjects/microservice-platform/docs/kibana-query-cheatsheet.md:1)
- [Logging Runbook](D:/IntelliJProjects/microservice-platform/docs/logging-runbook.md:1)

### Check Events
```bash
kubectl get events -n microservice-platform
kubectl describe events -n microservice-platform
```

---

## 🚀 Port Forwarding (Local Testing)

### Access Services Locally
```bash
# Gateway Service
kubectl port-forward svc/gateway-service 8000:8000 -n microservice-platform

# User Service
kubectl port-forward svc/user-service 8001:8001 -n microservice-platform

# Angular Frontend
kubectl port-forward svc/angular-fe 8080:80 -n microservice-platform

# Redis
kubectl port-forward svc/redis-cache 6379:6379 -n microservice-platform
```

## 🔧 Scaling

### Manual Scale
```bash
# Scale gateway-service to 5 replicas
kubectl scale deployment gateway-service --replicas=5 -n microservice-platform

# Check current scale
kubectl get deployment gateway-service -n microservice-platform
```

### HPA Status
```bash
kubectl get hpa -n microservice-platform
kubectl describe hpa gateway-service-hpa -n microservice-platform
```

---

## 🔐 Secrets & ConfigMap

### View ConfigMap
```bash
kubectl get configmap -n microservice-platform
kubectl describe configmap app-config -n microservice-platform
```

### View Secrets (without values)
```bash
kubectl get secrets -n microservice-platform
```

### Create/Update Secrets
```bash
# Update database password
kubectl create secret generic app-secrets \
  --from-literal=GATEWAY_DB_PASSWORD=newpassword \
  --dry-run=client -o yaml | kubectl apply -f -
```

---

## 📊 Resource Usage

### Check Node Resources
```bash
kubectl top node
```

### Check Pod Resources
```bash
kubectl top pods -n microservice-platform
```

---

## 🔄 Rolling Updates

### Update Service Image
```bash
kubectl set image deployment/gateway-service \
  gateway-service=your-registry/gateway-service:v2.0 \
  -n microservice-platform
```

### Check Rollout Status
```bash
kubectl rollout status deployment/gateway-service -n microservice-platform
```

### Rollback
```bash
kubectl rollout undo deployment/gateway-service -n microservice-platform
```

---

## 🧹 Cleanup

### Delete Service
```bash
kubectl delete deployment gateway-service -n microservice-platform
```

### Delete All in Namespace
```bash
kubectl delete all -n microservice-platform
```

### Delete Namespace (removes all)
```bash
kubectl delete namespace microservice-platform
```

---

## 🐛 Troubleshooting

### Pod Not Running
```bash
# Check pod status
kubectl describe pod [pod-name] -n microservice-platform

# Check events
kubectl get events -n microservice-platform --sort-by='.lastTimestamp'

# Check logs
kubectl logs [pod-name] -n microservice-platform
```

### Service Not Accessible
```bash
# Check service
kubectl get service gateway-service -n microservice-platform

# Check endpoints
kubectl get endpoints gateway-service -n microservice-platform

# Test connectivity
kubectl run -it --rm debug --image=busybox --restart=Never -- \
  wget -q -O- http://gateway-service:8000/actuator/health -n microservice-platform
```

### Database Connection Issues
```bash
# Check postgres pod
kubectl get pods -l app=postgres-db -n microservice-platform

# Check logs
kubectl logs postgres-db-0 -n microservice-platform

# Connect to pod
kubectl exec -it postgres-db-0 -n microservice-platform -- psql -U gateway -d gateway_db
```

---

## 📋 Pre-Deployment Checklist

Before deploying to production:

- [ ] Docker images built and pushed to registry
- [ ] Update image URLs in manifests (your-registry)
- [ ] Configure proper resource requests/limits
- [ ] Setup persistent volumes for databases
- [ ] Configure ingress for external access
- [ ] Setup TLS certificates
- [ ] Configure secrets properly
- [ ] Test HPA thresholds
- [ ] Setup monitoring/logging
- [ ] Configure backup strategy

---

## 🚀 Full Deployment Script

```bash
#!/bin/bash

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${YELLOW}Deploying Microservice Platform to Kubernetes${NC}"

# Step 1: Create namespace
echo -e "${YELLOW}Step 1: Creating namespace...${NC}"
kubectl apply -f 00-namespace.yaml
sleep 5

# Step 2: Create config & secrets
echo -e "${YELLOW}Step 2: Creating ConfigMap and Secrets...${NC}"
kubectl apply -f 01-configmap-secret.yaml
sleep 5

# Step 3: Deploy databases
echo -e "${YELLOW}Step 3: Deploying databases...${NC}"
kubectl apply -f 02-postgres.yaml
kubectl apply -f 03-redis.yaml
sleep 10

# Step 4: Deploy messaging
echo -e "${YELLOW}Step 4: Deploying messaging (Kafka)...${NC}"
kubectl apply -f 05-kafka.yaml
sleep 15

# Step 5: Deploy services
echo -e "${YELLOW}Step 5: Deploying microservices...${NC}"
kubectl apply -f 07-gateway-service.yaml
kubectl apply -f 08-user-service.yaml
kubectl apply -f 09-order-service.yaml
kubectl apply -f 10-file-service.yaml
kubectl apply -f 11-notification-service.yaml
kubectl apply -f 12-audit-log-service.yaml
sleep 10

# Step 6: Deploy frontend
echo -e "${YELLOW}Step 6: Deploying frontend...${NC}"
kubectl apply -f 06-angular-fe.yaml
sleep 10

# Verify
echo -e "${YELLOW}Verifying deployment...${NC}"
kubectl get all -n microservice-platform

echo -e "${GREEN}Deployment complete!${NC}"
echo -e "${YELLOW}Check status with: kubectl get all -n microservice-platform${NC}"
```

Save as `deploy.sh` and run: `bash deploy.sh`

---

## 📞 Useful Commands Reference

```bash
# Quick checks
kubectl get all -n microservice-platform
kubectl describe pod [pod-name] -n microservice-platform
kubectl logs -f [pod-name] -n microservice-platform

# Scale
kubectl scale deployment gateway-service --replicas=5 -n microservice-platform

# Port forward
kubectl port-forward svc/gateway-service 8000:8000 -n microservice-platform

# Execute command
kubectl exec -it [pod-name] -n microservice-platform -- /bin/bash

# View resources
kubectl top nodes
kubectl top pods -n microservice-platform

# Delete
kubectl delete -f 07-gateway-service.yaml -n microservice-platform
```

---

**Status**: ✅ Ready for Kubernetes Deployment

**Version**: 1.0.0

**Date**: 2026-02-11

