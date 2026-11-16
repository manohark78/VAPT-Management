package com.company.vapt.enrichment.service;

import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;

@Component
public class TargetClassifier {
    public String classify(String original) {
        if (original == null || original.isBlank()) return "UNKNOWN";
        String value = original.trim();
        if (isIpv4(value) || isIpv6Literal(value)) return "IP";
        return value.contains(".") ? "FQDN" : "HOSTNAME";
    }

    private boolean isIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) return false;
            try { if (Integer.parseInt(part) > 255) return false; }
            catch (NumberFormatException e) { return false; }
        }
        return true;
    }

    private boolean isIpv6Literal(String value) {
        if (!value.contains(":")) return false;
        try { return InetAddress.getByName(value) instanceof Inet6Address; }
        catch (Exception ignored) { return false; }
    }
}
