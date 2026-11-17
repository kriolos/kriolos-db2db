package com.zettais.db2db.core.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Thread-safe industrial i18n resolver backed by standard ResourceBundle (.properties).
 */
public final class I18nMessages {

    private static final String BUNDLE_BASE_NAME = "i18n.messages";

    private I18nMessages() {
        // utility class
    }

    /**
     * Resolves a message for the specified Language and key, with optional positional arguments.
     *
     * @param key  the message key in the .properties file
     * @param lang the target Language (EN, PT)
     * @param args optional MessageFormat parameters
     * @return the localized string, or the key itself if not found
     */
    public static String get(String key, Language lang, Object... args) {
        if (key == null) {
            return "";
        }
        Locale locale = (lang != null) ? lang.getLocale() : Language.EN.getLocale();
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, locale, new ResourceBundle.Control() {
                @Override
                public java.util.List<String> getFormats(String baseName) {
                    return FORMAT_PROPERTIES;
                }
            });
            String pattern = bundle.getString(key);
            if (args != null && args.length > 0) {
                return MessageFormat.format(pattern, args);
            }
            return pattern;
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /**
     * Resolves a message in the default system language (English).
     */
    public static String get(String key, Object... args) {
        return get(key, Language.EN, args);
    }
}
