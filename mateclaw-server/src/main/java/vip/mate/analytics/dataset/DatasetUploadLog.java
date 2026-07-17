package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Append-only audit record for each file upload into a {@link Dataset}.
 *
 * <p>This entity has no {@code deleted} / {@code update_time} columns because
 * upload logs are immutable once written — all mutations happen via new rows.
 */
@Data
@TableName("mate_dataset_upload_log")
public class DatasetUploadLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long datasetId;

    private String fileName;

    private Long fileSize;

    private Integer rowsReceived;

    private Integer rowsInserted;

    private Integer rowsRejected;

    /**
     * Processing outcome.
     * One of: {@code PROCESSING}, {@code SUCCESS}, {@code PARTIAL}, {@code FAILED}.
     */
    private String status;

    /** Human-readable summary of any errors encountered during ingestion. */
    private String errorSummary;

    /** User id who performed the upload; may be null for system/API uploads. */
    private Long uploader;

    /** Set automatically on INSERT via MyBatis-Plus field-fill. */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadTime;
}
