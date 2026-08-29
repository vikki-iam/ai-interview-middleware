package ai.interview.middleware.config;

import ai.interview.middleware.service.secret.DatabaseCredentials;
import ai.interview.middleware.service.secret.SecretService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Builds the DataSource from {@link SecretService} instead of {@code spring.datasource.*}.
 *
 * <p>This is the hinge that lets the same image run everywhere. There is no JDBC URL and no password
 * in any configuration file, ConfigMap or Helm value; the credentials are resolved once at startup
 * from whichever provider is active. Defining this bean makes Spring Boot's
 * {@code DataSourceAutoConfiguration} back off, and Flyway and JPA both pick it up automatically.
 *
 * <p>If resolution fails the context never refreshes, so a bad secret reference is a pod that fails
 * its startup probe rather than a service that accepts traffic and then 500s.
 */
@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

    @Bean
    @Primary
    @ConditionalOnMissingBean(DataSource.class)
    public DataSource dataSource(
            SecretService secretService,
            AppProperties properties,
            @Value("${spring.application.name}") String applicationName) {

        DatabaseCredentials credentials = secretService.databaseCredentials();
        AppProperties.Datasource pool = properties.datasource();

        HikariConfig config = new HikariConfig();
        config.setPoolName("aip-pool");
        config.setJdbcUrl(credentials.jdbcUrl(pool.sslMode(), applicationName));
        config.setUsername(credentials.username());
        config.setPassword(credentials.password());
        config.setDriverClassName("org.postgresql.Driver");

        config.setMaximumPoolSize(pool.maximumPoolSize());
        config.setMinimumIdle(pool.minimumIdle());
        config.setConnectionTimeout(pool.connectionTimeout().toMillis());
        config.setIdleTimeout(pool.idleTimeout().toMillis());
        // Kept below any RDS/proxy idle timeout so the pool recycles a connection before the server
        // closes it underneath an in-flight query.
        config.setMaxLifetime(pool.maxLifetime().toMillis());
        config.setValidationTimeout(pool.validationTimeout().toMillis());
        config.setConnectionTestQuery("SELECT 1");
        config.setAutoCommit(false);
        // Surfaces a pool leak as a log entry naming the borrowing stack instead of a silent stall.
        config.setLeakDetectionThreshold(pool.maxLifetime().toMillis() / 2);
        config.setRegisterMbeans(false);

        log.info(
                "Configuring DataSource via secret provider '{}': target={} poolMax={} sslMode={}",
                secretService.providerId(),
                credentials.describe(),
                pool.maximumPoolSize(),
                pool.sslMode());

        return new HikariDataSource(config);
    }
}
