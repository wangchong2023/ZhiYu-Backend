package com.zhiyu.ufp.common.exception;

import lombok.Getter;

@Getter
public enum BizErrorCode implements ErrorCode {
    // ---- General Errors (40xxx) ----
    VALIDATION_FAILED(40001, "Validation failed", "error.40001"),
    VALIDATION_USERNAME_LENGTH(40011, "Username must be 4-32 characters", "error.40011"),
    VALIDATION_USERNAME_FORMAT(40012, "Username may only contain letters, digits, and underscores", "error.40012"),
    VALIDATION_PASSWORD_LENGTH(40013, "Password must be 8-128 characters", "error.40013"),
    VALIDATION_PASSWORD_FORMAT(40014, "Password must contain uppercase, lowercase, and digits", "error.40014"),
    VALIDATION_EMAIL_FORMAT(40015, "Invalid email format", "error.40015"),
    MALFORMED_BODY(40002, "Malformed request body", "error.40002"),
    UNSUPPORTED_CONTENT_TYPE(40003, "Unsupported Content-Type", "error.40003"),
    MISSING_PARAM(40004, "Missing required parameter", "error.40004"),
    UNSUPPORTED_FILE_TYPE(40021, "Unsupported file type", "error.40021"),
    FILE_SIZE_EXCEEDED(40022, "File size exceeds limit", "error.40022"),
    CANNOT_UNBIND_LAST(40031, "Cannot unbind the last authentication method", "error.40031"),
    DEVICE_LIMIT(40032, "Device limit reached (5 devices)", "error.40032"),
    DELETION_ALREADY_REQUESTED(40033, "Account deletion already requested within 30 days", "error.40033"),
    RECOVERY_EXPIRED(40034, "Account recovery window (30 days) has expired", "error.40034"),
    PLAN_NOT_EXIST(40041, "Plan does not exist or is unavailable", "error.40041"),
    PLAN_SAME_AS_CURRENT(40042, "Target plan is the same as current plan", "error.40042"),
    REFUND_IN_PROGRESS(40043, "A refund request is already in progress", "error.40043"),
    TRIAL_ALREADY_USED(40044, "Trial can only be used once", "error.40044"),
    RESOURCE_NOT_FOUND(40401, "Resource not found", "error.40401"),
    ROLE_NOT_FOUND(40402, "Role not found", "error.40402"),
    REFUND_NOT_FOUND(41401, "Refund not found", "error.41401"),
    REFUND_STATUS_INVALID(41402, "Refund status does not allow review", "error.41402"),
    ROLE_ALREADY_ASSIGNED(41404, "Role already assigned to user", "error.41404"),
    ADMIN_EXISTS(41405, "Admin username already exists", "error.41405"),
    OAUTH_EMAIL_CONFLICT(41601, "This email is already registered, please log in with password and bind your account", "error.41601"),
    OAUTH_IDENTITY_CONFLICT(41602, "Account data anomaly", "error.41602"),
    OAUTH_UNSUPPORTED_PROVIDER(41603, "Unsupported login provider", "error.41603"),
    METHOD_NOT_ALLOWED(40501, "HTTP method not allowed", "error.40501"),
    UNSUPPORTED_MEDIA_TYPE(41501, "Unsupported Media Type", "error.41501"),

