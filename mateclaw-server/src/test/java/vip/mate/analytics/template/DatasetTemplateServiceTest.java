package vip.mate.analytics.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.analytics.dataset.DatasetRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DatasetTemplateService} — pure Mockito, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class DatasetTemplateServiceTest {

    @Mock
    DatasetTemplateRepository templateRepo;

    @Mock
    DatasetTemplateFieldRepository fieldRepo;

    @Mock
    DatasetRepository datasetRepo;

    @InjectMocks
    DatasetTemplateService service;

    // ------------------------------------------------------------------ create

    /** create() must derive physicalTable from code before insert. */
    @Test
    void create_assignsPhysicalTableFromCode() {
        DatasetTemplate t = new DatasetTemplate();
        t.setCode("Livestock-Poultry");

        DatasetTemplateField f = validField("STRING");
        service.create(t, List.of(f));

        assertThat(t.getPhysicalTable()).isEqualTo("dataset_livestock_poultry");
    }

    /** create() must call fieldRepo.insert once per field. */
    @Test
    void create_insertsAllFields() {
        DatasetTemplate t = new DatasetTemplate();
        t.setCode("household_survey_2025");

        DatasetTemplateField f1 = validField("STRING");
        DatasetTemplateField f2 = validField("INT");
        service.create(t, List.of(f1, f2));

        verify(fieldRepo, times(2)).insert((DatasetTemplateField) any());
    }

    /** create() must throw IllegalArgumentException for an unrecognised FieldType. */
    @Test
    void create_throwsOnInvalidFieldType() {
        DatasetTemplate t = new DatasetTemplate();
        t.setCode("bad_type_test");

        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldCode("col1");
        f.setFieldType("NOTYPE");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(t, List.of(f)));

        verify(templateRepo, never()).insert((DatasetTemplate) any());
    }

    // --------------------------------------------------------------- removeField

    /** removeField must throw IllegalStateException when any Dataset already uses the template. */
    @Test
    void removeField_throwsWhenDatasetExists() {
        when(datasetRepo.countByTemplateId(42L)).thenReturn(3L);

        assertThatIllegalStateException()
                .isThrownBy(() -> service.removeField(42L, 99L))
                .withMessageContaining("禁止删除字段");

        verify(fieldRepo, never()).deleteById((Long) any());
    }

    // --------------------------------------------------------------- appendField

    /** appendField must insert the field regardless of how many datasets exist. */
    @Test
    void appendField_alwaysAllowed() {
        DatasetTemplateField f = validField("DECIMAL");

        service.appendField(10L, f);

        assertThat(f.getTemplateId()).isEqualTo(10L);
        verify(fieldRepo).insert(f);
        // datasetRepo must NOT be consulted for append
        verifyNoInteractions(datasetRepo);
    }

    // ------------------------------------------------------------- physicalTable

    /** toPhysicalTable must normalise uppercase, hyphens, and spaces correctly. */
    @Test
    void toPhysicalTable_normalizesSpecialChars() {
        assertThat(DatasetTemplateService.toPhysicalTable("Livestock-Poultry"))
                .isEqualTo("dataset_livestock_poultry");

        assertThat(DatasetTemplateService.toPhysicalTable("household_survey_2025"))
                .isEqualTo("dataset_household_survey_2025");

        assertThat(DatasetTemplateService.toPhysicalTable("MY  REPORT  2024"))
                .isEqualTo("dataset_my_report_2024");

        assertThat(DatasetTemplateService.toPhysicalTable("A--B__C"))
                .isEqualTo("dataset_a_b_c");
    }

    // ------------------------------------------------------------------ helpers

    private static DatasetTemplateField validField(String type) {
        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldCode("col_" + type.toLowerCase());
        f.setFieldType(type);
        return f;
    }
}
