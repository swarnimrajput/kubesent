package com.kubesent.operator.service;

import com.kubesent.operator.model.AnalysisRequest;
import com.kubesent.operator.model.AnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service to communicate with the Python AI Agent for pod failure analysis.
 */
@Slf4j
@Service
public class AiAnalysisService {

    @Value("${kubesent.ai-agent.url:http://localhost:8000}")
    private String aiAgentUrl;

    private final RestTemplate restTemplate;

    public AiAnalysisService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Sends pod failure data to AI Agent and receives analysis.
     *
     * @param request Analysis request with logs and YAML
     * @return Analysis response with root cause and suggested fix
     */
    public AnalysisResponse analyzeFailure(AnalysisRequest request) {
        try {
            log.info("Sending analysis request to AI Agent for pod: {}/{}",
                    request.getNamespace(), request.getPodName());

            String url = aiAgentUrl + "/analyze";
            AnalysisResponse response = restTemplate.postForObject(
                    url,
                    request,
                    AnalysisResponse.class);

            if (response != null) {
                log.info("Received AI analysis. Root cause: {}, Confidence: {}",
                        response.getRootCause(), response.getConfidenceScore());
            }

            return response;
        } catch (Exception e) {
            log.error("Failed to get AI analysis for pod {}/{}: {}",
                    request.getNamespace(), request.getPodName(), e.getMessage());
            throw new RuntimeException("AI Analysis failed", e);
        }
    }
}
