package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * Minimal dataset entity — placeholder for Task 5 which will complete
 * all columns and business logic.
 *
 * <p>Only the fields required by {@link DatasetRepository} are declared here.
 */
@Data
@TableName("mate_dataset")
public class Dataset {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long templateId;

    @TableLogic
    private Integer deleted;
}
