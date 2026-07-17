package vip.mate.analytics.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetField;
import vip.mate.analytics.dataset.DatasetFieldRepository;
import vip.mate.analytics.dataset.DatasetRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnalyticsSchemaTool} — pure Mockito, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsSchemaToolTest {

    @Mock
    DatasetRepository datasetRepo;

    @Mock
    DatasetFieldRepository fieldRepo;

    @InjectMocks
    AnalyticsSchemaTool tool;

    @Test
    void listAll_returnsFormattedDatasetList() {
        Dataset d1 = new Dataset();
        d1.setId(1L);
        d1.setName("家禽季报");
        d1.setRowCount(100);
        d1.setPhysicalTable("dataset_1");
        when(datasetRepo.selectList(any(QueryWrapper.class))).thenReturn(List.of(d1));

        String result = tool.analyticsSchema(null);

        assertThat(result).contains("id=1").contains("家禽季报").contains("rows=100")
                .contains("physicalTable=dataset_1");
    }

    @Test
    void listAll_returnsHelpMessageWhenEmpty() {
        when(datasetRepo.selectList(any(QueryWrapper.class))).thenReturn(List.of());

        String result = tool.analyticsSchema(null);

        assertThat(result).contains("没有可用数据集");
    }

    @Test
    void detail_returnsFieldList() {
        Dataset d = new Dataset();
        d.setId(2L);
        d.setName("测试");
        d.setRowCount(50);
        d.setPhysicalTable("dataset_2");

        DatasetField f = new DatasetField();
        f.setDatasetId(2L);
        f.setFieldCode("end_stock");
        f.setFieldName("期末存栏");
        f.setFieldType("DECIMAL");
        f.setFieldUnit("只");

        when(datasetRepo.selectById(2L)).thenReturn(d);
        when(fieldRepo.selectList(any(QueryWrapper.class))).thenReturn(List.of(f));

        String result = tool.analyticsSchema(2L);

        assertThat(result).contains("物理表=dataset_2").contains("end_stock").contains("DECIMAL")
                .contains("单位:只").contains("期末存栏");
    }

    @Test
    void detail_returnsNotFoundWhenDatasetMissing() {
        when(datasetRepo.selectById(99L)).thenReturn(null);

        String result = tool.analyticsSchema(99L);

        assertThat(result).contains("不存在");
    }
}
