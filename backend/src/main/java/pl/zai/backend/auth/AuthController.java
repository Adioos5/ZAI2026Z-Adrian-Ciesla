package pl.zai.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.zai.backend.auth.AuthService.AuthenticatedAdmin;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotNull String username, @NotNull String password) {
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn) {
    }

    public record PasswordChange(@NotNull String currentPassword, @NotNull @Size(min = 8) String newPassword) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.IssuedToken token = authService.login(request.username(), request.password());
        return new LoginResponse(token.accessToken(), "Bearer", token.expiresInSeconds());
    }

    @RequiresAdmin
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestAttribute(AdminAuthInterceptor.ADMIN_ATTRIBUTE) AuthenticatedAdmin admin) {
        authService.logout(admin);
    }

    @RequiresAdmin
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestAttribute(AdminAuthInterceptor.ADMIN_ATTRIBUTE) AuthenticatedAdmin admin,
                               @Valid @RequestBody PasswordChange request) {
        authService.changePassword(admin, request.currentPassword(), request.newPassword());
    }
}
