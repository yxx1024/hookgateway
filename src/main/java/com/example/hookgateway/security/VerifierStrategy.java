package com.example.hookgateway.security;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.model.WebhookEvent;

public interface VerifierStrategy {
    /**
     * Verify the webhook payload against the signature using full context.
     *
     * @param event The webhook event containing headers and payload
     * @param sub   The subscription configuration containing secrets/keys
     * @return true if valid, false otherwise
     */
    boolean verify(WebhookEvent event, Subscription sub);
}
