package com.example.hookgateway.controller;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionRepository repository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("subscriptions", repository.findAll());
        model.addAttribute("currentUri", "/subscriptions");
        return "subscriptions";
    }

    @PostMapping
    public String create(@RequestParam String source,
            @RequestParam(required = false) String targetUrl,
            @RequestParam(defaultValue = "NONE") String filterType,
            @RequestParam(required = false) String filterRule,
            @RequestParam(defaultValue = "NONE") String verifyMethod,
            @RequestParam(required = false) String verifySecret,
            @RequestParam(required = false) String signatureHeader,
            @RequestParam(defaultValue = "HTTP") String destinationType,
            @RequestParam(required = false) String tunnelKey) { // Accept frontend key

        String finalTunnelKey = tunnelKey;
        String finalTargetUrl = targetUrl;

        // V11: Tunnel Support
        if ("TUNNEL".equalsIgnoreCase(destinationType)) {
            // Use frontend key if available, otherwise generate
            if (finalTunnelKey == null || finalTunnelKey.isEmpty()) {
                finalTunnelKey = java.util.UUID.randomUUID().toString();
            }
            finalTargetUrl = "tunnel://" + finalTunnelKey; // Placeholder for DB constraint
        } else {
            finalTunnelKey = null; // Clear key if not Tunnel
        }

        Subscription sub = Subscription.builder()
                .source(source)
                .targetUrl(finalTargetUrl)
                .filterType(filterType)
                .filterRule(filterRule)
                .verifyMethod(verifyMethod)
                .verifySecret(verifySecret)
                .signatureHeader(signatureHeader)
                .destinationType(destinationType)
                .tunnelKey(finalTunnelKey)
                .active(true)
                .build();
        repository.save(sub);
        return "redirect:/subscriptions";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        repository.deleteById(id);
        return "redirect:/subscriptions";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id) {
        repository.findById(id).ifPresent(sub -> {
            sub.setActive(!sub.isActive());
            repository.save(sub);
        });
        return "redirect:/subscriptions";
    }
}
