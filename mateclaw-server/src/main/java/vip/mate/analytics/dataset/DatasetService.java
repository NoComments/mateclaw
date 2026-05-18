package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mate.analytics.template.DatasetTemplate;
import vip.mate.analytics.template.DatasetTemplateRepository;

import java.util.List;

/**
 * CRUD service for {@link Dataset}.
 *
 * <p>The {@code delete} operation is the only method that requires external
 * collaborators ({@link JdbcTemplate}, {@link DatasetTemplateRepository}) because it
 * must purge rows from the dynamic physical table whose name is resolved at runtime.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class DatasetService {

    private final DatasetRepository datasetRepo;

    /**
     * Insert a new empty dataset.
     *
     * @param ds dataset to persist; {@code workspaceId} and {@code templateId} must be set
     * @return the same instance with its generated {@code id} populated
     */
    public Dataset create(Dataset ds) {
        datasetRepo.insert(ds);
        return ds;
    }

    /**
     * Return all non-deleted datasets belonging to a workspace.
     *
     * @param workspaceId target workspace
     */
    @Transactional(readOnly = true)
    public List<Dataset> listByWorkspace(Long workspaceId) {
        return datasetRepo.selectList(
                new LambdaQueryWrapper<Dataset>()
                        .eq(Dataset::getWorkspaceId, workspaceId));
    }

    /**
     * Fetch a single dataset by its primary key.
     *
     * @param id dataset id
     * @return dataset, or {@code null} if not found / soft-deleted
     */
    @Transactional(readOnly = true)
    public Dataset getById(Long id) {
        return datasetRepo.selectById(id);
    }

    /**
     * Soft-delete the dataset record and hard-delete all rows from the
     * dynamic physical table.
     *
     * <p>The physical table name is resolved by looking up the dataset's template
     * via {@code templateRepo} and reading {@code physicalTable}.  The row purge
     * uses a parameterised JDBC update to avoid SQL-injection risk.
     *
     * @param id           dataset id to delete
     * @param jdbc         JDBC template for the dynamic-table purge
     * @param templateRepo repository used to resolve the physical table name
     * @throws IllegalArgumentException if the dataset or its template cannot be found
     */
    public void delete(Long id, JdbcTemplate jdbc, DatasetTemplateRepository templateRepo) {
        Dataset ds = datasetRepo.selectById(id);
        if (ds == null) {
            throw new IllegalArgumentException("Dataset not found: " + id);
        }

        DatasetTemplate template = templateRepo.selectById(ds.getTemplateId());
        if (template == null) {
            throw new IllegalArgumentException("Template not found for dataset " + id
                    + " (templateId=" + ds.getTemplateId() + ")");
        }

        String physicalTable = template.getPhysicalTable();
        if (physicalTable != null && !physicalTable.isBlank()) {
            if (!physicalTable.matches("[a-z][a-z0-9_]{0,95}")) {
                throw new IllegalArgumentException("Unsafe physical table name: " + physicalTable);
            }
            // Parameterised update — dataset_id column is a safe fixed name from DDL
            jdbc.update("DELETE FROM " + physicalTable + " WHERE dataset_id = ?", id);
        }

        // Soft-delete the dataset record (MyBatis-Plus fills the deleted flag)
        datasetRepo.deleteById(id);
    }
}
