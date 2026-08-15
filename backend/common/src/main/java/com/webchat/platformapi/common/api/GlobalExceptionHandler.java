package com.webchat.platformapi.common.api;

import com.webchat.platformapi.ai.channel.NoChannelException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;

import java.nio.file.AccessDeniedException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadJson(HttpServletRequest request, HttpMessageNotReadableException e) {
        log.warn("[api] bad json: {} {}", safeMethod(request), safePath(request));
        return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数格式错误");
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadParam(HttpServletRequest request, Exception e) {
        log.warn("[api] bad param: {} {} ({})", safeMethod(request), safePath(request), e.getClass().getSimpleName());
        return ApiResponse.error(ErrorCodes.PARAM_MISSING, "参数错误");
    }

    @ExceptionHandler(NoChannelException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiResponse<Void> handleNoChannel(HttpServletRequest request, NoChannelException e) {
        log.warn("[api] no channel available: {} {}", safeMethod(request), safePath(request));
        return ApiResponse.error(ErrorCodes.SERVER_ERROR, "暂无可用渠道，请稍后再试");
    }

    @ExceptionHandler(ResearchFeatureDisabledException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> handleResearchFeatureDisabled(
            HttpServletRequest request, ResearchFeatureDisabledException e) {
        String reason = e.getMessage() == null ? "功能暂不可用" : e.getMessage();
        log.warn("[api] research feature disabled: {} {} -> {}",
                safeMethod(request), safePath(request), reason);
        return org.springframework.http.ResponseEntity.ok(ApiResponse.error(ErrorCodes.SERVER_ERROR, reason));
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiResponse<Void> handleAccessDenied(HttpServletRequest request, AccessDeniedException e) {
        log.warn("[api] access denied: {} {}", safeMethod(request), safePath(request));
        return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "无权访问");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiResponse<Void> handleDataIntegrity(HttpServletRequest request, DataIntegrityViolationException e) {
        log.warn("[api] data conflict: {} {} ({})", safeMethod(request), safePath(request), e.getMessage());
        return ApiResponse.error(ErrorCodes.SERVER_ERROR, "数据冲突，请重试");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotAllowed(HttpServletRequest request, HttpRequestMethodNotSupportedException e) {
        return ApiResponse.error(ErrorCodes.PARAM_MISSING, "不支持的请求方法: " + e.getMethod());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<ApiResponse<Void>> handleResponseStatus(
            HttpServletRequest request, org.springframework.web.server.ResponseStatusException e) {
        int code = e.getStatusCode().value();
        String reason = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
        log.warn("[api] status exception: {} {} -> {} {}",
                safeMethod(request), safePath(request), code, reason);
        return org.springframework.http.ResponseEntity
                .status(e.getStatusCode())
                .body(ApiResponse.error(code, reason));
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleUnhandled(HttpServletRequest request, Exception e) {
        log.error("[api] unhandled: {} {}", safeMethod(request), safePath(request), e);
        return ApiResponse.error(ErrorCodes.SERVER_ERROR, "服务器错误");
    }

    private static String safeMethod(HttpServletRequest request) {
        return request == null ? "" : String.valueOf(request.getMethod());
    }

    private static String safePath(HttpServletRequest request) {
        return request == null ? "" : String.valueOf(request.getRequestURI());
    }
}




