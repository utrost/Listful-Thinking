package app.listful.scraping;

import app.listful.scraping.dto.ScrapeRequest;
import app.listful.scraping.dto.ScrapeResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
