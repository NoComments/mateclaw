package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus mapper for {@link DatasetUploadLog}.
 */
@Mapper
public interface DatasetUploadLogRepository extends BaseMapper<DatasetUploadLog> {

    /**
     * Count upload logs with status SUCCESS or PARTIAL for all datasets
     * that belong to the given template.  Used by the template service to
     * decide whether physical-column-breaking changes are still safe.
     */
    @Select("SELECT COUNT(*) FROM mate_dataset_upload_log ul " +
            "JOIN mate_dataset d ON ul.dataset_id = d.id " +
            "WHERE d.template_id = #{tid} AND d.deleted = 0 " +
            "AND ul.status IN ('SUCCESS', 'PARTIAL')")
    long countSuccessfulByTemplateId(@Param("tid") Long tid);
}
