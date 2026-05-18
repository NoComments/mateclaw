package vip.mate.analytics.template;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * A single typed column definition belonging to a {@link DatasetTemplate}.
 *
 * <p>Field ordering is captured in {@code ordinal} (0-based) so the physical
 * DDL can emit columns in a deterministic sequence.
 */
@Data
@TableName("mate_dataset_template_field")
public class DatasetTemplateField {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long templateId;

    /** Stable programmatic column name; snake_case convention. */
    private String fieldCode;

    /** Human-readable label shown in the UI. */
    private String fieldName;

    /**
     * Logical data type. Stored as VARCHAR in the DB; resolved to
     * {@link FieldType} by callers via {@link FieldType#fromString(String)}.
     */
    private String fieldType;

    /** Optional SI / business unit (e.g. "kg", "元", "%"). */
    private String fieldUnit;

    /** Free-text semantic description for LLM context injection. */
    private String semantic;

    private Boolean isPartitionKey;

    private Boolean isNullable;

    /** 0-based column position in the physical table DDL. */
    private Integer ordinal;

    /**
     * Expected Excel column header(s) for import matching.
     * May be a comma-separated list of aliases.
     */
    private String excelHeader;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
