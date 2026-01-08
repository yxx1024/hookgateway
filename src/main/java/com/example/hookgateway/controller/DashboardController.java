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
                // 1. 获取已订阅的来源
                Set<String> knownSources = subscriptionRepository.findAll().stream()
                                .map(Subscription::getSource)
                                .collect(Collectors.toSet());

                // 2. 构建动态查询条件
                Specification<WebhookEvent> spec = (root, query, cb) -> {
                        List<Predicate> predicates = new ArrayList<>();

                        // Tab 过滤：已订阅 vs 未订阅
                        if ("subscribed".equals(tab)) {
                                predicates.add(root.get("source")
                                                .in(knownSources.isEmpty() ? List.of("__NONE__") : knownSources));
                        } else if ("unmapped".equals(tab)) {
                                if (!knownSources.isEmpty()) {
                                        predicates.add(cb.not(root.get("source").in(knownSources)));
                                }
                        }

                        // 关键字搜索：source、method、payload
                        if (q != null && !q.trim().isEmpty()) {
                                String keyword = "%" + q.toLowerCase() + "%";
                                predicates.add(cb.or(
                                                cb.like(cb.lower(root.get("source").as(String.class)), keyword),
                                                cb.like(cb.lower(root.get("method").as(String.class)), keyword),
                                                cb.like(cb.lower(root.get("payload").as(String.class)), keyword)));
                        }

                        return cb.and(predicates.toArray(new Predicate[0]));
                };

                // 3. 分页查询
                PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
                Page<WebhookEvent> eventPage = eventRepository.findAll(spec, pageRequest);

                // 4. 统计徽章计数（简单实现，大数据量下可能偏重）
                long subscribedTotal = eventRepository.count((root, query,
                                cb) -> knownSources.isEmpty() ? cb.disjunction() : root.get("source").in(knownSources));
                long unmappedTotal = eventRepository
                                .count((root, query, cb) -> knownSources.isEmpty() ? cb.conjunction()
                                                : cb.not(root.get("source").in(knownSources)));

                // 5. 组装页面数据
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
