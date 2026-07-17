package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * One typed column of a {@link Dataset}'s physical table.
 *
 * <p>Replaces {@code DatasetTemplateField}: fields are now owned by the dataset
 * itself rather than a shared template, so one dataset maps to exactly one
 * physical table.
 */
@Data
@TableName("mate_dataset_field")
public class DatasetField {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long datasetId;

    /** snake_case physical column name. */
    private String fieldCode;

    /** Display name shown in UI and injected into LLM prompts. */
    private String fieldName;

    /** One of: STRING | INT | DECIMAL | BOOLEAN | DATE. */
    private String fieldType;

    private String fieldUnit;

    /** Business meaning injected into LLM system prompts. */
    private String semantic;

    private Boolean isNullable;

    private Integer ordinal;

    /** Exact source header text; used by ExcelHeaderMatcher on upload. */
    private String excelHeader;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
