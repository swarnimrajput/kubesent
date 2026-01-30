## KubeSent Testing Status

### ✅ Services Running
- Python AI Agent: Running on port 8000 (health check OK)
- Java Operator: Running via Maven

### ✅ Test Pod Deployed
- Pod: `test-oomkilled-pod`
- Status: OOMKilled (expected behavior)
- Container trying to allocate 50MB with only 10Mi limit
- Restart count: 6+ restarts

### 🔍 Expected Behavior
The Java operator should:
1. Detect the OOMKilled event via Watcher
2. Extract pod logs and YAML
3. Call Python AI Agent at http://localhost:8000/analyze
4. Receive fix suggestion (increase memory limit to 200Mi)
5. Apply the patch automatically (if confidence > 90%)

### 📊 Verification Steps

Check operator logs to see if it's detecting the failure:
```bash
# In the terminal running mvn spring-boot:run
# Look for log messages like:
# "Pod failure detected: default/test-oomkilled-pod - OOMKilled"
# "Sending analysis request to AI Agent"
# "Received AI analysis with confidence: X%"
# "Applying remediation patch"
```

If operator is NOT detecting failures, possible causes:
1. Operator not watching the correct namespace (check application.yml)
2. Operator can't connect to Kubernetes API (check kubeconfig)
3. Operator started before Minikube was ready

### 🔧 Troubleshooting

If operator is silent, try:
1. Check operator logs in the terminal running `mvn spring-boot:run`
2. Verify namespace: `kubectl config view --minify | grep namespace`
3. Restart operator after Minikube is running
4. Check application.yml namespace matches `default`
