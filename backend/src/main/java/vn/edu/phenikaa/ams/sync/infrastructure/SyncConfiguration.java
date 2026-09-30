package vn.edu.phenikaa.ams.sync.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import vn.edu.phenikaa.ams.academic.application.CurriculumImportService;
import vn.edu.phenikaa.ams.academic.application.ProfileImportService;
import vn.edu.phenikaa.ams.academic.application.port.AcademicPortalClient;
import vn.edu.phenikaa.ams.sync.application.SyncService;
import vn.edu.phenikaa.ams.sync.application.SyncMaintenance;
import vn.edu.phenikaa.ams.sync.application.SyncWorker;
import vn.edu.phenikaa.ams.user.infrastructure.UserRepository;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SyncConfiguration {
    @Bean Clock syncClock() { return Clock.systemUTC(); }
    @Bean SyncRunStore syncRunStore(JdbcTemplate jdbc) { return new SyncRunStore(jdbc); }
    @Bean
    SyncMaintenance syncMaintenance(SyncRunStore runs, PlatformTransactionManager transactions,
                                    Clock clock, MeterRegistry metrics,
                                    @Value("${ams.sync.auto-enabled:false}") boolean autoEnabled,
                                    @Value("${ams.phenikaa.enabled:false}") boolean sourceEnabled,
                                    @Value("${ams.sync.auto-scan-interval:5m}") Duration scan,
                                    @Value("${ams.sync.auto-interval:24h}") Duration interval,
                                    @Value("${ams.sync.auto-failure-cooldown:24h}") Duration failureCooldown,
                                    @Value("${ams.sync.auto-batch-size:4}") int autoBatch,
                                    @Value("${ams.sync.retention:90d}") Duration retention,
                                    @Value("${ams.sync.cleanup-interval:1h}") Duration cleanupInterval,
                                    @Value("${ams.sync.cleanup-batch-size:100}") int cleanupBatch) {
        return new SyncMaintenance(runs, transactions, clock, metrics, autoEnabled, sourceEnabled,
                scan, interval, failureCooldown, autoBatch, retention, cleanupInterval, cleanupBatch);
    }
    @Bean
    @ConditionalOnProperty(name = "ams.phenikaa.enabled", havingValue = "true")
    RedisSyncLock redisSyncLock(StringRedisTemplate redis,
                               @Value("${ams.sync.lock-ttl:2m}") Duration ttl) {
        return new RedisSyncLock(redis, ttl);
    }
    @Bean
    @ConditionalOnProperty(name = "ams.phenikaa.enabled", havingValue = "true")
    SyncService syncService(SyncRunStore runs, UserRepository users, AcademicPortalClient portal,
                                  StringRedisTemplate redis, PlatformTransactionManager transactions, Clock clock,
                                  @Value("${ams.sync.manual-cooldown:1m}") Duration cooldown,
                                  @Value("${ams.sync.history-max-page-size:100}") int historyMaxPageSize) {
        return new SyncService(runs, users, portal, redis, cooldown, historyMaxPageSize, transactions, clock);
    }
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "ams.phenikaa.enabled", havingValue = "true")
    SyncWorker syncWorker(SyncRunStore runs, RedisSyncLock lock, ProfileImportService profiles,
                          CurriculumImportService curricula, Clock clock, MeterRegistry metrics,
                          @Value("${ams.sync.max-attempts:3}") int attempts,
                          @Value("${ams.sync.batch-size:4}") int batch,
                          @Value("${ams.sync.backoff:10s}") Duration backoff,
                          @Value("${ams.sync.stale-timeout:3m}") Duration stale,
                          @Value("${ams.sync.worker-enabled:true}") boolean enabled) {
        return new SyncWorker(runs, lock, profiles, curricula, clock, metrics,
                attempts, batch, backoff, stale, enabled);
    }
}
