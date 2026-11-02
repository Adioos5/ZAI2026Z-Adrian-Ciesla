package pl.zai.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.zai.backend.common.NotImplemented;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String token) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        throw NotImplemented.yet();
    }

    @PostMapping("/logout")
    public void logout() {
        throw NotImplemented.yet();
    }

    @PutMapping("/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        throw NotImplemented.yet();
    }
}
