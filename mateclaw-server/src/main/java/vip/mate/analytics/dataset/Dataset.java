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
 * A dataset is a versioned collection of uploaded rows bound to a
 * {@link vip.mate.analytics.template.DatasetTemplate}.
 *
 * <p>Physical row storage lives in the dynamic table whose name is
 * derived from the template's {@code physicalTable} column.
 */
@Data
@TableName("mate_dataset")
public class Dataset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long workspaceId;

    private Long templateId;

    private String name;

    private String description;

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
