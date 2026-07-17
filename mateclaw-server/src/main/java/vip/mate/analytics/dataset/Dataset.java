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
 * A dataset is a collection of uploaded rows plus the typed schema describing them.
 *
 * <p>Each dataset owns exactly one physical table named {@code dataset_<id>}, whose
 * columns are defined by this dataset's {@link DatasetField} rows. One dataset per
 * table means queries cannot accidentally mix rows from another dataset.
 */
@Data
@TableName("mate_dataset")
public class Dataset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long workspaceId;

    private String name;

    private String description;

    /** Physical table holding this dataset's rows; always "dataset_" + id. */
    private String physicalTable;

    /** SHA-256 of the field list; lets DynamicTableService skip no-op DDL. */
    private String appliedDdlHash;

    /** Cached count of rows currently in the physical table for this dataset. */
    private Integer rowCount;

    /** Timestamp of the most recent successful upload. */
    private LocalDateTime lastUploadAt;

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
