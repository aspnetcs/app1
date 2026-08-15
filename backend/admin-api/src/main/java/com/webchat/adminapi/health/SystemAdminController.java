package com.webchat.adminapi.health;

import com.webchat.platformapi.auth.jwt.JwtAuthFilter;
import com.webchat.platformapi.common.api.ApiResponse;
import com.webchat.platformapi.common.api.ErrorCodes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.*;

@RestController
@ConditionalOnProperty(name = "platform.dev-panel", havingValue = "true")
@RequestMapping("/api/v1/admin/system")
public class SystemAdminController {

    private final JdbcTemplate jdbc;

    public SystemAdminController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/info")
    public ApiResponse<Map<String, Object>> info(
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ID, required = false) UUID userId,
            @RequestAttribute(name = JwtAuthFilter.ATTR_USER_ROLE, required = false) String role
    ) {
        if (userId == null) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "未登录");
        if (!"admin".equalsIgnoreCase(role)) return ApiResponse.error(ErrorCodes.UNAUTHORIZED, "权限不足");

        Map<String, Object> result = new LinkedHashMap<>();

        // JVM Memory
        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        var heap = mem.getHeapMemoryUsage();
        var nonHeap = mem.getNonHeapMemoryUsage();
        result.put("jvm", Map.of(
                "heapUsed", heap.getUsed(),
                "heapMax", heap.getMax(),
                "heapCommitted", heap.getCommitted(),
                "nonHeapUsed", nonHeap.getUsed(),
                "nonHeapCommitted", nonHeap.getCommitted()
        ));

        // Threads
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        result.put("threads", Map.of(
                "current", threads.getThreadCount(),
                "peak", threads.getPeakThreadCount(),
                "daemon", threads.getDaemonThreadCount()
        ));

        // Runtime
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        long uptime = runtime.getUptime();
        result.put("runtime", Map.of(
                "uptimeMs", uptime,
                "uptimeHuman", formatUptime(uptime),
                "javaVersion", runtime.getVmVersion(),
                "vmName", runtime.getVmName(),
                "startTime", runtime.getStartTime()
        ));

        // OS
        result.put("os", Map.of(
                "name", System.getProperty("os.name", ""),
                "arch", System.getProperty("os.arch", ""),
                "processors", Runtime.getRuntime().availableProcessors()
        ));

        // Database
        try {
            Map<String, Object> dbInfo = new LinkedHashMap<>();
            String dbVersion = jdbc.queryForObject("SELECT version()", String.class);
            dbInfo.put("version", dbVersion);

            // Table sizes
            List<Map<String, Object>> tableSizes = jdbc.queryForList(
                    "SELECT relname as table_name, n_live_tup as row_count " +
                    "FROM pg_stat_user_tables ORDER BY n_live_tup DESC LIMIT 10"
            );
            dbInfo.put("tables", tableSizes);
            result.put("database", dbInfo);
        } catch (Exception e) {
            result.put("database", Map.of("error", e.getMessage()));
        }

        return ApiResponse.ok(result);
    }

    private static String formatUptime(long ms) {
        long s = ms / 1000;
        long d = s / 86400; s %= 86400;
        long h = s / 3600; s %= 3600;
        long m = s / 60; s %= 60;
        if (d > 0) return d + "天" + h + "小时" + m + "分钟";
        if (h > 0) return h + "小时" + m + "分钟";
        return m + "分钟" + s + "秒";
    }
}



