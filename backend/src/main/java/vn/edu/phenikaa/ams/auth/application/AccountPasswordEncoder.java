package vn.edu.phenikaa.ams.auth.application;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AccountPasswordEncoder implements PasswordEncoder {
    private final BCryptPasswordEncoder delegate = new BCryptPasswordEncoder(12);

    public static boolean hasValidLength(CharSequence password) {
        return password != null && password.length() >= 12 && password.length() <= 64
                && password.toString().getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    @Override
    public String encode(CharSequence password) {
        if (!hasValidLength(password)) throw new IllegalArgumentException("Invalid password length");
        return delegate.encode(password);
    }

    @Override
    public boolean matches(CharSequence password, String hash) {
        return hasValidLength(password) && delegate.matches(password, hash);
    }
}
