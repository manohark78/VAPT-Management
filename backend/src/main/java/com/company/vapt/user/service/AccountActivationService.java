package com.company.vapt.user.service;

import com.company.vapt.user.repository.UserActivationTokenRepository;

import com.company.vapt.audit.service.AuditService;
import com.company.vapt.common.exception.ValidationException;
import com.company.vapt.user.domain.AppUser;
import com.company.vapt.user.repository.AppUserRepository;
import com.company.vapt.user.entity.UserActivationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class AccountActivationService {
    private final UserActivationTokenRepository tokens;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    @Value("${vapt.security.activation-token-ttl-hours:24}") private long tokenTtlHours;

    public AccountActivationService(UserActivationTokenRepository tokens, AppUserRepository users,
            PasswordEncoder passwords, AuditService audit) {
        this.tokens=tokens; this.users=users; this.passwords=passwords; this.audit=audit;
    }

    @Transactional
    public IssuedToken issue(AppUser user, String adminActor) {
        if ("ACTIVE".equalsIgnoreCase(user.getStatus())) throw new ValidationException("An activation token is only available for a pending account");
        Instant now=Instant.now();
        tokens.invalidateOutstanding(user.getId(),now);
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expires=now.plus(Duration.ofHours(Math.max(1,Math.min(168,tokenTtlHours))));
        String hash=hash(raw);
        tokens.save(new UserActivationToken(UUID.randomUUID(),user,hash,now,expires));
        audit.record(adminActor,"ADMIN","USER_ACTIVATION_TOKEN_ISSUED","USER",String.valueOf(user.getId()),"Activation token issued; expiresAt="+expires);
        return new IssuedToken("/activate#token="+raw,expires);
    }

    @Transactional
    public void activate(String rawToken, String newPassword) {
        PasswordPolicy.validate(newPassword);
        if(rawToken==null || rawToken.isBlank() || rawToken.length()>256) throw new ValidationException("Activation link is invalid or expired");
        UserActivationToken token=tokens.findByTokenHashForUpdate(hash(rawToken))
                .filter(t->t.getUsedAt()==null && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(()->new ValidationException("Activation link is invalid or expired"));
        AppUser user=token.getUser();
        user.setPasswordHash(passwords.encode(newPassword));
        user.setStatus("ACTIVE");
        user.setUpdatedAt(Instant.now());
        token.consume(Instant.now());
        users.save(user); tokens.save(token);
        audit.record(String.valueOf(user.getId()),user.getRole(),"USER_ACTIVATED","USER",String.valueOf(user.getId()),"Account activated");
    }

    private String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("SHA-256 is not available",e); }
    }
    public record IssuedToken(String activationPath, Instant expiresAt) { }
}
