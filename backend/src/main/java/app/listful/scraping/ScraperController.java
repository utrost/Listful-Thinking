package app.listful.scraping;

import app.listful.scraping.dto.ScrapeRequest;
import app.listful.scraping.dto.ScrapeResponse;
import app.listful.scraping.dto.PageDraftResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import app.listful.api.ApiError;

@RestController
@RequestMapping("/api/v1/utils")
public class ScraperController {
    private final ScraperService scraperService;

    public ScraperController(ScraperService scraperService) {
        this.scraperService = scraperService;
    }

    @PostMapping("/scrape")
    public ScrapeResponse scrape(@Valid @RequestBody ScrapeRequest request) {
        return scraperService.scrape(request.url());
    }

    @PostMapping("/extract")
    public PageDraftResponse extract(@Valid @RequestBody ScrapeRequest request) {
        return scraperService.extractPage(request.url());
    }

    @ExceptionHandler(ScraperBusyException.class)
    public ResponseEntity<ApiError> busy(ScraperBusyException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header("Retry-After", "2")
            .body(new ApiError("scraper_busy", ex.getMessage()));
    }
}
