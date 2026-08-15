package com.webchat.adminapi.user;

import com.webchat.platformapi.common.api.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAdminControllerTest {

    @Mock
    private UserAdminService userAdminService;

    private UserAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new UserAdminController(userAdminService);
    }

    @Test
    void updateDelegatesToServiceAndReturnsResult() {
        UUID adminUserId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Map<String, Object> body = Map.of("role", "user");

        Map<String, Object> resultData = new LinkedHashMap<>();
        resultData.put("id", targetUserId.toString());
        resultData.put("role", "user");
        resultData.put("tokenQuota", 0L);
        resultData.put("tokenUsed", 0L);
        when(userAdminService.update(eq(targetUserId), any())).thenReturn(ApiResponse.ok("updated", resultData));

        ApiResponse<Map<String, Object>> response = controller.update(adminUserId, "admin", targetUserId, body);

        assertNotNull(response.data());
        assertEquals("user", response.data().get("role"));
        verify(userAdminService).update(targetUserId, body);
    }

    @Test
    void updateRejectsNonAdmin() {
        UUID userId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Map<String, Object> body = Map.of("role", "admin");

        ApiResponse<Map<String, Object>> response = controller.update(userId, "user", targetUserId, body);

        assertEquals(401, response.code());
    }

    @Test
    void updateRejectsUnauthenticated() {
        UUID targetUserId = UUID.randomUUID();
        Map<String, Object> body = Map.of("role", "admin");

        ApiResponse<Map<String, Object>> response = controller.update(null, null, targetUserId, body);

        assertEquals(401, response.code());
    }

    @Test
    void updateDeclaresTransactionalRollbackBoundary() throws NoSuchMethodException {
        Method method = UserAdminController.class.getMethod(
                "update",
                UUID.class,
                String.class,
                UUID.class,
                Map.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertNotNull(transactional);
        assertEquals(1, transactional.rollbackFor().length);
        assertEquals(Exception.class, transactional.rollbackFor()[0]);
    }
}
