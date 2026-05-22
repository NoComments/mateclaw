package vip.mate.analytics.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import vip.mate.analytics.dataset.Dataset;
import vip.mate.analytics.dataset.DatasetRepository;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateField;
import vip.mate.analytics.template.DatasetTemplateFieldRepository;
import vip.mate.analytics.template.DatasetTemplateRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring AI @Tool: exposes available datasets and their column schemas to the Agent.
 *
 * <p>The Agent should call this FIRST before invoking {@link AnalyticsQueryTool} so it
 * knows which physical tables and columns are available for querying.
 *
 * <p><b>Phase-1 limitation:</b> {@code workspace_id} is hardcoded to {@code 1L}.
 * Workspace context is not yet threaded through the Tool layer — tracked as a known
 * concern (DONE_WITH_CONCERNS). Fix is deferred until workspace resolution is added
 * to the agent request context.
 */
@Component
@RequiredArgsConstructor
public class AnalyticsSchemaTool {

    private final DatasetRepository datasetRepo;
    private final DatasetTemplateRepository templateRepo;
    private final DatasetTemplateFieldRepository fieldRepo;

    /**
     * Lists all datasets in the current workspace, or describes one dataset's columns.
     *
     * @param datasetId optional dataset id; pass null to list all datasets
     * @return formatted schema description string for LLM consumption
     */
    @Tool(description = "List available datasets in current workspace, or describe one dataset's columns when datasetId is provided. Always call this FIRST before analytics_query to know which tables and columns exist.")
    public String analyticsSchema(
            @ToolParam(description = "Optional dataset id; omit (pass null) to list all datasets", required = false) Long datasetId
    ) {
        if (datasetId == null) {
            // PHASE-1: workspace_id hardcoded to 1L — replace with context-resolved value once available
            List<Dataset> all = datasetRepo.selectList(
                    new QueryWrapper<Dataset>().eq("workspace_id", 1L).eq("deleted", 0));
            if (all.isEmpty()) {
                return "当前工作空间没有可用数据集。请先上传数据。";
            }
            return all.stream()
                    .map(d -> String.format("- id=%d  name=%s  rows=%d  templateId=%d",
                            d.getId(), d.getName(), d.getRowCount(), d.getTemplateId()))
                    .collect(Collectors.joining("\n", "可用数据集:\n", ""));
        }

        Dataset d = datasetRepo.selectById(datasetId);
        if (d == null) {
            return "数据集不存在: id=" + datasetId;
        }

        DatasetTemplate t = templateRepo.selectById(d.getTemplateId());
        List<DatasetTemplateField> fields = fieldRepo.selectList(
                new QueryWrapper<DatasetTemplateField>()
                        .eq("template_id", t.getId())
                        .eq("deleted", 0)
                        .orderByAsc("ordinal"));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("数据集: %s (id=%d, 物理表=%s, 行数=%d)\n字段清单:\n",
                d.getName(), d.getId(), t.getPhysicalTable(), d.getRowCount()));
        for (DatasetTemplateField f : fields) {
            sb.append(String.format("  - %s (%s", f.getFieldCode(), f.getFieldType()));
            if (f.getFieldUnit() != null && !f.getFieldUnit().isBlank()) {
                sb.append(", 单位:").append(f.getFieldUnit());
            }
            sb.append(") — ").append(f.getFieldName());
            if (f.getSemantic() != null && !f.getSemantic().isBlank()) {
                sb.append(" [").append(f.getSemantic()).append("]");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
