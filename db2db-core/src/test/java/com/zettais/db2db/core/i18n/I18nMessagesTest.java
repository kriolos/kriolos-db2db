package com.zettais.db2db.core.i18n;

import com.zettais.db2db.core.workflow.MigrationStepId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("I18n Messages & ResourceBundle Resolution Tests")
class I18nMessagesTest {

    @Test
    @DisplayName("Should resolve English messages by default and explicitly")
    void testEnglishMessageResolution() {
        var defaultTitle = I18nMessages.get("assess.report.title");
        var explicitTitle = I18nMessages.get("assess.report.title", Language.EN);

        assertThat(defaultTitle).isEqualTo("DB2DB Migration Assessment");
        assertThat(explicitTitle).isEqualTo("DB2DB Migration Assessment");
    }

    @Test
    @DisplayName("Should resolve Portuguese messages")
    void testPortugueseMessageResolution() {
        var ptTitle = I18nMessages.get("assess.report.title", Language.PT);
        var ptPrint = I18nMessages.get("assess.report.print", Language.PT);

        assertThat(ptTitle).isEqualTo("Avaliação de Migração DB2DB");
        assertThat(ptPrint).isEqualTo("Imprimir / Guardar em PDF");
    }

    @Test
    @DisplayName("Should resolve all 12 MigrationStepId names and descriptions in EN and PT")
    void testAllMigrationStepsResolution() {
        for (var stepId : MigrationStepId.values()) {
            var nameEn = stepId.getDisplayName(Language.EN);
            var descEn = stepId.getDescription(Language.EN);
            var namePt = stepId.getDisplayName(Language.PT);
            var descPt = stepId.getDescription(Language.PT);

            assertThat(nameEn).isNotBlank().doesNotContain("!step.");
            assertThat(descEn).isNotBlank().doesNotContain("!step.");
            assertThat(namePt).isNotBlank().doesNotContain("!step.");
            assertThat(descPt).isNotBlank().doesNotContain("!step.");
        }

        assertThat(MigrationStepId.PRE_FLIGHT_CHECK.getDisplayName(Language.PT)).isEqualTo("Verificação Prévia");
        assertThat(MigrationStepId.PRE_FLIGHT_CHECK.getDisplayName(Language.EN)).isEqualTo("Pre-Flight Check");
        assertThat(MigrationStepId.PARALLEL_DATA_STREAMING.getDisplayName(Language.PT)).isEqualTo("Transmissão Paralela de Dados");
    }

    @Test
    @DisplayName("Should parse language codes correctly with safe fallback")
    void testLanguageParsing() {
        assertThat(Language.fromString("pt")).isEqualTo(Language.PT);
        assertThat(Language.fromString("PT")).isEqualTo(Language.PT);
        assertThat(Language.fromString("pt-BR")).isEqualTo(Language.PT);
        assertThat(Language.fromString("en")).isEqualTo(Language.EN);
        assertThat(Language.fromString("EN")).isEqualTo(Language.EN);
        assertThat(Language.fromString(null)).isEqualTo(Language.EN);
        assertThat(Language.fromString("unknown")).isEqualTo(Language.EN);
    }
}
