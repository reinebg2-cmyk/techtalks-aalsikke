package com.aalsikke.util;

/**
 * Single source of truth for phone formatting (KAN-257).
 * Everything is stored and looked up in E.164 form, e.g. +9613000000.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {}

    /**
     * Normalizes a phone number to E.164.
     * Lebanese local formats ("03 000 000", "71234567", "009613000000", "9613000000",
     * "+961 03 000 000") all become +961 followed by the national number.
     * International numbers starting with + or 00 are kept (digits only).
     *
     * @return the normalized number, or null if the input is null/blank
     * @throws IllegalArgumentException if the input is not a valid phone number
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String s = raw.trim().replaceAll("[\\s\\-().]", "");

        if (s.startsWith("00")) {
            s = "+" + s.substring(2);
        }

        if (!s.startsWith("+")) {
            if (s.startsWith("961") && s.length() >= 10) {
                s = "+" + s;                     // 9613000000 -> +9613000000
            } else if (s.startsWith("0")) {
                s = "+961" + s.substring(1);     // 03000000   -> +9613000000
            } else {
                s = "+961" + s;                  // 71234567   -> +96171234567
            }
        }

        if (s.startsWith("+961")) {
            String national = s.substring(4);
            if (national.startsWith("0")) {
                national = national.substring(1); // +961 03... -> +9613...
            }
            if (!national.matches("\\d{7,8}")) {
                throw new IllegalArgumentException("Please enter a valid Lebanese phone number.");
            }
            return "+961" + national;
        }

        if (!s.matches("\\+[1-9]\\d{7,14}")) {
            throw new IllegalArgumentException("Please enter a valid phone number.");
        }
        return s;
    }
}