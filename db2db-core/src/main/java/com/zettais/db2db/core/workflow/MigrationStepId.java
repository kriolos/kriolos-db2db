package com.zettais.db2db.core.workflow;

import com.zettais.db2db.core.i18n.I18nMessages;
import com.zettais.db2db.core.i18n.Language;

/**
 * The 12 industrial migration lifecycle steps supported by DB2DB by ZettaIS.
 * Titles and descriptions are dynamically resolved from standard i18n ResourceBundle (.properties) files.
 */
public enum MigrationStepId {
    PRE_FLIGHT_CHECK,
    SCHEMA_ASSESSMENT,
    PRE_SCHEMA_SETUP,
    DDL_EXPORT,
    DDL_APPLY,
    PARALLEL_DATA_STREAMING,
    POST_DATA_INDEXES,
    FOREIGN_KEYS,
    PROCEDURAL_CODE,
    SEQUENCE_SYNC,
    DATA_VALIDATION_AUDIT,
    FINAL_AUDIT_REPORT;

    /**
     * Resolves localized step display name from ResourceBundle (.properties).
     */
    public String getDisplayName(Language lang) {
        return I18nMessages.get("step." + name() + ".name", lang);
    }

    /**
     * Resolves localized step detailed description from ResourceBundle (.properties).
     */
    public String getDescription(Language lang) {
        return I18nMessages.get("step." + name() + ".description", lang);
    }

    /**
     * Resolves default display name (English) from ResourceBundle.
     */
    public String getDisplayName() {
        return getDisplayName(Language.EN);
    }

    /**
     * Resolves default description (English) from ResourceBundle.
     */
    public String getDescription() {
        return getDescription(Language.EN);
    }
}
