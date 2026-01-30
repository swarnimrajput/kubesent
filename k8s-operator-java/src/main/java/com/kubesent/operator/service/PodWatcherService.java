package com.kubesent.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.kubesent.operator.model.AnalysisRequest;
import com.kubesent.operator.model.AnalysisResponse;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Core Watcher service that monitors Kubernetes pod events.
 * Detects failures (CrashLoopBackOff, OOMKilled, ImagePullBackOff) and triggers
 * AI analysis.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PodWatcherService implements Watcher<Pod> {

    private final KubernetesClient kubernetesClient;
    private final AiAnalysisService aiAnalysisService;
    private final RemediationService remediationService;

    @Value("${kubesent.kubernetes.namespace:default}")
    private String watchNamespace;

    @Value("${kubesent.pod-watcher.log-lines:50}")
    private Integer logLines;

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    private static final List<String> FAILURE_REASONS = Arrays.asList(
            "CrashLoopBackOff",
            "OOMKilled",
            "ImagePullBackOff",
            "Error",
            "Failed");

    /**
     * Starts watching pods in the configured namespace.
     */
    @PostConstruct
    public void startWatching() {
        log.info("Starting pod watcher for namespace: {}", watchNamespace);

        kubernetesClient.pods()
                .inNamespace(watchNamespace)
                .watch(this);

        log.info("Pod watcher started successfully");
    }

    @Override
    public void eventReceived(Action action, Pod pod) {
        String podName = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();

        log.info("Received {} event for pod: {}/{}", action, namespace, podName);

        // Only process MODIFIED events (status changes)
        if (action != Action.MODIFIED) {
            return;
        }

        // Check if pod has failed
        String failureReason = detectFailure(pod);
        if (failureReason != null) {
            log.warn("Detected failure in pod {}/{}: {}", namespace, podName, failureReason);

            // Process failure asynchronously to avoid blocking the watcher
            executorService.submit(() -> handlePodFailure(pod, failureReason));
        }
    }

    @Override
    public void onClose(WatcherException e) {
        if (e != null) {
            log.error("Pod watcher closed due to exception: {}", e.getMessage(), e);

            // Attempt to restart watcher
            log.info("Attempting to restart pod watcher...");
            try {
                Thread.sleep(5000);
                startWatching();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("Failed to restart pod watcher", ie);
            }
        } else {
            log.info("Pod watcher closed normally");
        }
    }

    /**
     * Detects if pod is in a failure state.
     *
     * @param pod The pod to check
     * @return Failure reason if detected, null otherwise
     */
    private String detectFailure(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return null;
        }

        for (ContainerStatus containerStatus : pod.getStatus().getContainerStatuses()) {
            // Check waiting state (CrashLoopBackOff, ImagePullBackOff)
            if (containerStatus.getState() != null &&
                    containerStatus.getState().getWaiting() != null) {

                String reason = containerStatus.getState().getWaiting().getReason();
                if (reason != null && FAILURE_REASONS.contains(reason)) {
                    return reason;
                }
            }

            // Check terminated state (OOMKilled, Error)
            if (containerStatus.getState() != null &&
                    containerStatus.getState().getTerminated() != null) {

                String reason = containerStatus.getState().getTerminated().getReason();
                if (reason != null && FAILURE_REASONS.contains(reason)) {
                    return reason;
                }
            }

            // Check last terminated state for recent failures
            if (containerStatus.getLastState() != null &&
                    containerStatus.getLastState().getTerminated() != null) {

                String reason = containerStatus.getLastState().getTerminated().getReason();
                if (reason != null && FAILURE_REASONS.contains(reason)) {
                    return reason;
                }
            }
        }

        return null;
    }

    /**
     * Handles pod failure by extracting logs, calling AI analysis, and applying
     * remediation.
     *
     * @param pod           The failed pod
     * @param failureReason The detected failure reason
     */
    private void handlePodFailure(Pod pod, String failureReason) {
        String podName = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();

        try {
            log.info("Processing failure for pod {}/{}", namespace, podName);

            // Extract logs (last N lines)
            String logs = extractPodLogs(namespace, podName);

            // Convert pod to YAML
            String podYaml = convertPodToYaml(pod);

            // Create analysis request
            AnalysisRequest request = AnalysisRequest.builder()
                    .podName(podName)
                    .namespace(namespace)
                    .failureReason(failureReason)
                    .logs(logs)
                    .podYaml(podYaml)
                    .build();

            // Call AI Agent for analysis
            AnalysisResponse analysis = aiAnalysisService.analyzeFailure(request);

            if (analysis != null && analysis.getSuggestedFixYaml() != null) {
                // Apply remediation
                remediationService.applyRemediation(pod, analysis);
            } else {
                log.warn("No valid analysis received for pod {}/{}", namespace, podName);
            }

        } catch (Exception e) {
            log.error("Failed to process pod failure for {}/{}: {}",
                    namespace, podName, e.getMessage(), e);
        }
    }

    /**
     * Extracts the last N lines of logs from the pod.
     *
     * @param namespace Pod namespace
     * @param podName   Pod name
     * @return Pod logs
     */
    private String extractPodLogs(String namespace, String podName) {
        try {
            String logs = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName)
                    .tailingLines(logLines)
                    .getLog();

            log.debug("Extracted {} lines of logs for pod {}/{}", logLines, namespace, podName);
            return logs != null ? logs : "No logs available";

        } catch (Exception e) {
            log.error("Failed to extract logs for pod {}/{}: {}",
                    namespace, podName, e.getMessage());
            return "Failed to retrieve logs: " + e.getMessage();
        }
    }

    /**
     * Converts pod object to YAML string.
     *
     * @param pod The pod to convert
     * @return YAML representation
     */
    private String convertPodToYaml(Pod pod) {
        try {
            return yamlMapper.writeValueAsString(pod);
        } catch (IOException e) {
            log.error("Failed to convert pod to YAML: {}", e.getMessage());
            return "Failed to convert pod to YAML";
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down pod watcher service");
        executorService.shutdown();
    }
}
