package com.harding.feeds.service.auth;

import com.harding.feeds.repository.AppJwtTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Deletes long-expired refresh token rows daily at 02:00.
 */
@Service
public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    private final AppJwtTokenRepository tokenRepository;

    public TokenCleanupService(AppJwtTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusDays(1);
        int deletedCount = tokenRepository.deleteExpiredTokens(cutoff);
        log.info("Expired token cleanup completed. Deleted {} tokens", deletedCount);
    }
}
