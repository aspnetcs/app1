package com.webchat.adminapi.group;

import com.webchat.platformapi.common.api.ErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGroupAdminControllerTest {

    @Mock
    private UserGroupAdminService userGroupService;

    private UserGroupAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new UserGroupAdminController(userGroupService);
    }

    @Test
    void listWrapsPagedResultForAdminClients() {
        var page = new PageImpl<>(
                List.of(Map.<String, Object>of("name", "core-team")),
                PageRequest.of(1, 10),
                11
        );
        when(userGroupService.listForAdmin("core", true, 1, 10)).thenReturn(page);

        var response = controller.list(UUID.randomUUID(), "admin", 1, 10, "core", true);

        assertEquals(0, response.code());
        assertEquals(11L, response.data().get("total"));
        assertEquals(1, response.data().get("page"));
        assertEquals(10, response.data().get("size"));
        verify(userGroupService).listForAdmin(eq("core"), eq(true), eq(1), eq(10));
    }

    @Test
    void createMapsIllegalArgumentExceptionToParamMissing() {
        Map<String, Object> body = Map.of("enabled", true);
        when(userGroupService.create(body)).thenThrow(new IllegalArgumentException("missing name"));

        var response = controller.create(UUID.randomUUID(), "admin", body);

        assertEquals(ErrorCodes.PARAM_MISSING, response.code());
        assertEquals("missing name", response.message());
        assertNull(response.data());
    }

    @Test
    void deleteRejectsNonAdminRole() {
        UUID groupId = UUID.randomUUID();

        var response = controller.delete(UUID.randomUUID(), "user", groupId);

        assertEquals(ErrorCodes.UNAUTHORIZED, response.code());
        verifyNoInteractions(userGroupService);
    }
}
