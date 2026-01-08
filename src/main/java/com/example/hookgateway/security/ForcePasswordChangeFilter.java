package com.example.hookgateway.security;

import com.example.hookgateway.model.User;
import com.example.hookgateway.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 强制用户修改初始密码的过滤器。
 */
@Component
@RequiredArgsConstructor
public class ForcePasswordChangeFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    // 此列表内的路径不需要强制修改密码即可访问
    private static final List<String> ALLOWED_PATHS = Arrays.asList(
            "/change-password",
            "/logout",
            "/login",
            "/css/",
            "/js/",
            "/webjars/",
            "/error",
            "/hooks/" // Webhook 摄入端点必须允许匿名访问，不应被拦截
    );

    /**
     * 检查是否需要强制修改密码。
     *
     * @param request     请求
     * @param response    响应
     * @param filterChain 过滤链
     * @throws ServletException Servlet 异常
     * @throws IOException      IO 异常
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // 1. 如果路径在白名单中，直接放行
        if (ALLOWED_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. 检查用户认证状态
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof UserDetails) {
            String username = ((UserDetails) auth.getPrincipal()).getUsername();

            // 为了安全起见，从数据库重新查询一次 passwordChanged 状态
            // (虽然 SecurityContext 中通常是缓存的，但我们刚刚从数据库更新完)
            // 考虑性能，这里可能是一个小损耗，但对于后台管理系统通常可以接受
            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null && !user.isPasswordChanged()) {
                // 如果未修改过密码，强制重定向
                response.sendRedirect("/change-password");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
