package com.example.hookgateway.controller;

import com.example.hookgateway.model.Subscription;
import com.example.hookgateway.model.WebhookEvent;
import com.example.hookgateway.repository.SubscriptionRepository;
import com.example.hookgateway.repository.WebhookEventRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class DashboardController {

        private final WebhookEventRepository eventRepository;
        private final SubscriptionRepository subscriptionRepository;

        @GetMapping("/")
        public String dashboard(
                        @RequestParam(defaultValue = "subscribed") String tab,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "15") int size,
                        @RequestParam(required = false) String q,
                        Model model) {
                // 1. Get known sources
                Set<String> knownSources = subscriptionRepository.findAll().stream()
                                .map(Subscription::getSource)
                                .collect(Collectors.toSet());

                // 2. Build Dynamic Specification for Filtering
                Specification<WebhookEvent> spec = (root, query, cb) -> {
                        List<Predicate> predicates = new ArrayList<>();

                        // Tab filtering: mapped vs unmapped
                        if ("subscribed".equals(tab)) {
                                predicates.add(root.get("source")
                                                .in(knownSources.isEmpty() ? List.of("__NONE__") : knownSources));
                        } else if ("unmapped".equals(tab)) {
                                if (!knownSources.isEmpty()) {
                                        predicates.add(cb.not(root.get("source").in(knownSources)));
                                }
                        }

                        // Keyword searching: source, method, payload
                        if (q != null && !q.trim().isEmpty()) {
                                String keyword = "%" + q.toLowerCase() + "%";
                                predicates.add(cb.or(
                                                cb.like(cb.lower(root.get("source").as(String.class)), keyword),
                                                cb.like(cb.lower(root.get("method").as(String.class)), keyword),
                                                cb.like(cb.lower(root.get("payload").as(String.class)), keyword)));
                        }

                        return cb.and(predicates.toArray(new Predicate[0]));
                };

                // 3. Execution Query with Pagination
                PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
                Page<WebhookEvent> eventPage = eventRepository.findAll(spec, pageRequest);

                // 4. Count for badges (Simplified, might be expensive in large DB but okay for
                // now)
                long subscribedTotal = eventRepository.count((root, query,
                                cb) -> knownSources.isEmpty() ? cb.disjunction() : root.get("source").in(knownSources));
                long unmappedTotal = eventRepository
                                .count((root, query, cb) -> knownSources.isEmpty() ? cb.conjunction()
                                                : cb.not(root.get("source").in(knownSources)));

                // 5. Model data
                model.addAttribute("tab", tab);
                model.addAttribute("eventPage", eventPage);
                model.addAttribute("query", q);
                model.addAttribute("subscribedCount", subscribedTotal);
                model.addAttribute("unmappedCount", unmappedTotal);
                model.addAttribute("currentUri", "/");

                return "dashboard";
        }

        @GetMapping("/view/{id}")
        public String viewDetail(@PathVariable Long id, Model model) {
                eventRepository.findById(id).ifPresent(event -> {
                        model.addAttribute("event", event);
                        model.addAttribute("currentUri", "/view");
                });
                return "detail";
        }
}
