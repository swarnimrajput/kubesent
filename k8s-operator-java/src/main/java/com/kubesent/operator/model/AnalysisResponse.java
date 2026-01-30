package com.kubesent.operator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Response DTO received from the Python AI Agent containing diagnosis and fix.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResponse {

    @JsonProperty("root_cause")
    private String rootCause;

    @JsonProperty("suggested_fix_yaml")
    private Map<String, Object> suggestedFixYaml;

    @JsonProperty("confidence_score")
    private Double confidenceScore;
}
