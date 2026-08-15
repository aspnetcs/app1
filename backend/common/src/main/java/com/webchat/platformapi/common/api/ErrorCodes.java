package com.webchat.platformapi.common.api;

/**
 * 统一错误码（与 legacy Node 后端语义对齐：_reference/legacy/server/constants/errorCodes.js）
 */
public final class ErrorCodes {
    private ErrorCodes() {}

    // 通用
    public static final int SUCCESS = 0;
    public static final int SERVER_ERROR = -1;

    // 参数校验
    public static final int INVALID_PHONE = 1;
    public static final int INVALID_CODE = 2;
    public static final int INVALID_PASSWORD = 3;
    public static final int CAPTCHA_FAILED = 4;
    public static final int INVALID_EMAIL = 5;

    // 业务逻辑
    public static final int USER_NOT_FOUND = 10;
    public static final int USER_EXISTS = 11;
    public static final int PASSWORD_NOT_SET = 12;
    public static final int PASSWORD_ALREADY_SET = 13;
    public static final int WRONG_PASSWORD = 14;
    public static final int CODE_EXPIRED = 15;
    public static final int ACCOUNT_LOCKED = 16;

    // 第三方
    public static final int WX_LOGIN_FAILED = 20;
    public static final int WX_PHONE_FAILED = 21;
    public static final int WX_API_ERROR = 22;
    public static final int PARAM_MISSING = 23;
    public static final int PARAM_INVALID = 24;

    // 频率限制
    public static final int RATE_LIMIT = 30;
    public static final int SEND_LIMIT = 31;

    // 邮箱绑定
    public static final int NEED_BIND_EMAIL = 40;

    // 认证
    public static final int CREDITS_INSUFFICIENT = 50;
    public static final int CREDITS_RESERVE_FAILED = 51;
    public static final int MODEL_NOT_ALLOWED = 52;
    public static final int CREDITS_ACCOUNT_NOT_FOUND = 53;
    public static final int UNAUTHORIZED = 401;
}
