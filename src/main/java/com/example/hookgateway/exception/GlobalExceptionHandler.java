package com.example.hookgateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理逻辑
 * 确保在生产环境下，所有未捕获的异常都不会向用户泄露堆栈信息。
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理资源未找到异常 (404)
     * 避免像 favicon.ico 这种缺失资源在控制台打印错误堆栈
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
        log.debug("[ResourceNotFound] Path: {}", request.getRequestURI());
        
        if (request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/hooks/")) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", HttpStatus.NOT_FOUND.value());
            error.put("error", "Not Found");
            error.put("path", request.getRequestURI());
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }

        ModelAndView mav = new ModelAndView();
        mav.addObject("message", "Resource not found.");
        mav.setViewName("error");
        mav.setStatus(HttpStatus.NOT_FOUND);
        return mav;
    }

    /**
     * 处理所有 API 异常 (JSON 响应)
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request) {
        log.error("[GlobalException] Path: {}, Error: {}", request.getRequestURI(), e.getMessage(), e);

        // 如果是 API 请求或 Webhook 请求，返回 JSON
        if (request.getRequestURI().startsWith("/api/") || request.getRequestURI().startsWith("/hooks/")) {
            Map<String, Object> error = new HashMap<>();
            error.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
            error.put("error", "Internal Server Error");
            error.put("message", "An unexpected error occurred. Please contact administrator.");
            error.put("path", request.getRequestURI());
            return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 如果是页面请求，返回通用的 Error 页面
        ModelAndView mav = new ModelAndView();
        mav.addObject("message", "An unexpected error occurred.");
        mav.setViewName("error"); 
        return mav;
    }
}
