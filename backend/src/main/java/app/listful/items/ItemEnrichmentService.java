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
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemEnrichmentService {
    private static final Logger logger = LoggerFactory.getLogger(ItemEnrichmentService.class);
    public static final String PLACEHOLDER_NAME = "Loading metadata…";

    private final ItemRepository itemRepository;
    private final ScraperService scraperService;

    public ItemEnrichmentService(ItemRepository itemRepository, ScraperService scraperService) {
        this.itemRepository = itemRepository;
        this.scraperService = scraperService;
    }

    @Async
    @Transactional
    public void enrichUrlItem(String itemId, String url) {
        ScrapeResponse metadata;
        try {
            metadata = scraperService.scrape(url);
        } catch (RuntimeException ex) {
            logger.info("URL metadata enrichment failed for item {}: {}", itemId, ex.getMessage());
            return;
        }

        itemRepository.findById(itemId).ifPresent(item -> applyMetadata(item, metadata));
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
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
