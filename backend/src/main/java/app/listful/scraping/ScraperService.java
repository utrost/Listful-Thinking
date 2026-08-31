package app.listful.scraping;

import app.listful.api.ValidationFailedException;
import app.listful.scraping.dto.ScrapeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

@Service
public class ScraperService {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private final ObjectMapper objectMapper;

    public ScraperService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ScrapeResponse scrape(String rawUrl) {
        URI uri = validateHttpUrl(rawUrl);
        try {
            Document document = Jsoup.connect(uri.toString())
                .userAgent(USER_AGENT)
                .header("Accept", ACCEPT)
                .header("Accept-Language", LocaleContextHolder.getLocale().toLanguageTag())
                .timeout(8_000)
                .followRedirects(true)
                .get();
            return extract(document);
        } catch (IOException ex) {
            throw new ValidationFailedException("Could not fetch URL metadata.");
        }
    }

    ScrapeResponse extract(Document document) {
        return new ScrapeResponse(
            firstNonBlank(meta(document, "meta[property=og:title]"), meta(document, "meta[name=twitter:title]"), text(document, "title")),
            firstNonBlank(meta(document, "meta[property=og:description]"), meta(document, "meta[name=description]")),
            absoluteUrl(document, firstNonBlank(meta(document, "meta[property=og:image]"), meta(document, "meta[name=twitter:image]"))),
            firstPrice(
                meta(document, "meta[property=product:price:amount]"),
                jsonLdOfferPrice(document),
                attr(document, "[itemprop=price]", "content"),
                text(document, "[itemprop=price]")
            )
        );
    }

    private URI validateHttpUrl(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new ValidationFailedException("Only HTTP and HTTPS URLs can be scraped.");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new ValidationFailedException("URL must include a host.");
            }
            return uri;
        } catch (URISyntaxException ex) {
            throw new ValidationFailedException("URL is invalid.");
        }
    }

    private String meta(Document document, String selector) {
        return attr(document, selector, "content");
    }

    private String attr(Document document, String selector, String attribute) {
        Element element = document.selectFirst(selector);
        if (element == null) {
            return null;
        }
        return blankToNull(element.attr(attribute));
    }

    private String text(Document document, String selector) {
        Element element = document.selectFirst(selector);
        return element == null ? null : blankToNull(element.text());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String absoluteUrl(Document document, String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        try {
            return new URI(document.baseUri()).resolve(value).toString();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return value;
        }
    }

    private BigDecimal firstPrice(String... candidates) {
        for (String candidate : candidates) {
            BigDecimal price = parsePrice(candidate);
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    private BigDecimal parsePrice(String candidate) {
        String value = blankToNull(candidate);
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("[^0-9,.-]", "").replace(',', '.');
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String jsonLdOfferPrice(Document document) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = objectMapper.readTree(script.data());
                String price = findOfferPrice(root);
                if (price != null) {
                    return price;
                }
            } catch (IOException ignored) {
                // Bad JSON-LD should not make otherwise useful metadata fail.
            }
        }
        return null;
    }

    private String findOfferPrice(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                String price = findOfferPrice(child);
                if (price != null) {
                    return price;
                }
            }
        }
        JsonNode offers = node.get("offers");
        if (offers != null) {
            if (offers.isArray()) {
                for (JsonNode offer : offers) {
                    String price = textValue(offer.get("price"));
                    if (price != null) {
                        return price;
                    }
                }
            }
            String price = textValue(offers.get("price"));
            if (price != null) {
                return price;
            }
        }
        for (JsonNode child : node) {
            String price = findOfferPrice(child);
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isNumber() ? node.asText() : blankToNull(node.asText(null));
    }
}
