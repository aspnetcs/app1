package com.webchat.platformapi.ai.extension;

import com.webchat.platformapi.config.SysConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolRuntimeConfigServiceTest {

    @Test
    void refreshLoadsPersistedValuesFromSysConfig() {
        ToolConfigProperties properties = new ToolConfigProperties();
        SysConfigService sysConfigService = mock(SysConfigService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SysConfigService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sysConfigService);
        when(sysConfigService.get("function_calling.enabled")).thenReturn(Optional.of("true"));
        when(sysConfigService.get("function_calling.max_steps")).thenReturn(Optional.of("9"));
        when(sysConfigService.get("function_calling.tools.current_time.enabled")).thenReturn(Optional.of("false"));
        when(sysConfigService.get("function_calling.tools.calculator.enabled")).thenReturn(Optional.of("true"));
        when(sysConfigService.get("function_calling.tools.web_page_summary.enabled")).thenReturn(Optional.of("false"));

        ToolRuntimeConfigService service = new ToolRuntimeConfigService(properties, provider);

        ToolConfigProperties current = service.refresh();

        assertTrue(current.isEnabled());
        assertEquals(9, current.getMaxSteps());
        assertFalse(current.getTools().getCurrentTime().isEnabled());
        assertTrue(current.getTools().getCalculator().isEnabled());
        assertFalse(current.getTools().getWebPageSummary().isEnabled());
    }

    @Test
    void applyPersistsUpdatedFlagsAndLimits() {
        ToolConfigProperties properties = new ToolConfigProperties();
        SysConfigService sysConfigService = mock(SysConfigService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<SysConfigService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sysConfigService);
        when(sysConfigService.get(anyString())).thenReturn(Optional.empty());

        ToolRuntimeConfigService service = new ToolRuntimeConfigService(properties, provider);

        ToolConfigProperties current = service.apply(Map.of(
                "enabled", false,
                "maxSteps", 7,
                "tools", Map.of(
                        "current_time", Map.of("enabled", false),
                        "calculator", Map.of("enabled", false),
                        "web_page_summary", Map.of("enabled", true)
                )
        ));

        assertFalse(current.isEnabled());
        assertEquals(7, current.getMaxSteps());
        assertFalse(current.getTools().getCurrentTime().isEnabled());
        assertFalse(current.getTools().getCalculator().isEnabled());
        assertTrue(current.getTools().getWebPageSummary().isEnabled());
        verify(sysConfigService).set("function_calling.enabled", "false");
        verify(sysConfigService).set("function_calling.max_steps", "7");
        verify(sysConfigService).set("function_calling.tools.current_time.enabled", "false");
        verify(sysConfigService).set("function_calling.tools.calculator.enabled", "false");
        verify(sysConfigService).set("function_calling.tools.web_page_summary.enabled", "true");
    }
}
