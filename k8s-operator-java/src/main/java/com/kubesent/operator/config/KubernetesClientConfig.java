package com.kubesent.operator.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Kubernetes Client.
 * Auto-detects in-cluster vs. local (kubeconfig) environment.
 */
@Slf4j
@Configuration
public class KubernetesClientConfig {

    @Value("${kubesent.kubernetes.namespace:default}")
    private String namespace;

    @Bean
    public KubernetesClient kubernetesClient() {
        Config config = new ConfigBuilder()
                .withNamespace(namespace)
                .build();

        KubernetesClient client = new KubernetesClientBuilder()
                .withConfig(config)
                .build();

        log.info("Kubernetes client initialized. Namespace: {}, Master URL: {}",
                client.getNamespace(), client.getConfiguration().getMasterUrl());

        return client;
    }
}
