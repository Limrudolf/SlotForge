package com.slotforge.api.common.error;

public record ApiFieldError(
        String field,
        String message
) {
}