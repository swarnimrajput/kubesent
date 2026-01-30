package com.kubesent.operator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for KubeSent Kubernetes Operator.
 * This operator watches for pod failures and uses GenAI to auto-heal them.
 */
@SpringBootApplication
public class KubeSentOperatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(KubeSentOperatorApplication.class, args);
    }
}
