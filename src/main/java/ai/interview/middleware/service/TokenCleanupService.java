package ai.interview.middleware.service;

import ai.interview.middleware.repository.RevokedTokenRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prunes revoked tokens that have expired on their own.
 *
 * <p>Once a token is past its {@code exp} the signature check rejects it anyway, so the row no longer
 * affects behaviour and only costs space in a table on the hot path of every request.
 *
 * <p>Every replica runs this. That is intentional and safe: the delete is idempotent and bounded by
 * {@code idx_revoked_tokens_expires_at}, so a distributed lock would add a dependency to avoid work
 * that is already cheap.
 */
@Service
public class TokenCleanupService {

    private static final Logger log = LoggerFactory.getLogger(TokenCleanupService.class);

    private final RevokedTokenRepository revokedTokenRepository;

    public TokenCleanupService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Scheduled(cron = "${app.jobs.token-cleanup-cron:0 15 * * * *}")
    @Transactional
    public void purgeExpiredTokens() {
        int removed = revokedTokenRepository.deleteExpired(Instant.now());
        if (removed > 0) {
            log.info("Purged {} expired revoked token(s)", removed);
        }
    }
}
