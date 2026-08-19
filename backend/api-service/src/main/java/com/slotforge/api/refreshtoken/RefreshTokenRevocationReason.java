package com.slotforge.api.refreshtoken;

public enum RefreshTokenRevocationReason {
    ROTATED,
    LOGOUT,
    REUSE_DETECTED,
    ACCOUNT_DISABLED,
    ADMIN_REVOKED
}
