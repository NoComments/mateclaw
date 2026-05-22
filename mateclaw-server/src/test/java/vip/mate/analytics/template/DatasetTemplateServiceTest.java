package vip.mate.analytics.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.dataset.DatasetUploadLogRepository;
import vip.mate.analytics.template.DatasetTemplateService.UpdateFieldMetaRequest;

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

    @Mock
    DatasetUploadLogRepository uploadLogRepo;

    @InjectMocks
    DatasetTemplateService service;

    // ------------------------------------------------------------------ create

    /** create() must derive physicalTable from code before insert. */
    @Test
    void create_assignsPhysicalTableFromCode() {
        DatasetTemplate t = template("Livestock-Poultry");

        DatasetTemplateField f = validField("STRING");
        service.create(t, List.of(f));

        assertThat(t.getPhysicalTable()).isEqualTo("dataset_livestock_poultry");
    }

    /** create() must call fieldRepo.insert once per field. */
    @Test
    void create_insertsAllFields() {
        DatasetTemplate t = template("household_survey_2025");

        DatasetTemplateField f1 = validField("STRING");
        DatasetTemplateField f2 = validField("INT");
        service.create(t, List.of(f1, f2));

        verify(fieldRepo, times(2)).insert((DatasetTemplateField) any());
    }

    /** create() must throw IllegalArgumentException for an unrecognised FieldType. */
    @Test
    void create_throwsOnInvalidFieldType() {
        DatasetTemplate t = template("bad_type_test");

        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldCode("col1");
        f.setFieldName("col1");
        f.setFieldType("NOTYPE");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(t, List.of(f)));

        verify(templateRepo, never()).insert((DatasetTemplate) any());
    }

    /** create() with blank code must auto-derive a workspace-unique code from name. */
    @Test
    void create_autoDerivesTemplateCodeFromName() {
        DatasetTemplate t = new DatasetTemplate();
        t.setWorkspaceId(7L);
        t.setName("Livestock-Poultry");
        // code intentionally null

        service.create(t, List.of());

        assertThat(t.getCode()).isEqualTo("livestock_poultry");
        assertThat(t.getPhysicalTable()).isEqualTo("dataset_livestock_poultry");
    }

    /** create() with a pure-CJK name must fall back to the "tpl" slug. */
    @Test
    void create_autoDerivesTemplateCodeFallsBackForCjkName() {
        DatasetTemplate t = new DatasetTemplate();
        t.setWorkspaceId(7L);
        t.setName("畜禽养殖");

        service.create(t, List.of());

        assertThat(t.getCode()).isEqualTo("tpl");
        assertThat(t.getPhysicalTable()).isEqualTo("dataset_tpl");
    }

    /** create() with blank fieldCode/excelHeader must auto-derive both from fieldName. */
    @Test
    void create_autoDerivesFieldCodeAndExcelHeader() {
        DatasetTemplate t = template("survey");

        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldName("Daily Egg Yield");
        f.setFieldType("DECIMAL");
        // fieldCode and excelHeader intentionally null

        service.create(t, List.of(f));

        assertThat(f.getFieldCode()).isEqualTo("daily_egg_yield");
        assertThat(f.getExcelHeader()).isEqualTo("Daily Egg Yield");
    }

    /** create() with two blank-fieldCode fields sharing a slug must disambiguate with _2 suffix. */
    @Test
    void create_autoDerivesFieldCode_handlesCollisionWithinBatch() {
        DatasetTemplate t = template("survey");

        DatasetTemplateField a = new DatasetTemplateField();
        a.setFieldName("Count");
        a.setFieldType("INT");

        DatasetTemplateField b = new DatasetTemplateField();
        b.setFieldName("Count");
        b.setFieldType("INT");

        service.create(t, List.of(a, b));

        assertThat(a.getFieldCode()).isEqualTo("count");
        assertThat(b.getFieldCode()).isEqualTo("count_2");
    }

    /** create() must reject blank template name (defense-in-depth — code derivation needs it). */
    @Test
    void create_rejectsBlankTemplateName() {
        DatasetTemplate t = new DatasetTemplate();
        t.setWorkspaceId(1L);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(t, List.of()))
                .withMessageContaining("模板名称");
        verify(templateRepo, never()).insert((DatasetTemplate) any());
    }

    /** create() must reject any field with a blank fieldName. */
    @Test
    void create_rejectsBlankFieldName() {
        DatasetTemplate t = template("survey");

        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldType("STRING");
        // fieldName intentionally null

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.create(t, List.of(f)))
                .withMessageContaining("字段名");
        verify(templateRepo, never()).insert((DatasetTemplate) any());
    }

    // --------------------------------------------------------------- removeField

    /** removeField must throw IllegalStateException when the template has successful uploads. */
    @Test
    void removeField_throwsWhenTemplateHasSuccessfulUploads() {
        when(uploadLogRepo.countSuccessfulByTemplateId(42L)).thenReturn(5L);

        assertThatIllegalStateException()
                .isThrownBy(() -> service.removeField(42L, 99L))
                .withMessageContaining("禁止删除字段");

        verify(fieldRepo, never()).deleteById((Long) any());
    }

    /** removeField must succeed when there are no successful uploads yet. */
    @Test
    void removeField_allowedWhenNoSuccessfulUploads() {
        when(uploadLogRepo.countSuccessfulByTemplateId(42L)).thenReturn(0L);

        service.removeField(42L, 99L);

        verify(fieldRepo).deleteById(99L);
    }

    // --------------------------------------------------------------- updateFieldMeta

    /** updateFieldMeta must patch only the supplied non-null meta fields. */
    @Test
    void updateFieldMeta_patchesOnlyNonNullFields() {
        DatasetTemplateField existing = new DatasetTemplateField();
        existing.setId(10L);
        existing.setTemplateId(5L);
        existing.setFieldName("旧名称");
        existing.setFieldUnit("只");
        when(fieldRepo.selectById(10L)).thenReturn(existing);

        var patch = new UpdateFieldMetaRequest("新名称", null, "口径说明", null, null, null, null, null, null, null);
        service.updateFieldMeta(5L, 10L, patch);

        assertThat(existing.getFieldName()).isEqualTo("新名称");
        assertThat(existing.getFieldUnit()).isEqualTo("只");        // unchanged
        assertThat(existing.getSemantic()).isEqualTo("口径说明");
        verify(fieldRepo).updateById(any(DatasetTemplateField.class));
    }

    /** updateFieldMeta must throw when field belongs to a different template. */
    @Test
    void updateFieldMeta_throwsWhenTemplateIdMismatch() {
        DatasetTemplateField existing = new DatasetTemplateField();
        existing.setId(10L);
        existing.setTemplateId(99L);   // different template
        when(fieldRepo.selectById(10L)).thenReturn(existing);

        var patch = new UpdateFieldMetaRequest("名称", null, null, null, null, null, null, null, null, null);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.updateFieldMeta(5L, 10L, patch));
        verify(fieldRepo, never()).updateById(any(DatasetTemplateField.class));
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

    /** appendField with blank fieldCode/excelHeader must auto-derive both, querying the repo for uniqueness. */
    @Test
    void appendField_autoDerivesFieldCodeAndExcelHeader() {
        when(fieldRepo.selectCount(any())).thenReturn(0L);

        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldName("Feed Amount");
        f.setFieldType("DECIMAL");

        service.appendField(10L, f);

        assertThat(f.getFieldCode()).isEqualTo("feed_amount");
        assertThat(f.getExcelHeader()).isEqualTo("Feed Amount");
        verify(fieldRepo).insert(f);
    }

    /** appendField must reject blank fieldName before touching the repo. */
    @Test
    void appendField_rejectsBlankFieldName() {
        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldType("STRING");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.appendField(10L, f))
                .withMessageContaining("字段名");
        verify(fieldRepo, never()).insert((DatasetTemplateField) any());
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
        f.setFieldName("col_" + type.toLowerCase());
        f.setFieldType(type);
        return f;
    }

    private static DatasetTemplate template(String code) {
        DatasetTemplate t = new DatasetTemplate();
        t.setWorkspaceId(1L);
        t.setName(code);
        t.setCode(code);
        return t;
    }
}
