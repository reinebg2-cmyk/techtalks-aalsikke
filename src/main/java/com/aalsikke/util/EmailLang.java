package com.aalsikke.util;

public final class EmailLang {
    private EmailLang() {}

    /** Returns "ar" or "en". Anything missing or unknown becomes "en". */
    public static String normalize(String lang) {
        if (lang == null) return "en";
        return lang.trim().toLowerCase().startsWith("ar") ? "ar" : "en";
    }
}