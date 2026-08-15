package com.webchat.platformapi.ai.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.webchat.platformapi.ai.adapter.openai.OpenAiAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class FunctionCallingServiceTest {

    @Mock
    private ToolCatalogService toolCatalogService;

    @Mock
    private ToolExecutionService toolExecutionService;

    @Mock
    private OpenAiAdapter openAiAdapter;

    @Mock
    private ToolRuntimeConfigService toolRuntimeConfigService;

    private FunctionCallingService service;
    private Method parseArgsMethod;

    @BeforeEach
    void setUp() throws Exception {
        service = new FunctionCallingService(
                toolCatalogService,
                toolExecutionService,
                new ObjectMapper(),
                openAiAdapter,
                toolRuntimeConfigService
        );
        parseArgsMethod = FunctionCallingService.class.getDeclaredMethod(
                "parseArgs",
                com.fasterxml.jackson.databind.JsonNode.class,
                ToolDefinition.class
        );
        parseArgsMethod.setAccessible(true);
    }

    @Test
    void parseArgsAcceptsJsonStringArguments() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parseArgsMethod.invoke(
                service,
                TextNode.valueOf("{\"environment\":\"production\"}"),
                null
        );

        assertEquals(Map.of("environment", "production"), result);
    }

    @Test
    void parseArgsCoercesScalarStringWhenToolHasSinglePropertySchema() throws Exception {
        ToolDefinition definition = new ToolDefinition(
                "mcp_release_window_lookup",
                "Return release window information.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "environment", Map.of("type", "string")
                        )
                )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) parseArgsMethod.invoke(
                service,
                TextNode.valueOf("prod"),
                definition
        );

        assertEquals(Map.of("environment", "prod"), result);
    }

    @Test
    void parseArgsRejectsMalformedJsonStringArguments() {
        InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class,
                () -> parseArgsMethod.invoke(service, TextNode.valueOf("{bad json"), null)
        );

        assertEquals(
                FunctionCallingService.FunctionCallingException.class,
                thrown.getCause().getClass()
        );
        assertEquals("invalid tool arguments", thrown.getCause().getMessage());
    }
}
