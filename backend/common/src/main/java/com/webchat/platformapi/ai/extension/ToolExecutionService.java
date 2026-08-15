package com.webchat.platformapi.ai.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webchat.platformapi.webread.WebReadService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ToolExecutionService {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZONE_ID);

    private final ObjectMapper objectMapper;
    private final WebReadService webReadService;
    private final ObjectProvider<McpClientService> mcpClientServiceProvider;

    public ToolExecutionService(
            ObjectMapper objectMapper,
            WebReadService webReadService,
            ObjectProvider<McpClientService> mcpClientServiceProvider
    ) {
        this.objectMapper = objectMapper;
        this.webReadService = webReadService;
        this.mcpClientServiceProvider = mcpClientServiceProvider;
    }

    public ToolExecutionResult execute(String toolName, Map<String, Object> arguments) throws ToolExecutionException {
        if (toolName != null && toolName.startsWith("mcp_s")) {
            return executeMcpTool(toolName, arguments);
        }
        return switch (toolName) {
            case "current_time" -> executeCurrentTime();
            case "calculator" -> executeCalculator(arguments);
            case "web_page_summary" -> executeWebPageSummary(arguments);
            default -> throw new ToolExecutionException("unsupported tool: " + toolName);
        };
    }

    private ToolExecutionResult executeCurrentTime() throws ToolExecutionException {
        try {
            Instant now = Instant.now();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("timezone", ZONE_ID.toString());
            payload.put("iso", FORMATTER.format(now));
            payload.put("epochMillis", System.currentTimeMillis());
            return new ToolExecutionResult("success", objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new ToolExecutionException("current_time failed", e);
        }
    }

    private ToolExecutionResult executeMcpTool(String toolName, Map<String, Object> arguments) throws ToolExecutionException {
        McpClientService mcpClientService = mcpClientServiceProvider.getIfAvailable();
        if (mcpClientService == null) {
            throw new ToolExecutionException("mcp is disabled");
        }
        try {
            Map<String, Object> payload = mcpClientService.executeToolDefinition(toolName, arguments);
            return new ToolExecutionResult("success", objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new ToolExecutionException("mcp tool failed: " + e.getMessage(), e);
        }
    }

    private ToolExecutionResult executeCalculator(Map<String, Object> arguments) throws ToolExecutionException {
        try {
            double left = number(arguments.get("left"), "left");
            double right = number(arguments.get("right"), "right");
            String operator = string(arguments.get("operator"));
            double result = switch (operator) {
                case "+" -> left + right;
                case "-" -> left - right;
                case "*" -> left * right;
                case "/" -> {
                    if (right == 0.0d) throw new ToolExecutionException("division by zero");
                    yield left / right;
                }
                default -> throw new ToolExecutionException("invalid operator");
            };
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("left", left);
            payload.put("right", right);
            payload.put("operator", operator);
            payload.put("result", result);
            return new ToolExecutionResult("success", objectMapper.writeValueAsString(payload));
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException("calculator failed", e);
        }
    }

    private ToolExecutionResult executeWebPageSummary(Map<String, Object> arguments) throws ToolExecutionException {
        String url = string(arguments.get("url"));
        if (url.isBlank()) throw new ToolExecutionException("url is required");
        WebReadService.WebReadResult result = webReadService.read(url);
        if (!result.success()) throw new ToolExecutionException(result.error());
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("url", result.url());
            payload.put("title", result.title());
            payload.put("content", result.content());
            payload.put("truncated", result.truncated());
            payload.put("contentLength", result.contentLength());
            return new ToolExecutionResult("success", objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new ToolExecutionException("web_page_summary failed", e);
        }
    }

    private static double number(Object value, String field) throws ToolExecutionException {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            throw new ToolExecutionException("invalid " + field);
        }
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record ToolExecutionResult(String status, String output) {}

    public static class ToolExecutionException extends Exception {
        public ToolExecutionException(String message) {
            super(message);
        }

        public ToolExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
