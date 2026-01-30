package com.kubesent.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kubesent.operator.model.AnalysisResponse;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.PodResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service to apply remediation patches to Kubernetes resources.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RemediationService {

    private final KubernetesClient kubernetesClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kubesent.remediation.confidence-threshold:90.0}")
    private Double confidenceThreshold;

    @Value("${kubesent.remediation.dry-run:false}")
    private Boolean dryRun;

    /**
     * Applies the suggested fix if confidence score exceeds threshold.
     *
     * @param pod      The pod to remediate
     * @param analysis The AI analysis response
     */
    public void applyRemediation(Pod pod, AnalysisResponse analysis) {
        String podName = pod.getMetadata().getName();
        String namespace = pod.getMetadata().getNamespace();

        log.info("Evaluating remediation for pod {}/{}. Confidence: {}, Threshold: {}",
                namespace, podName, analysis.getConfidenceScore(), confidenceThreshold);

        if (analysis.getConfidenceScore() < confidenceThreshold) {
            log.warn("Confidence score {} is below threshold {}. Skipping auto-remediation.",
                    analysis.getConfidenceScore(), confidenceThreshold);
            return;
        }

        if (dryRun) {
            log.info("[DRY-RUN] Would apply patch to pod {}/{}: {}",
                    namespace, podName, analysis.getSuggestedFixYaml());
            return;
        }

        try {
            log.info("Applying remediation patch to pod {}/{}", namespace, podName);

            // Convert suggested fix to JSON string for patch
            String patchJson = objectMapper.writeValueAsString(analysis.getSuggestedFixYaml());

            // Apply strategic merge patch
            PodResource podResource = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .withName(podName);

            Pod patchedPod = podResource.patch(patchJson);

            log.info("Successfully applied remediation patch to pod {}/{}. New resource version: {}",
                    namespace, podName, patchedPod.getMetadata().getResourceVersion());

        } catch (Exception e) {
            log.error("Failed to apply patch for pod {}/{}: {}. Attempting force replace...",
                    namespace, podName, e.getMessage());
            forceReplace(pod, analysis.getSuggestedFixYaml());
        }
    }

    /**
     * Deletes and recreates the pod with the suggested changes.
     * Used when in-place patching is forbidden (e.g., resource updates on
     * standalone pods).
     */
    private void forceReplace(Pod originalPod, Map<String, Object> patchMap) {
        String podName = originalPod.getMetadata().getName();
        String namespace = originalPod.getMetadata().getNamespace();

        try {
            log.info("Starting force replace for pod {}/{}", namespace, podName);

            // 1. Manually merge suggestions into originalPod (Client-side patching)
            // We do this to avoid losing fields like 'image' which Jackson would overwrite
            // if we used simple merge
            Map<String, Object> spec = (Map) patchMap.get("spec");
            if (spec != null) {
                java.util.List<Map<String, Object>> containers = (java.util.List) spec.get("containers");
                if (containers != null) {
                    for (Map<String, Object> containerPatch : containers) {
                        String name = (String) containerPatch.get("name");
                        // Find matching container in original pod
                        originalPod.getSpec().getContainers().stream()
                                .filter(c -> c.getName().equals(name))
                                .findFirst()
                                .ifPresent(target -> {
                                    try {
                                        // Update resources if present
                                        if (containerPatch.containsKey("resources")) {
                                            io.fabric8.kubernetes.api.model.ResourceRequirements reqs = objectMapper
                                                    .convertValue(containerPatch.get("resources"),
                                                            io.fabric8.kubernetes.api.model.ResourceRequirements.class);
                                            target.setResources(reqs);
                                            log.info("Updated resources for container {}", name);
                                        }
                                        // Update image if present (optional support)
                                        if (containerPatch.containsKey("image")) {
                                            target.setImage((String) containerPatch.get("image"));
                                        }
                                    } catch (Exception ex) {
                                        log.error("Failed to merge patch for container {}", name, ex);
                                    }
                                });
                    }
                }
            }

            // 2. Clean metadata for recreation
            originalPod.getMetadata().setResourceVersion(null);
            originalPod.getMetadata().setUid(null);
            originalPod.getMetadata().setCreationTimestamp(null);
            originalPod.setStatus(null); // Clear status

            // 3. Delete old pod
            log.info("Deleting old pod {}/{}", namespace, podName);
            kubernetesClient.pods().inNamespace(namespace).withName(podName).delete();

            // 4. Wait for deletion to complete
            log.info("Waiting for pod {}/{} to be fully deleted...", namespace, podName);
            long timeout = System.currentTimeMillis() + 30000; // 30 seconds timeout
            boolean deleted = false;

            while (System.currentTimeMillis() < timeout) {
                if (kubernetesClient.pods().inNamespace(namespace).withName(podName).get() == null) {
                    deleted = true;
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for pod deletion");
                }
            }

            if (!deleted) {
                log.error("Timed out waiting for pod {}/{} to be deleted", namespace, podName);
                return;
            }

            // 5. Create new pod
            log.info("Creating new pod {}/{}", namespace, podName);
            kubernetesClient.pods().inNamespace(namespace).resource(originalPod).create();

            log.info("Successfully recreated pod {}/{} with applied remediation.", namespace, podName);

        } catch (Exception e) {
            log.error("Force replace failed for pod {}/{}: {}", namespace, podName, e.getMessage(), e);
        }
    }
}
