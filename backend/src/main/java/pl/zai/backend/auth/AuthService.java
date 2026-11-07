package pl.zai.backend.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.zai.backend.common.ApiErrors;
import pl.zai.backend.common.Secrets;

@Service
public class AuthService implements ApplicationRunner {

    /** What a valid bearer token resolves to; attached to the request by {@link AdminAuthInterceptor}. */
    public record AuthenticatedAdmin(Long adminId, String tokenHash) {
    }

    public record IssuedToken(String accessToken, long expiresInSeconds) {
    }

    private final AdminUserRepository adminUsers;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String initialUsername;
    private final String initialPassword;
    private final long tokenTtlSeconds;

    public AuthService(AdminUserRepository adminUsers, AuthTokenRepository tokens,
                       @Value("${app.admin.username}") String initialUsername,
                       @Value("${app.admin.password}") String initialPassword,
                       @Value("${app.auth.token-ttl-seconds}") long tokenTtlSeconds) {
        this.adminUsers = adminUsers;
        this.tokens = tokens;
        this.initialUsername = initialUsername;
        this.initialPassword = initialPassword;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /** Creates the configured administrator on first start; an existing account keeps its password. */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!adminUsers.existsByUsername(initialUsername)) {
            adminUsers.save(new AdminUser(initialUsername, passwordEncoder.encode(initialPassword)));
        }
    }

    @Transactional
    public IssuedToken login(String username, String password) {
        AdminUser admin = adminUsers.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .orElseThrow(() -> ApiErrors.unauthorized("Invalid username or password"));

        Instant now = Instant.now();
        tokens.deleteExpired(now);
        String token = Secrets.randomToken();
        tokens.save(new AuthToken(Secrets.sha256(token), admin.getId(), now.plusSeconds(tokenTtlSeconds)));
        return new IssuedToken(token, tokenTtlSeconds);
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedAdmin> authenticate(String token) {
        String hash = Secrets.sha256(token);
        return tokens.findByTokenHash(hash)
                .filter(stored -> stored.getExpiresAt().isAfter(Instant.now()))
                .map(stored -> new AuthenticatedAdmin(stored.getAdminId(), hash));
    }

    @Transactional
    public void logout(AuthenticatedAdmin admin) {
        tokens.deleteByTokenHash(admin.tokenHash());
    }

    @Transactional
    public void changePassword(AuthenticatedAdmin authenticated, String currentPassword, String newPassword) {
        AdminUser admin = adminUsers.findById(authenticated.adminId())
                .orElseThrow(() -> ApiErrors.unauthorized("Administrator no longer exists"));
        if (!passwordEncoder.matches(currentPassword, admin.getPasswordHash())) {
            throw ApiErrors.forbidden("Current password is incorrect");
        }
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
    }
}
