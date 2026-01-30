package com.kubesent.operator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO sent to the Python AI Agent for analysis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisRequest {

    @JsonProperty("pod_name")
    private String podName;

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("logs")
    private String logs;

    @JsonProperty("pod_yaml")
    private String podYaml;
}
