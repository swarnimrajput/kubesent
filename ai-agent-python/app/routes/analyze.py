from fastapi import APIRouter, HTTPException
from app.models.models import AnalysisRequest, AnalysisResponse
from app.services.diagnosis_service import DiagnosisService
import logging

logger = logging.getLogger(__name__)
router = APIRouter()

# Lazy initialization - will be created on first request
_diagnosis_service = None

def get_diagnosis_service():
    """Lazy initialization of diagnosis service to ensure .env is loaded first."""
    global _diagnosis_service
    if _diagnosis_service is None:
        _diagnosis_service = DiagnosisService()
    return _diagnosis_service


@router.post("/analyze", response_model=AnalysisResponse)
async def analyze_pod_failure(request: AnalysisRequest):
    """
    Analyzes Kubernetes pod failure using GenAI.
    
    Args:
        request: Analysis request containing pod details and logs
    
    Returns:
        Analysis response with root cause, fix, and confidence score
    """
    try:
        logger.info(f"Received analysis request for pod: {request.namespace}/{request.pod_name}")
        
        diagnosis_service = get_diagnosis_service()
        
        result = diagnosis_service.analyze_pod_failure(
            pod_name=request.pod_name,
            namespace=request.namespace,
            failure_reason=request.failure_reason,
            logs=request.logs,
            pod_yaml=request.pod_yaml
        )
        
        response = AnalysisResponse(
            root_cause=result["root_cause"],
            suggested_fix_yaml=result["suggested_fix_yaml"],
            confidence_score=result["confidence_score"]
        )
        
        logger.info(f"Analysis successful for {request.namespace}/{request.pod_name}")
        
        return response
        
    except Exception as e:
        error_msg = str(e)
        logger.error(f"Analysis failed: {error_msg}")
        
        # Check for rate limit/quota errors
        if "429" in error_msg or "Quota exceeded" in error_msg or "Resource has been exhausted" in error_msg:
            raise HTTPException(status_code=429, detail="AI Quota Exceeded. Please retry later.")
            
        raise HTTPException(status_code=500, detail=f"Analysis failed: {error_msg}")

