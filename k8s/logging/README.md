# Kubernetes Logging Stack

This directory contains the cluster-level EFK skeleton for the microservice platform.

## Purpose

- `Fluentd` collects pod logs from Kubernetes nodes.
- `Elasticsearch` stores operational logs.
- `Kibana` provides search and dashboards.

This stack is infrastructure. It is not a replacement for `audit-log-service`.

## Files

- `00-logging-namespace.yaml`: Namespace for logging components.
- `01-elasticsearch-config.yaml`: Elasticsearch environment and service settings.
- `02-elasticsearch.yaml`: Elasticsearch service and `StatefulSet`.
- `03-kibana.yaml`: Kibana config, service, and deployment.
- `04-fluentd-rbac.yaml`: Service account, cluster role, and binding for Fluentd metadata access.
- `05-fluentd-config.yaml`: Fluentd input, filter, and Elasticsearch output configuration.
- `06-fluentd-daemonset.yaml`: Fluentd `DaemonSet` that tails `/var/log/containers`.

## Deployment Order

```bash
kubectl apply -f 00-logging-namespace.yaml
kubectl apply -f 01-elasticsearch-config.yaml
kubectl apply -f 02-elasticsearch.yaml
kubectl apply -f 03-kibana.yaml
kubectl apply -f 04-fluentd-rbac.yaml
kubectl apply -f 05-fluentd-config.yaml
kubectl apply -f 06-fluentd-daemonset.yaml
```

## Verification

```bash
kubectl get pods -n logging
kubectl get svc -n logging
kubectl logs -n logging daemonset/fluentd
kubectl port-forward -n logging svc/kibana 5601:5601
```

## Implementation Notes

- Replace image tags with versions approved by your environment.
- Replace example storage class names with your cluster storage class.
- Create Kubernetes secrets for Elasticsearch credentials before enabling authentication.
- Keep logging Elasticsearch isolated from `product-service` search data.
