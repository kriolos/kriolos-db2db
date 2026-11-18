package com.zettais.db2db.core.i18n;

import java.util.Locale;

/**
 * Supported platform languages for DB2DB by ZettaIS.
 */
public enum Language {
    EN("English", Locale.ENGLISH),
    PT("Português", Locale.forLanguageTag("pt-PT"));

    private final String label;
    private final Locale locale;

    Language(String label, Locale locale) {
        this.label = label;
        this.locale = locale;
    }

    public String getLabel() {
        return label;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getCode() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Language fromString(String code) {
        if (code == null) return EN;
        String trimmed = code.trim().toLowerCase();
        if (trimmed.startsWith("pt")) {
            return PT;
        }
        return EN;
    }
}
