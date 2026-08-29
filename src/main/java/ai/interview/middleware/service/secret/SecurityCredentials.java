package ai.interview.middleware.service.secret;

/** Non-database secret material: the JWT signing key and the AI service's internal API key. */
public record SecurityCredentials(String jwtSigningKey, String aiServiceApiKey) {

    @Override
    public String toString() {
        return "SecurityCredentials[jwtSigningKey=***, aiServiceApiKey=***]";
    }
}
