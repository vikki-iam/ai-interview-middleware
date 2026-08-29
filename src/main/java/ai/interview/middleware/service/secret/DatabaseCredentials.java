package ai.interview.middleware.service.secret;

/**
 * Everything needed to open a database connection.
 *
 * <p>All five fields come from the same source, so switching from environment variables to Secrets
 * Manager cannot leave the host pointing at one database while the password belongs to another.
 */
public record DatabaseCredentials(
        String host, int port, String databaseName, String username, String password) {

    /**
     * Builds the JDBC URL. {@code ApplicationName} is included so {@code pg_stat_activity} attributes
     * a runaway query to this service, which is the first thing you want during an incident.
     */
    public String jdbcUrl(String sslMode, String applicationName) {
        return "jdbc:postgresql://%s:%d/%s?sslmode=%s&ApplicationName=%s"
                .formatted(host, port, databaseName, sslMode, applicationName);
    }

    /** Safe for logs: identifies the target without revealing the password. */
    public String describe() {
        return "%s@%s:%d/%s".formatted(username, host, port, databaseName);
    }

    @Override
    public String toString() {
        // Guards against a stray log statement or exception message leaking the password.
        return "DatabaseCredentials[" + describe() + ", password=***]";
    }
}
