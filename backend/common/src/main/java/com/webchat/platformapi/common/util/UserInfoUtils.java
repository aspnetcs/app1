package com.webchat.platformapi.common.util;

import com.webchat.platformapi.user.UserEntity;

import java.util.LinkedHashMap;
import java.util.Map;

public final class UserInfoUtils {

    private UserInfoUtils() {
    }

    public static Map<String, Object> toUserInfo(UserEntity user, boolean wxBound) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("userId", user.getId().toString());
        info.put("phone", user.getPhone() == null ? "" : user.getPhone());
        info.put("email", user.getEmail() == null ? "" : user.getEmail());
        info.put("avatar", user.getAvatar() == null ? "" : user.getAvatar());
        info.put("role", user.getRole() == null || user.getRole().isBlank() ? "user" : user.getRole());
        info.put("wxBound", wxBound);
        info.put("createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString());
        return info;
    }

    public static Map<String, Object> toGuestUserInfo(UserEntity user, String nickName, String role) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("userId", user.getId().toString());
        info.put("phone", "");
        info.put("email", "");
        info.put("nickName", nickName == null ? "" : nickName);
        info.put("avatar", user.getAvatar() == null ? "" : user.getAvatar());
        info.put("role", role == null || role.isBlank() ? "guest" : role);
        info.put("wxBound", false);
        info.put("createdAt", user.getCreatedAt() == null ? "" : user.getCreatedAt().toString());
        return info;
    }
}
