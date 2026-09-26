package app.listful.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import app.listful.domain.Item;
import app.listful.domain.ListEntity;
import app.listful.domain.enums.ListType;
import app.listful.domain.repository.ItemRepository;
import app.listful.scraping.ScraperService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ItemEnrichmentServiceTests {
    private final ItemRepository repository = mock(ItemRepository.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final ItemEnrichmentService service = new ItemEnrichmentService(repository, mock(ScraperService.class), manager);

    @Test
    void retriesContentionInFreshTransactionAndPersistsUsefulFailureState() {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        var list = new ListEntity(null, "Wishes", null, ListType.WISH, Instant.now());
        var item = new Item(list, ItemEnrichmentService.PLACEHOLDER_NAME, Instant.now());
        item.setUrl("https://shop.test/offline");
        item.setImportStatus("PENDING");
        when(repository.findById("item")).thenThrow(new CannotAcquireLockException("busy")).thenReturn(Optional.of(item));
        service.markFailed("item", item.getUrl());
        assertThat(item.getName()).isEqualTo("shop.test");
        assertThat(item.getImportStatus()).isEqualTo("FAILED");
        verify(manager, times(2)).getTransaction(any());
        verify(manager).rollback(any());
        verify(manager).commit(any());
    }

    @Test
    void concurrentUserEditIsNeverRetried() {
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(repository.findById("item")).thenThrow(new ObjectOptimisticLockingFailureException(Item.class, "item"));
        service.markFailed("item", "https://shop.test/offline");
        verify(repository).findById("item");
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }
}
