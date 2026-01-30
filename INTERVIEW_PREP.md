# KubeSent - Technical Interview Guide 🎓

## 1. Project Overview
**KubeSent** is an intelligent "Self-Healing" Kubernetes Operator. It detects pod failures in real-time, uses Generative AI (Google Gemini) to diagnose the root cause from logs/configs, and automatically applies fixes to recover the application.

**Core Philosophy:** "Detect -> Diagnose -> Heal" loop.

---

## 2. Architecture & Tech Stack

### 🅰️ Java Operator ( The "Brain" )
*   **Role:** Acts as the Kubernetes Controller. It watches the cluster state and executes actions.
*   **Tech Stack:** Java 17, Spring Boot 3.2, **Fabric8 Kubernetes Client**.
*   **Key Concepts:**
    *   **Informer/Watcher Pattern:** Instead of polling (asking "is everything okay?" every 5s), we open a persistent HTTP stream to the Kubernetes API Server. K8s pushes `ADDED`, `MODIFIED`, `DELETED` events to us instantly.
    *   **Reconciliation:** We compare the *desired state* (running pod) with the *current state* (crashed pod) and take action to reconcile them.

### 🅱️ Python AI Agent ( The "Doctor" )
*   **Role:** Analyzes raw logs and YAML to produce structured diagnoses.
*   **Tech Stack:** Python 3.9+, **FastAPI** (Async Web Framework), Google Generative AI SDK, Pydantic.
*   **Key Concepts:**
    *   **Microservice Pattern:** Decoupled from the heavy Java Operator. Allows independent scaling and easier Python-native AI integration.
    *   **Resilience:** Implements tiered model fallbacks (see Section 4).

---

## 3. "How It Works" - The Deep Dive 🕵️‍♂️

### Phase 1: Detection (Java)
*   **Class:** `PodWatcherService.java`
*   **Mechanism:** Uses `server-sent events` (SSE) via the Fabric8 client to listen to Pod events.
*   **Filter Logic:** We ignore healthy pods. We only react if:
    *   State is `CrashLoopBackOff`
    *   State is `ImagePullBackOff` / `ErrImagePull`
    *   State is `OOMKilled` (Exit Code 137)

### Phase 2: Diagnosis (Python)
*   **Flow:** Operator extracts **Last 50 Log Lines** + **Pod YAML** -> POST `/analyze` -> Python Agent.
*   **Prompt Engineering:** We don't just ask "What's wrong?". We use a **Structured Prompt** that forces the AI to return Valid JSON with:
    1.  `root_cause` (Human readable text)
    2.  `suggested_fix_yaml` (Machine executable patch)
    3.  `confidence_score` (0-100)

### Phase 3: Healing (Java)
*   **Class:** `RemediationService.java`
*   **Validation:** Implementation of a **Confidence Gate**. If `score < 90`, we discard the fix (Safety First).
*   **The "OOMKilled" Challenge:**
    *   *Problem:* You CANNOT change `resources.limits.memory` on a running pod. Kubernetes API returns `403 Forbidden` (Immutable Field).
    *   *Solution (Force Replace Strategy):*
        1.  **Extract** the old pod spec.
        2.  **Merge** the AI's suggested memory limit into the spec.
        3.  **Delete** the old pod.
        4.  **Poll (Wait)** loop until the pod is actually gone (to prevent name collisions).
        5.  **Create** the new pod with the upgraded memory.
    *   *Why this matters:* This demonstrates handling of **Distributed System Race Conditions** and **Immutable Infrastructure constraints**.

---

## 4. Resilience & System Design Patterns 🛡️

### A. Model Fallback Chain (Chain of Responsibility)
*   **Problem:** Large Language Models (LLMs) are unreliable. They hit Rate Limits (429) or get depreciated (404).
*   **Solution:** We implemented a **Priority Fallback Implementation**:
    1.  **Try** `gemini-2.5-flash` (Fast, Modern).
    2.  **Catch** 429/404/500 errors.
    3.  **Retry** with `gemini-2.0-flash`.
    4.  **Final Fallback** to `gemini-2.0-flash-lite`.
*   *Interview Key:* This shows you understand **Fault Tolerance** in distributed AI systems.

### B. Rate Limit Backpressure
*   **Problem:** If the AI quota is full, the Operator keeps retrying, potentially DDoS-ing the AI service.
*   **Solution:** The Python Agent catches quota errors and returns **HTTP 429**. The Java Operator (client) logs this graceful failure instead of crashing. This establishes **Backpressure signaling**.

---

## 5. Potential Interview Questions

**Q: Why use a separate Python service instead of calling Gemini directly from Java?**
*   **A:** Separation of Concerns. Python has first-class support for AI/ML libraries (LangChain/Google SDK). It allows the "Intelligence" layer to evolve independently (e.g., swapping Gemini for OpenAI) without recompiling the core K8s Operator.

**Q: How do you handle race conditions when deleting a pod?**
*   **A:** Kubernetes deletion is asynchronous (graceful termination). If we immediately try to create the new pod, we get a `409 Conflict` (Name Exists). I implemented a **Polling Wait Loop** in Java that checks `pod == null` before proceeding with creation.

**Q: What happens if the AI suggests a dangerous fix?**
*   **A:** We have a **Confidence Threshold** (90%). We also use **Structured JSON Output** to ensure the AI only patches specific fields (`spec.containers[*].resources`) rather than executing arbitrary commands.

**Q: How does this scale?**
*   **A:** The Operator uses **Informers**, which cache cluster state locally. This reduces load on the API server. For massive clusters, we would implement **Work Queues** and **Leader Election** (running multiple replicas of the operator), but for this scope, a single replica is sufficient.
