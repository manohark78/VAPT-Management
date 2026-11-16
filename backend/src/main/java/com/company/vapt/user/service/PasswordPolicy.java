package com.company.vapt.user.service;

import com.company.vapt.common.exception.ValidationException;
import java.util.regex.Pattern;

public final class PasswordPolicy {
    private static final Pattern STRONG = Pattern.compile("^(?=.{12,128}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$");
    private PasswordPolicy() { }
    public static void validate(String password) {
        if (password == null || !STRONG.matcher(password).matches()) {
            throw new ValidationException("Password must be 12 to 128 characters and include upper and lower case letters, a number, and a symbol");
        }
    }
}
