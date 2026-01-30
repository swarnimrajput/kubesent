# KubeSent - GenAI-Driven Kubernetes Incident Response Operator

<div align="center">

**Automate the "Detect → Diagnose → Heal" loop for Kubernetes pod failures using AI**

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Python](https://img.shields.io/badge/Python-3.9+-blue.svg)](https://www.python.org/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.104+-009688.svg)](https://fastapi.tiangolo.com/)

</div>

---

## 🚀 Overview

**KubeSent** is a self-healing Kubernetes operator that detects pod failures, analyzes them using GenAI, and automatically patches the cluster. It eliminates alert fatigue by automating incident response for production environments.

### Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                          │
│                                                                 │
│  ┌──────────┐                                                  │
│  │  Pod ❌  │ ──► CrashLoopBackOff / OOMKilled                 │
│  └──────────┘                                                  │
│       │                                                         │
│       ▼                                                         │
│  ┌─────────────────────────────────────┐                      │
│  │   Java Operator (Watcher)           │                      │
│  │  - Fabric8 Kubernetes Client        │                      │
│  │  - Detects pod failures             │                      │
│  │  - Extracts logs & YAML             │                      │
│  └─────────────┬───────────────────────┘                      │
│                │                                                │
└────────────────┼────────────────────────────────────────────────┘
                 │
                 │ REST API Call
                 ▼
        ┌────────────────────────────────┐
        │   Python AI Agent (Brain)      │
        │  - FastAPI Service             │
        │  - Google Gemini API           │
        │  - Root cause analysis         │
        │  - Generates YAML patch        │
        └────────────┬───────────────────┘
                     │
                     │ Analysis Response
                     ▼
        ┌────────────────────────────────┐
        │   Remediation Service          │
        │  - Confidence threshold check  │
        │  - Applies K8s patch           │
        │  - Pod auto-heals ✅           │
        └────────────────────────────────┘
```

### Key Features

- ✅ **Automatic Detection**: Monitors pods for `CrashLoopBackOff`, `OOMKilled`, `ImagePullBackOff`
- 🧠 **AI-Powered Diagnosis**: Uses Google Gemini to analyze failures and suggest fixes
- 🔧 **Self-Healing**: Automatically applies patches when confidence > 90%
- 🛡️ **Dry-Run Mode**: Test remediation logic without applying changes
- 📊 **Detailed Logging**: Comprehensive audit trail of all actions
- 🐳 **Docker Support**: Full containerization with docker-compose

---

## 📋 Prerequisites

- **Java 17+** (for the Spring Boot operator)
- **Python 3.9+** (for the AI agent)
- **Maven 3.6+** (for building Java project)
- **Minikube or Kubernetes cluster** (for testing)
- **Docker & Docker Compose** (optional, for containerized deployment)
- **Google Gemini API Key** (get it from [Google AI Studio](https://makersuite.google.com/app/apikey))

---

## 🛠️ Quick Start

### 1. Clone and Navigate

```bash
cd /Users/swarnimrajput/IdeaProjects/kubesent
```

### 2. Set Up Python AI Agent

```bash
cd ai-agent-python

# Create virtual environment
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Configure environment
cp .env.example .env
# Edit .env and add your GEMINI_API_KEY
```

**Start the AI Agent:**

```bash
uvicorn app.main:app --reload --port 8000
```

The service will be available at `http://localhost:8000`

### 3. Set Up Java Operator

In a new terminal:

```bash
cd k8s-operator-java

# Build the project
mvn clean install

# Configure application.yml if needed
# Edit src/main/resources/application.yml

# Run the operator
mvn spring-boot:run
```

The operator will start on port `8080` and begin watching the `default` namespace.

### 4. Start Minikube

```bash
minikube start
```

### 5. Test with OOMKilled Scenario

```bash
# Apply the test pod
kubectl apply -f k8s-manifests/test-oomkilled-pod.yaml

# Watch the logs
kubectl logs -f test-oomkilled-pod

# Check pod status
kubectl get pods -w
```

**What happens:**
1. Pod starts and tries to allocate 50MB memory
2. Hits the 10MB limit and gets `OOMKilled`
3. KubeSent operator detects the failure
4. Extracts logs and sends to AI Agent
5. AI Agent analyzes and suggests increasing memory to 200MB
6. Operator applies the patch automatically
7. Pod recovers! ✅

---

## 🐳 Docker Deployment (Alternative)

### Option 1: Docker Compose (Recommended for Local Development)

```bash
# Set your Gemini API key
export GEMINI_API_KEY="your_api_key_here"

# Start both services
docker-compose up --build
```

### Option 2: Individual Containers

**AI Agent:**
```bash
cd ai-agent-python
docker build -t kubesent-ai-agent .
docker run -p 8000:8000 -e GEMINI_API_KEY="your_key" kubesent-ai-agent
```

**Java Operator:**
```bash
cd k8s-operator-java
docker build -t kubesent-operator .
docker run -p 8080:8080 \
  -v ~/.kube/config:/root/.kube/config:ro \
  -e KUBESENT_AI_AGENT_URL=http://host.docker.internal:8000 \
  kubesent-operator
```

---

## ⚙️ Configuration

### Java Operator (`application.yml`)

```yaml
kubesent:
  kubernetes:
    namespace: default  # Namespace to watch
  
  ai-agent:
    url: http://localhost:8000  # AI Agent endpoint
  
  pod-watcher:
    log-lines: 50  # Number of log lines to extract
  
  remediation:
    confidence-threshold: 90.0  # Minimum confidence to auto-apply
    dry-run: false  # Set to true to only log patches
```

### Python AI Agent (`.env`)

```bash
GEMINI_API_KEY=your_gemini_api_key_here
MODEL_NAME=gemini-1.5-flash
TEMPERATURE=0.3  # Lower = more deterministic
MAX_TOKENS=2048
LOG_LEVEL=INFO
```

---

## 🧪 Testing Scenarios

### Test 1: OOMKilled

```bash
kubectl apply -f k8s-manifests/test-oomkilled-pod.yaml
```

**Expected AI Fix:** Increase memory limit from 10Mi to 200Mi

### Test 2: CrashLoopBackOff

```bash
kubectl apply -f k8s-manifests/test-crashloop-pod.yaml
```

**Expected AI Fix:** Modify command to not exit with error

### Test 3: ImagePullBackOff

```bash
kubectl apply -f k8s-manifests/test-imagepull-pod.yaml
```

**Expected AI Fix:** Suggest correct image name or registry credentials

---

## 📊 API Reference

### Python AI Agent

**POST `/analyze`**

Request:
```json
{
  "pod_name": "test-oomkilled-pod",
  "namespace": "default",
  "failure_reason": "OOMKilled",
  "logs": "...",
  "pod_yaml": "..."
}
```

Response:
```json
{
  "root_cause": "Container exceeded memory limit...",
  "suggested_fix_yaml": {
    "spec": {
      "containers": [{
        "name": "memory-hog",
        "resources": {
          "limits": {"memory": "200Mi"}
        }
      }]
    }
  },
  "confidence_score": 95.0
}
```

**GET `/health`**

Health check endpoint.

---

## 🔍 Troubleshooting

### Operator not detecting failures

- Check if the operator is running: `mvn spring-boot:run` output
- Verify namespace configuration matches your pods
- Check Kubernetes client connection: look for "Kubernetes client initialized" log

### AI Agent returning low confidence

- Ensure Gemini API key is valid
- Check logs for parsing errors
- Try adjusting `TEMPERATURE` (lower = more consistent)

### Patches not applying

- Check `dry-run` setting (should be `false`)
- Verify `confidence-threshold` (default: 90.0)
- Check operator logs for patch application errors
- Ensure proper RBAC permissions in the cluster

### Connection refused errors

- Verify AI Agent is running on port 8000
- Check `ai-agent.url` in Java operator config
- Ensure both services are on the same network (Docker)

---

## 🎯 Production Considerations

### Security

- ✅ Use Kubernetes RBAC to limit operator permissions
- ✅ Store Gemini API key in Kubernetes Secrets
- ✅ Enable TLS for inter-service communication
- ✅ Set `dry-run: true` initially to audit AI suggestions

### Scalability

- ✅ Increase executor thread pool size for high pod count
- ✅ Use persistent storage for audit logs
- ✅ Implement rate limiting for AI API calls
- ✅ Consider caching common failure patterns

### Monitoring

- ✅ Integrate with Prometheus for metrics
- ✅ Set up alerting for low confidence scores
- ✅ Track remediation success rate
- ✅ Monitor AI API usage and costs

---

## 📝 Project Structure

```
kubesent/
├── k8s-operator-java/          # Java Spring Boot operator
│   ├── src/main/java/com/kubesent/operator/
│   │   ├── config/             # Kubernetes client config
│   │   ├── model/              # DTOs (AnalysisRequest, AnalysisResponse)
│   │   ├── service/            # Core services
│   │   │   ├── PodWatcherService.java      # ⭐ Main watcher
│   │   │   ├── AiAnalysisService.java      # AI client
│   │   │   └── RemediationService.java     # Patch applier
│   │   └── KubeSentOperatorApplication.java
│   └── pom.xml
├── ai-agent-python/            # Python FastAPI AI agent
│   ├── app/
│   │   ├── routes/
│   │   │   └── analyze.py      # ⭐ /analyze endpoint
│   │   ├── services/
│   │   │   └── diagnosis_service.py  # ⭐ Gemini integration
│   │   ├── models/
│   │   │   └── models.py       # Pydantic models
│   │   └── main.py
│   └── requirements.txt
├── k8s-manifests/              # Test pod manifests
│   ├── test-oomkilled-pod.yaml
│   ├── test-crashloop-pod.yaml
│   └── test-imagepull-pod.yaml
├── docker-compose.yml          # Multi-service orchestration
└── README.md
```

---

## 🤝 Contributing

Contributions are welcome! Areas for improvement:

- Add support for more failure types (Evicted, Pending, etc.)
- Implement caching for repeated failures
- Add Prometheus metrics exporter
- Create Helm chart for deployment
- Support multiple AI providers (OpenAI, Claude, etc.)

---

## 📄 License

MIT License - feel free to use this in your projects!

---

## 🙏 Acknowledgments

Built with:
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client)
- [FastAPI](https://fastapi.tiangolo.com/)
- [Google Gemini](https://ai.google.dev/)

---

**Happy Auto-Healing! 🚀**

For questions or issues, please open a GitHub issue.
