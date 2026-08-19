package com.slotforge.api.user;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(
            UserAccountRepository userAccountRepository
    ) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(UUID userId) {
        UserAccount user = userAccountRepository
                .findWithRolesById(userId)
                .orElseThrow(
                        AuthenticatedAccountUnavailableException::new
                );

        if (!user.isActive()) {
            throw new AuthenticatedAccountUnavailableException();
        }

        return CurrentUserResponse.from(user);
    }
}
