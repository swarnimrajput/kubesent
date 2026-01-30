import os
import json
import yaml
import logging
import google.generativeai as genai

logger = logging.getLogger(__name__)


class DiagnosisService:
    """Service for AI-powered pod failure diagnosis using Google Gemini."""
    
    def __init__(self):
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise ValueError("GEMINI_API_KEY environment variable not set")
        
        # Configure Gemini API
        genai.configure(api_key=api_key)
        
        self.temperature = float(os.getenv("TEMPERATURE", "0.3"))
        self.max_tokens = int(os.getenv("MAX_TOKENS", "2048"))
        
        # Build list of fallback models
        self.available_models = self._get_fallback_models(os.getenv("MODEL_NAME"))
        logger.info(f"DiagnosisService initialized with {len(self.available_models)} candidate models: {self.available_models}")

    def _get_fallback_models(self, requested_model: str) -> list[str]:
        """
        Returns a prioritized list of models to try.
        Always includes core stable models to ensure we have valid fallbacks.
        """
        # Core models we trust and want to try in order
        priorities = [
            # Primary: Newest fast model
            "gemini-2.5-flash",
            # Fallbacks: Proven stable models
            "gemini-2.0-flash", 
            "gemini-2.0-flash-lite",
            "gemini-flash-latest",
        ]

        candidates = []
        
        # 1. Add requested model first if provided and valid
        # We skip 'gemini-pro' here because it is deprecated/404ing
        if requested_model and requested_model != "gemini-pro" and requested_model not in priorities:
            candidates.append(requested_model)
            
        # 2. Add core priorities
        for p in priorities:
            if p not in candidates:
                candidates.append(p)
                
        return candidates
    
    def analyze_pod_failure(self, pod_name: str, namespace: str, failure_reason: str, 
                           logs: str, pod_yaml: str) -> dict:
        """
        Analyzes pod failure using GenAI and returns diagnosis with remediation.
        Tries multiple models if failures occur.
        """
        prompt = self._build_prompt(pod_name, namespace, failure_reason, logs, pod_yaml)
        
        last_exception = None
        
        for model_name in self.available_models:
            try:
                logger.info(f"Analyzing pod failure {namespace}/{pod_name} using model: {model_name}")
                
                model = genai.GenerativeModel(model_name)
                
                generation_config = {
                    "temperature": self.temperature,
                    "max_output_tokens": self.max_tokens,
                }
                
                response = model.generate_content(
                    prompt,
                    generation_config=generation_config
                )
                
                result = self._parse_response(response.text)
                
                logger.info(f"Analysis complete with {model_name}. Confidence: {result['confidence_score']}%")
                return result
                
            except Exception as e:
                logger.warning(f"Model {model_name} failed: {str(e)}")
                last_exception = e
                continue
        
        # If we get here, all models failed
        logger.error(f"All models failed to analyze pod failure. Last error: {last_exception}")
        raise last_exception
    
    def _build_prompt(self, pod_name: str, namespace: str, failure_reason: str, 
                     logs: str, pod_yaml: str) -> str:
        """Constructs the prompt for the LLM."""
        
        prompt = f"""You are a Senior DevOps Engineer and Kubernetes expert specializing in incident response.

**Your Mission**: Analyze the following Kubernetes pod failure and provide a precise remediation plan.

**Pod Information**:
- Pod Name: {pod_name}
- Namespace: {namespace}
- Failure Reason: {failure_reason}

**Pod Logs** (last 50 lines):
```
{logs}
```

**Pod YAML Configuration**:
```yaml
{pod_yaml}
```

**Your Task**:
1. Analyze the logs and YAML to determine the ROOT CAUSE of the failure
2. Suggest a Kubernetes YAML PATCH that will fix the issue
3. Provide a confidence score (0-100) for your diagnosis

**Response Format** (MUST be valid JSON):
{{
  "root_cause": "Clear explanation of what caused the failure",
  "suggested_fix_yaml": {{
    "spec": {{
      "containers": [
        {{
          "name": "container-name",
          "resources": {{
            "limits": {{
              "memory": "200Mi"
            }}
          }}
        }}
      ]
    }}
  }},
  "confidence_score": 95.0
}}

**Important Guidelines**:
- For OOMKilled: Increase memory limits (2x-5x current limit)
- For CrashLoopBackOff: Fix startup commands, environment variables, or dependencies
- For ImagePullBackOff: Check image name, registry authentication
- The suggested_fix_yaml should be a strategic merge patch (partial YAML)
- Confidence score should reflect certainty in diagnosis (>90 = very confident)

RESPOND ONLY WITH THE JSON. NO ADDITIONAL TEXT.
"""
        return prompt
    
    def _parse_response(self, response_text: str) -> dict:
        """Parses the LLM response and extracts structured data."""
        
        try:
            # Remove markdown code blocks if present
            cleaned_text = response_text.strip()
            if cleaned_text.startswith("```json"):
                cleaned_text = cleaned_text[7:]
            if cleaned_text.startswith("```"):
                cleaned_text = cleaned_text[3:]
            if cleaned_text.endswith("```"):
                cleaned_text = cleaned_text[:-3]
            cleaned_text = cleaned_text.strip()
            
            # Parse JSON response
            result = json.loads(cleaned_text)
            
            # Validate required fields
            if "root_cause" not in result:
                raise ValueError("Missing 'root_cause' in response")
            if "suggested_fix_yaml" not in result:
                raise ValueError("Missing 'suggested_fix_yaml' in response")
            if "confidence_score" not in result:
                # Default to 70 if not provided
                result["confidence_score"] = 70.0
            
            # Ensure confidence score is within bounds
            result["confidence_score"] = max(0.0, min(100.0, float(result["confidence_score"])))
            
            return result
            
        except json.JSONDecodeError as e:
            logger.error(f"Failed to parse JSON response: {e}")
            logger.error(f"Response text: {response_text}")
            
            # Fallback response
            return {
                "root_cause": "Failed to parse AI response. Manual investigation required.",
                "suggested_fix_yaml": {},
                "confidence_score": 0.0
            }
