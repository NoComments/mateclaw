package vip.mate.analytics.dataset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the V123 schema: a dataset owns its physical table name and its fields.
 */
@SpringBootTest(
        classes = vip.mate.MateClawApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dataset_field_repo_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.main.web-application-type=none",
        "spring.ai.dashscope.api-key=test-key"
})
class DatasetFieldRepositoryTest {

    @Autowired
    DatasetRepository datasetRepo;

    @Autowired
    DatasetFieldRepository fieldRepo;

    @Test
    @DisplayName("dataset persists its own physical table name — no template indirection")
    void datasetOwnsPhysicalTable() {
        Dataset ds = new Dataset();
        ds.setWorkspaceId(7L);
        ds.setName("销售数据");
        ds.setRowCount(0);
        ds.setPhysicalTable("dataset_placeholder");
        datasetRepo.insert(ds);

        ds.setPhysicalTable("dataset_" + ds.getId());
        datasetRepo.updateById(ds);

        Dataset found = datasetRepo.selectById(ds.getId());
        assertThat(found.getPhysicalTable()).isEqualTo("dataset_" + ds.getId());
        assertThat(found.getWorkspaceId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("fields are owned by a dataset and round-trip through the repository")
    void fieldsBelongToDataset() {
        Dataset ds = new Dataset();
        ds.setWorkspaceId(7L);
        ds.setName("库存数据");
        ds.setRowCount(0);
        ds.setPhysicalTable("dataset_placeholder");
        datasetRepo.insert(ds);

        DatasetField f = new DatasetField();
        f.setDatasetId(ds.getId());
        f.setFieldCode("amount");
        f.setFieldName("金额");
        f.setFieldType("DECIMAL");
        f.setFieldUnit("元");
        f.setSemantic("订单成交金额");
        f.setIsNullable(true);
        f.setOrdinal(0);
        f.setExcelHeader("金额（元）");
        fieldRepo.insert(f);

        DatasetField found = fieldRepo.selectById(f.getId());
        assertThat(found.getDatasetId()).isEqualTo(ds.getId());
        assertThat(found.getExcelHeader()).isEqualTo("金额（元）");
        assertThat(found.getSemantic()).isEqualTo("订单成交金额");
    }
}
