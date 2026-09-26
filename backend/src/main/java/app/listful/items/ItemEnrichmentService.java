package app.listful.items;

import app.listful.domain.Item;
import app.listful.domain.enums.ListType;
import app.listful.domain.repository.ItemRepository;
import app.listful.scraping.ScraperService;
import app.listful.scraping.dto.ScrapeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ItemEnrichmentService {
    private static final Logger logger = LoggerFactory.getLogger(ItemEnrichmentService.class);
    public static final String PLACEHOLDER_NAME = "Loading metadata…";

    private final ItemRepository itemRepository;
    private final ScraperService scraperService;
    private final org.springframework.transaction.support.TransactionTemplate transaction;

    public ItemEnrichmentService(ItemRepository itemRepository, ScraperService scraperService, org.springframework.transaction.PlatformTransactionManager manager) {
        this.itemRepository = itemRepository;
        this.scraperService = scraperService;
        this.transaction = new org.springframework.transaction.support.TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void recoverInterruptedImports() {
        transaction.executeWithoutResult(status -> itemRepository.findByImportStatus("PENDING").forEach(item -> {
            if (PLACEHOLDER_NAME.equals(item.getName())) item.setName(fallbackName(item.getUrl()));
            item.setImportStatus("FAILED");
        }));
    }

    @Async("importExecutor")
    public void enrichUrlItem(String itemId, String url) {
        ScrapeResponse metadata;
        try {
            metadata = scraperService.scrape(url);
        } catch (RuntimeException ex) {
            logger.info("URL metadata enrichment failed for item {}", itemId);
            markFailed(itemId, url);
            return;
        }
        updateImport(() -> transaction.executeWithoutResult(status -> itemRepository.findById(itemId)
                .filter(item -> !item.isDeleted() && item.getList().isActive() && url.equals(item.getUrl()) && "PENDING".equals(item.getImportStatus()))
                .ifPresent(item -> {
                    applyMetadata(item, metadata);
                    if (PLACEHOLDER_NAME.equals(item.getName())) item.setName(fallbackName(url));
                    item.setImportStatus("READY");
                })), itemId);
    }

    public void markFailed(String itemId, String url) {
        updateImport(() -> transaction.executeWithoutResult(status -> itemRepository.findById(itemId)
            .filter(item -> !item.isDeleted() && item.getList().isActive() && url.equals(item.getUrl()) && "PENDING".equals(item.getImportStatus()))
            .ifPresent(item -> {
                if (PLACEHOLDER_NAME.equals(item.getName())) item.setName(fallbackName(url));
                item.setImportStatus("FAILED");
            })), itemId);
    }

    private void updateImport(Runnable write, String itemId) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                write.run();
                return;
            } catch (org.springframework.dao.OptimisticLockingFailureException concurrentEdit) {
                // The user's edit wins. Do not replay a stale metadata write.
                return;
            } catch (org.springframework.dao.TransientDataAccessException contention) {
                // Retry in a fresh transaction, re-reading lifecycle and pending state.
                if (attempt == 4) {
                    logger.warn("Metadata persistence remains busy for item {}; pending state is recoverable on restart", itemId);
                    return;
                }
                try { Thread.sleep(50L * (attempt + 1)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    private String fallbackName(String url) {
        try { return java.net.URI.create(url).getHost() == null ? "Imported item" : java.net.URI.create(url).getHost(); }
        catch (IllegalArgumentException ex) { return "Imported item"; }
    }

    private void applyMetadata(Item item, ScrapeResponse metadata) {
        if (item.getList().getType() != ListType.WISH) {
            return;
        }
        if (hasText(metadata.title()) && PLACEHOLDER_NAME.equals(item.getName())) {
            item.setName(metadata.title());
        }
        if (hasText(metadata.description()) && !hasText(item.getDescription())) {
            item.setDescription(metadata.description());
        }
        if (hasText(metadata.imageUrl()) && !hasText(item.getImageUrl())) {
            item.setImageUrl(metadata.imageUrl());
        }
        if (metadata.price() != null && item.getPrice() == null) {
            item.setPrice(metadata.price());
            item.setPriceCurrency(metadata.priceCurrency());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
