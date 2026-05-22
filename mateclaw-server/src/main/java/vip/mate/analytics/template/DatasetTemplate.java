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
 * Logical schema template for an analytics dataset.
 *
 * <p>Each template defines a reusable set of typed fields ({@link DatasetTemplateField})
 * and maps to a physical table in the dynamic dataset schema once applied.
 */
@Data
@TableName("mate_dataset_template")
public class DatasetTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long workspaceId;

    /** Stable programmatic identifier; unique per workspace. */
    private String code;

    private String name;

    private String description;

    /** Business category label (e.g. "livestock", "finance"). */
    private String category;

    /**
     * Comma-separated field codes that serve as partition keys.
     * Mirrors the {@code partition_keys} column for quick access without
     * joining to {@code mate_dataset_template_field}.
     */
    private String partitionKeys;

    /**
     * Name of the physical table created by the DDL apply step.
     * Null until the template has been applied at least once.
     */
    private String physicalTable;

    /**
     * SHA-256 hash of the DDL that was last successfully applied.
     * Used to detect schema drift before re-applying.
     */
    private String appliedDdlHash;

    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private Long creator;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updater;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
