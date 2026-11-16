package com.company.vapt.user.dto.response;
import java.time.Instant;
public record AdminCreatedUserDto(AuthUserDto user, String activationPath, Instant activationExpiresAt) { }
