# Deploying KubeSent to Minikube 🚀

Follow these steps to deploy the full system (Java Operator + Python AI Agent) inside your Minikube cluster.

## 1. Prerequisites
Ensure Minikube is running:
```bash
minikube start
```

## 2. Point Shell to Minikube Docker Env
This allows you to build images directly inside Minikube (no need to push to Docker Hub!):
```bash
eval $(minikube docker-env)
```

## 3. Build Docker Images
Build both services. This might take a few minutes the first time:

**Python AI Agent:**
```bash
docker build -t ai-agent-python:latest ./ai-agent-python
```

**Java Operator:**
```bash
docker build -t kubesent-operator:latest ./k8s-operator-java
```

## 4. Create Secrets
We need to store your API Key safely in Kubernetes:
```bash
# Replace YOUR_KEY_HERE with your actual key
kubectl create secret generic kubesent-secrets \
  --from-literal=gemini-api-key='YOUR_ACTUAL_API_KEY_HERE'
```

## 5. Deploy to Cluster
Apply the manifests we created:
```bash
kubectl apply -f k8s-manifests/ai-agent-deployment.yaml
kubectl apply -f k8s-manifests/operator-deployment.yaml
```

## 6. Verify Deployment
Check if pods are running:
```bash
kubectl get pods
```

View the logs:
```bash
# Java Operator Logs
kubectl logs -l app=kubesent-operator -f

# Python Agent Logs
kubectl logs -l app=ai-agent -f
```

## 7. Visualize in Dashboard 📊
Start the dashboard:
```bash
minikube dashboard
```
You will see your deployments, services, and pods running visually!

## 8. Test Auto-Healing
Deploy the crashloop pod again:
```bash
kubectl apply -f k8s-manifests/test-crashloop-pod.yaml
```
Watch the operator logs to see it detect, diagnose, and heal the pod inside the cluster!
