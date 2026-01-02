package com.example.hookgateway.controller;

import com.example.hookgateway.model.User;
import com.example.hookgateway.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class PasswordChangeController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/change-password")
    public String showChangePasswordPage(Model model) {
        model.addAttribute("currentUri", "/settings");
        return "change-password";
    }

    @PostMapping("/change-password")
    public String changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            @RequestParam String confirmPassword,
            Model model) {

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "New passwords do not match");
            return "change-password";
        }

        // P2: 密码复杂度验证 - 至少 8 位，包含大小写字母和数字
        if (newPassword.length() < 8) {
            model.addAttribute("error", "Password must be at least 8 characters");
            return "change-password";
        }
        if (!newPassword.matches(".*[A-Z].*")) {
            model.addAttribute("error", "Password must contain at least one uppercase letter");
            return "change-password";
        }
        if (!newPassword.matches(".*[a-z].*")) {
            model.addAttribute("error", "Password must contain at least one lowercase letter");
            return "change-password";
        }
        if (!newPassword.matches(".*\\d.*")) {
            model.addAttribute("error", "Password must contain at least one digit");
            return "change-password";
        }

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            model.addAttribute("error", "Invalid old password");
            return "change-password";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordChanged(true);
        userRepository.save(user);

        return "redirect:/?passwordChanged=true";
    }
}
