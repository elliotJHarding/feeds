package com.harding.feeds.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Short human-enterable invite codes: 8 characters from an alphabet with the
 * ambiguous characters (I, L, O, 0, 1) removed, so a code read off one phone
 * screen can be typed into another without transcription errors.
 */
@Component
public class InviteCodeGenerator {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
