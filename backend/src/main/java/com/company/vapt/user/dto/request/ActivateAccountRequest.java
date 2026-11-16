package com.company.vapt.user.dto.request;
import jakarta.validation.constraints.NotBlank;
public record ActivateAccountRequest(@NotBlank String token, @NotBlank String password) { }
