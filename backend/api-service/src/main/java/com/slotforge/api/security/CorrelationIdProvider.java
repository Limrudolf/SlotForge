package com.slotforge.api.security;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component
public class CorrelationIdProvider {

    public UUID currentCorrelationId() {
        RequestAttributes attributes = RequestContextHolder
                .currentRequestAttributes();
        Object value = attributes.getAttribute(
                CorrelationIdFilter.REQUEST_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST
        );
        if (value instanceof UUID correlationId) {
            return correlationId;
        }
        throw new IllegalStateException("Correlation ID is unavailable");
    }
}
