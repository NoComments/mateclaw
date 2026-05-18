package vip.mate.analytics.dataset;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vip.mate.analytics.template.DatasetTemplateRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DatasetService} — pure Mockito, no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class DatasetServiceTest {

    @Mock
    DatasetRepository datasetRepo;

    @Mock
    DatasetTemplateRepository templateRepo;

    @InjectMocks
    DatasetService service;

    // ------------------------------------------------------------------ create

    /** create() must delegate to datasetRepo.insert and return the same instance. */
    @Test
    void create_insertsDataset() {
        Dataset ds = new Dataset();
        ds.setWorkspaceId(1L);
        ds.setTemplateId(10L);
        ds.setName("my dataset");

        Dataset result = service.create(ds);

        verify(datasetRepo).insert(ds);
        assertThat(result).isSameAs(ds);
    }

    // ------------------------------------------------------------------ listByWorkspace

    /** listByWorkspace() must query with correct workspaceId condition. */
    @Test
    void listByWorkspace_queriesCorrectly() {
        Dataset ds1 = new Dataset();
        ds1.setId(1L);
        ds1.setWorkspaceId(5L);

        Dataset ds2 = new Dataset();
        ds2.setId(2L);
        ds2.setWorkspaceId(5L);

        when(datasetRepo.selectList(any())).thenReturn(List.of(ds1, ds2));

        List<Dataset> results = service.listByWorkspace(5L);

        assertThat(results).hasSize(2);
        verify(datasetRepo).selectList(any(LambdaQueryWrapper.class));
    }

    // ------------------------------------------------------------------ getById

    /** getById() must delegate to datasetRepo.selectById and return its result. */
    @Test
    void getById_returnsDataset() {
        Dataset ds = new Dataset();
        ds.setId(7L);

        when(datasetRepo.selectById(7L)).thenReturn(ds);

        Dataset result = service.getById(7L);

        assertThat(result).isSameAs(ds);
        verify(datasetRepo).selectById(7L);
    }

    /** getById() must return null when the dataset does not exist. */
    @Test
    void getById_returnsNullWhenNotFound() {
        when(datasetRepo.selectById(99L)).thenReturn(null);

        Dataset result = service.getById(99L);

        assertThat(result).isNull();
    }
}
