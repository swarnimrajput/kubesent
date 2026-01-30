from pydantic import BaseModel, Field
from typing import Optional, Dict, Any


class AnalysisRequest(BaseModel):
    """Request model for pod failure analysis."""
    
    pod_name: str = Field(..., description="Name of the failed pod")
    namespace: str = Field(..., description="Kubernetes namespace")
    failure_reason: str = Field(..., description="Detected failure reason (e.g., OOMKilled)")
    logs: str = Field(..., description="Pod logs (last N lines)")
    pod_yaml: str = Field(..., description="Pod YAML configuration")


class AnalysisResponse(BaseModel):
    """Response model containing AI diagnosis and remediation."""
    
    root_cause: str = Field(..., description="Root cause analysis of the failure")
    suggested_fix_yaml: Dict[str, Any] = Field(..., description="YAML patch to fix the issue")
    confidence_score: float = Field(..., ge=0.0, le=100.0, description="Confidence score (0-100)")