    // ---- Authentication Errors (41xxx) ----
    TOKEN_EXPIRED(40101, "Access token expired", "error.40101"),
    INVALID_TOKEN(40102, "Invalid access token", "error.40102"),
    REFRESH_TOKEN_EXPIRED(40103, "Refresh token expired, please log in again", "error.40103"),
    TOKEN_REUSE_DETECTED(40104, "Token reuse detected, all devices have been signed out", "error.40104"),
    INCORRECT_PASSWORD(40105, "Incorrect password", "error.40105"),
    ACCOUNT_LOCKED(40106, "Account temporarily locked, please retry in 15 minutes", "error.40106"),
    ACCOUNT_DISABLED(40107, "Account has been disabled", "error.40107"),
    ACCOUNT_DELETED(40108, "Account has been deleted", "error.40108"),
    VERIFY_CODE_INCORRECT(40109, "Verification code is incorrect or expired", "error.40109"),
    VERIFY_CODE_TOO_FREQUENT(40110, "Verification code sent too frequently, retry in 60 seconds", "error.40110"),
    CAPTCHA_FAILED(40111, "CAPTCHA verification failed", "error.40111"),
    TOTP_INCORRECT(40112, "Incorrect TOTP code", "error.40112"),
    TOTP_NOT_ENABLED(40113, "TOTP is not enabled, please set up two-factor authentication first", "error.40113"),
    SMS_CODE_INCORRECT(40114, "Incorrect SMS verification code", "error.40114"),
    OAUTH_FAILED(40115, "Third-party login authorization failed", "error.40115"),
    OAUTH_THIRD_PARTY_ERROR(41501, "Third-party service is temporarily unavailable", "error.41501"),
    OAUTH_CODE_INVALID(41502, "Authorization code is invalid", "error.41502"),
    WEBAUTHN_FAILED(40116, "WebAuthn authentication failed", "error.40116"),
    RESET_LINK_INVALID(40117, "Password reset link is invalid or expired", "error.40117"),
    PASSWORD_SAME_AS_USERNAME(40118, "Password cannot be the same as username or email", "error.40118"),

    PRIVACY_CONSENT_REQUIRED(40119, "Please agree to the Privacy Policy", "error.40119"),

    // ---- Authorization Errors (42xxx) ----
    ACCESS_DENIED(40301, "Access denied", "error.40301"),
    EMAIL_NOT_BOUND(40302, "Please bind an email address first", "error.40302"),
    EMAIL_NOT_VERIFIED(40303, "Please verify your email address first", "error.40303"),
    ACTION_EXPIRED(40304, "Action verification expired, please re-verify", "error.40304"),
    INSUFFICIENT_ADMIN(40305, "Insufficient admin permissions", "error.40305"),
    FEATURE_NOT_IN_PLAN(40341, "This feature is not available in your current plan, please upgrade", "error.40341"),

    // ---- User Errors (43xxx) ----
    USERNAME_TAKEN(40901, "Username already taken", "error.40901"),
    EMAIL_TAKEN(40902, "Email already registered", "error.40902"),
    PHONE_TAKEN(40903, "Phone number already registered", "error.40903"),
    AUTH_ALREADY_BOUND(40904, "Authentication method already bound", "error.40904"),

    // ---- Subscription Errors (44xxx) ----
    ORDER_NOT_FOUND(40441, "Order not found", "error.40441"),
    ORDER_ALREADY_PAID(40941, "Order already paid", "error.40941"),
    DUPLICATE_PURCHASE(40942, "Duplicate purchase in the same period", "error.40942"),
    PAYMENT_SIGN_FAILED(42241, "Payment signature verification failed", "error.42241"),
    RECEIPT_VALIDATION_FAILED(42242, "Receipt validation failed", "error.42242"),
    FILE_VERIFY_FAILED(42221, "File verification failed", "error.42221"),
    PAYMENT_UNAVAILABLE(50341, "Payment service temporarily unavailable", "error.50341"),

    // ---- Rate Limiting (47xxx) ----
    DAILY_QUOTA_EXHAUSTED(42901, "Daily quota exhausted, please try again tomorrow", "error.42901"),
    TOO_MANY_REQUESTS(42902, "Too many requests, please retry later", "error.42902"),
    IP_RATE_LIMITED(42903, "Too many requests from this IP", "error.42903"),
    ACCOUNT_RATE_LIMITED(42904, "Too many requests from this account", "error.42904"),

    // ---- Server Errors (50xxx) ----
    INTERNAL_ERROR(50001, "Internal server error, please try again later", "error.50001"),
    SERVICE_UNAVAILABLE(50301, "Service temporarily unavailable", "error.50301"),
    DB_ERROR(50302, "Database connection error", "error.50302"),
    CACHE_ERROR(50303, "Cache service connection error", "error.50303"),
    CONFIG_CENTER_UNREACHABLE(50304, "Configuration center unreachable", "error.50304"),
    THIRD_PARTY_TIMEOUT(50401, "Third-party service timeout, please try again later", "error.50401");

    private final int code;
    private final String message;
    private final String i18nKey;

    BizErrorCode(final int code, final String message, final String i18nKey) {
        this.code = code;
        this.message = message;
        this.i18nKey = i18nKey;
    }

    @Override
    public String getI18nKey() {
        return i18nKey;
    }
}
