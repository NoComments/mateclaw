package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis-Plus mapper for {@link Dataset}.
 *
 * <p>Full implementation added in Task 5. Only {@code countByTemplateId} is
 * needed here to allow the template service to guard against deleting a
 * template that still has datasets attached.
 */
@Mapper
public interface DatasetRepository extends BaseMapper<Dataset> {

    @Select("SELECT COUNT(*) FROM mate_dataset WHERE template_id = #{tid} AND deleted = 0")
    long countByTemplateId(@Param("tid") Long tid);
}
