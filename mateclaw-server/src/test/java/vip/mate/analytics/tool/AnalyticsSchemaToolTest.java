package vip.mate.analytics.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateField;
import vip.mate.analytics.template.DatasetTemplateFieldRepository;
import vip.mate.analytics.template.DatasetTemplateRepository;

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
    DatasetTemplateRepository templateRepo;

    @Mock
    DatasetTemplateFieldRepository fieldRepo;

    @InjectMocks
    AnalyticsSchemaTool tool;

    @Test
    void listAll_returnsFormattedDatasetList() {
        Dataset d1 = new Dataset();
        d1.setId(1L);
        d1.setName("家禽季报");
        d1.setRowCount(100);
        d1.setTemplateId(10L);
        when(datasetRepo.selectList(any(QueryWrapper.class))).thenReturn(List.of(d1));

        String result = tool.analyticsSchema(null);

        assertThat(result).contains("id=1").contains("家禽季报").contains("rows=100");
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
        d.setTemplateId(20L);

        DatasetTemplate t = new DatasetTemplate();
        t.setId(20L);
        t.setPhysicalTable("dataset_test");

        DatasetTemplateField f = new DatasetTemplateField();
        f.setFieldCode("end_stock");
        f.setFieldName("期末存栏");
        f.setFieldType("DECIMAL");
        f.setFieldUnit("只");

        when(datasetRepo.selectById(2L)).thenReturn(d);
        when(templateRepo.selectById(20L)).thenReturn(t);
        when(fieldRepo.selectList(any(QueryWrapper.class))).thenReturn(List.of(f));

        String result = tool.analyticsSchema(2L);

        assertThat(result).contains("end_stock").contains("DECIMAL").contains("单位:只").contains("期末存栏");
    }

    @Test
    void detail_returnsNotFoundWhenDatasetMissing() {
        when(datasetRepo.selectById(99L)).thenReturn(null);

        String result = tool.analyticsSchema(99L);

        assertThat(result).contains("不存在");
    }
}
