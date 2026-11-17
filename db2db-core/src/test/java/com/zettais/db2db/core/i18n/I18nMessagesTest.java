package com.zettais.db2db.core.i18n;

import com.zettais.db2db.core.workflow.MigrationStepId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class I18nMessagesTest {

    @Test
    void testMigrationStepIdLocalizedViaProperties() {
        // English (default)
        assertEquals("Pre-Flight Check", MigrationStepId.PRE_FLIGHT_CHECK.getDisplayName(Language.EN));
        assertEquals("Schema Assessment", MigrationStepId.SCHEMA_ASSESSMENT.getDisplayName(Language.EN));
        assertTrue(MigrationStepId.PRE_FLIGHT_CHECK.getDescription(Language.EN).contains("connectivity"));

        // Portuguese
        assertEquals("Verificação Prévia", MigrationStepId.PRE_FLIGHT_CHECK.getDisplayName(Language.PT));
        assertEquals("Avaliação do Esquema", MigrationStepId.SCHEMA_ASSESSMENT.getDisplayName(Language.PT));
        assertTrue(MigrationStepId.PRE_FLIGHT_CHECK.getDescription(Language.PT).contains("conectividade"));

        // Default getDisplayName() / getDescription() should be English
        assertEquals("Pre-Flight Check", MigrationStepId.PRE_FLIGHT_CHECK.getDisplayName());
        assertEquals(MigrationStepId.PRE_FLIGHT_CHECK.getDescription(Language.EN), MigrationStepId.PRE_FLIGHT_CHECK.getDescription());
    }

    @Test
    void testAllStepsHaveTranslationsInBothLanguages() {
        for (MigrationStepId step : MigrationStepId.values()) {
            String nameEn = step.getDisplayName(Language.EN);
            String descEn = step.getDescription(Language.EN);
            String namePt = step.getDisplayName(Language.PT);
            String descPt = step.getDescription(Language.PT);

            assertNotNull(nameEn, "Missing EN name for " + step);
            assertFalse(nameEn.startsWith("step."), "EN name not found in bundle: " + nameEn);

            assertNotNull(descEn, "Missing EN description for " + step);
            assertFalse(descEn.startsWith("step."), "EN description not found in bundle: " + descEn);

            assertNotNull(namePt, "Missing PT name for " + step);
            assertFalse(namePt.startsWith("step."), "PT name not found in bundle: " + namePt);

            assertNotNull(descPt, "Missing PT description for " + step);
            assertFalse(descPt.startsWith("step."), "PT description not found in bundle: " + descPt);
        }
    }
}
