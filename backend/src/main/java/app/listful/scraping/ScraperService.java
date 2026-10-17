package app.listful.scraping;

import app.listful.api.ValidationFailedException;
import app.listful.config.SecurityHardeningProperties;
import app.listful.scraping.dto.ScrapeResponse;
import app.listful.scraping.dto.PageDraftResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.htmlunit.StringWebResponse;
import org.htmlunit.WebClient;
import org.htmlunit.WebConnection;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNode;
import org.htmlunit.html.DomText;
import org.htmlunit.html.HtmlPage;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;

@Service
public class ScraperService {
    private static final String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    private final ObjectMapper objectMapper;
    private final SecurityHardeningProperties securityProperties;

    public ScraperService(ObjectMapper objectMapper, SecurityHardeningProperties securityProperties) {
        this.objectMapper = objectMapper;
        this.securityProperties = securityProperties;
    }

    public ScrapeResponse scrape(String rawUrl) {
        URI uri = validateHttpUrl(rawUrl);
        try {
            HtmlPage document = fetchHtmlPage(uri);
            return extract(document);
        } catch (IOException ex) {
            throw new ValidationFailedException("Could not fetch URL metadata.");
        }
    }

    public PageDraftResponse extractPage(String rawUrl) {
        URI uri = validateHttpUrl(rawUrl);
        try {
            HtmlPage document = fetchHtmlPage(uri);
            ScrapeResponse base = extract(document);
            JsonNode recipe = jsonLdOfType(document, "Recipe");
            JsonNode event = jsonLdOfType(document, "Event");
            JsonNode howTo = jsonLdOfType(document, "HowTo");
            JsonNode product = jsonLdOfType(document, "Product");
            String kind = recipe != null ? "RECIPE" : event != null ? "EVENT" : howTo != null ? "HOWTO" : product != null ? "PRODUCT" : "PAGE";
            JsonNode offer = product == null ? null : firstPricedOffer(product.get("offers"));
            return new PageDraftResponse(
                kind,
                document.getUrl().toString(),
                Instant.now().toString(),
                firstNonBlank(recipe == null ? null : textValue(recipe.get("name")), event == null ? null : textValue(event.get("name")), howTo == null ? null : textValue(howTo.get("name")), base.title()),
                firstNonBlank(recipe == null ? null : textValue(recipe.get("description")), event == null ? null : textValue(event.get("description")), howTo == null ? null : textValue(howTo.get("description")), base.description()),
                base.imageUrl(), base.price(), base.priceCurrency(),
                offer == null ? null : nodeName(offer.get("seller")),
                offer == null ? null : shortSchemaName(textValue(offer.get("availability"))),
                recipe == null ? List.of() : textEntries(recipe.get("recipeIngredient"), 40),
                howTo == null ? List.of() : textEntries(howTo.get("step"), 30),
                event == null ? null : textValue(event.get("startDate")),
                event == null ? null : nodeName(event.get("location"))
            );
        } catch (IOException ex) {
            throw new ValidationFailedException("Could not fetch URL metadata.");
        }
    }

    private JsonNode jsonLdOfType(HtmlPage document, String type) {
        for (DomNode script : document.querySelectorAll("script[type=\"application/ld+json\"]")) {
            try {
                JsonNode match = findType(objectMapper.readTree(script.getTextContent()), type);
                if (match != null) return match;
            } catch (JacksonException ignored) {
                // A malformed block does not hide useful metadata in other blocks.
            }
        }
        return null;
    }

    private JsonNode findType(JsonNode node, String type) {
        if (node == null || node.isNull()) return null;
        if (isSchemaType(node.get("@type"), type)) return node;
        for (JsonNode child : node) {
            JsonNode match = findType(child, type);
            if (match != null) return match;
        }
        return null;
    }

    private boolean isSchemaType(JsonNode node, String type) {
        if (node == null || node.isNull()) return false;
        if (node.isArray()) {
            for (JsonNode candidate : node) if (isSchemaType(candidate, type)) return true;
            return false;
        }
        String value = textValue(node);
        if (value == null) return false;
        String localType = value.substring(Math.max(value.lastIndexOf('/'), value.lastIndexOf('#')) + 1);
        return localType.equalsIgnoreCase(type) || ("Event".equals(type) && localType.endsWith("Event"));
    }

    private JsonNode firstPricedOffer(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isArray()) {
            for (JsonNode candidate : node) {
                JsonNode object = firstPricedOffer(candidate);
                if (object != null) return object;
            }
            return null;
        }
        return node.isObject() && parsePrice(textValue(node.get("price"))) != null ? node : null;
    }

    private String nodeName(JsonNode node) {
        if (node == null || node.isNull()) return null;
        return node.isObject() ? textValue(node.get("name")) : textValue(node);
    }

    private String shortSchemaName(String value) {
        if (value == null) return null;
        String name = value.substring(value.lastIndexOf('/') + 1);
        return name.length() <= 40 ? name : null;
    }

    private List<String> textEntries(JsonNode entries, int limit) {
        if (entries == null || entries.isNull()) return List.of();
        List<String> result = new ArrayList<>();
        Iterable<JsonNode> values = entries.isArray() ? entries : List.of(entries);
        for (JsonNode entry : values) {
            if (result.size() >= limit) break;
            String value = entry.isObject() ? firstNonBlank(textValue(entry.get("text")), textValue(entry.get("name")), textValue(entry.get("value"))) : textValue(entry);
            if (value != null) result.add(value.length() <= 255 ? value : value.substring(0, 255));
        }
        return result;
    }

    ScrapeResponse extract(HtmlPage document) {
        ProductMetadata product = jsonLdProduct(document);
        PriceMetadata price = firstPrice(
            new PriceMetadata(parsePrice(product.price()), product.priceCurrency()),
            new PriceMetadata(parsePrice(meta(document, "meta[property=\"product:price:amount\"]")), null),
            new PriceMetadata(parsePrice(attr(document, "[itemprop=\"price\"]", "content")), null),
            new PriceMetadata(parsePrice(text(document, "[itemprop=\"price\"]")), null),
            new PriceMetadata(parsePrice(amazonVisiblePrice(document)), null)
        );
        return new ScrapeResponse(
            firstNonBlank(
                product.name(),
                meta(document, "meta[property=\"og:title\"]"),
                meta(document, "meta[name=\"twitter:title\"]"),
                text(document, "#productTitle"),
                nonGenericPageTitle(text(document, "title"))
            ),
            firstNonBlank(
                product.description(),
                productDescription(document),
                meta(document, "meta[property=\"og:description\"]"),
                meta(document, "meta[name=\"description\"]")
            ),
            absoluteUrl(document, firstNonBlank(
                product.image(),
                meta(document, "meta[property=\"og:image\"]"),
                meta(document, "meta[name=\"twitter:image\"]"),
                attr(document, "#landingImage", "data-old-hires"),
                attr(document, "#landingImage", "src"),
                productGalleryImage(document)
            )),
            price.price(),
            price.priceCurrency()
        );
    }

    private HtmlPage fetchHtmlPage(URI startUri) throws IOException {
        URI uri = startUri;
        for (int redirect = 0; redirect <= 5; redirect++) {
            FetchResponse response = fetchOnce(uri);
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                uri = validateRedirectLocation(uri, response.header("location"));
                continue;
            }
            try (WebClient client = new WebClient()) {
                client.getOptions().setJavaScriptEnabled(false);
                client.getOptions().setCssEnabled(false);
                client.getOptions().setDownloadImages(false);
                URL pageUrl = uri.toURL();
                String html = new String(response.body(), StandardCharsets.UTF_8);
                // Only fetchOnce may use the network; parsing cannot follow embedded resources.
                client.setWebConnection(new WebConnection() {
                    @Override
                    public WebResponse getResponse(WebRequest request) throws IOException {
                        if (!request.getUrl().equals(pageUrl)) {
                            throw new IOException("External HTML resources are disabled.");
                        }
                        return new StringWebResponse(html, StandardCharsets.UTF_8, pageUrl);
                    }

                    @Override
                    public void close() {}
                });
                return client.getPage(pageUrl);
            }
        }
        throw new ValidationFailedException("Too many redirects while scraping URL metadata.");
    }

    private FetchResponse fetchOnce(URI uri) throws IOException {
        InetAddress address = resolvePublicAddress(uri);
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        try (Socket plainSocket = new Socket()) {
            plainSocket.connect(new InetSocketAddress(address, port), 8_000);
            plainSocket.setSoTimeout(8_000);
            Socket socket = plainSocket;
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                SSLParameters parameters = new SSLParameters();
                parameters.setServerNames(List.of(new SNIHostName(uri.getHost())));
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                SSLSocket sslSocket = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(plainSocket, uri.getHost(), port, true);
                sslSocket.setSSLParameters(parameters);
                sslSocket.startHandshake();
                socket = sslSocket;
            }
            writeRequest(socket.getOutputStream(), uri);
            return readResponse(socket.getInputStream());
        }
    }

    URI validateRedirectLocation(URI currentUri, String location) {
        if (location == null || location.isBlank()) {
            throw new ValidationFailedException("Redirect response is missing a Location header.");
        }
        return validateHttpUrl(currentUri.resolve(location).toString());
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
            rejectPrivateAddress(uri);
            return uri;
        } catch (URISyntaxException ex) {
            throw new ValidationFailedException("URL is invalid.");
        }
    }

    private InetAddress resolvePublicAddress(URI uri) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
            if (securityProperties.isScraperAllowPrivateAddresses()) {
                return addresses[0];
            }
            for (InetAddress address : addresses) {
                if (!isBlockedAddress(address)) {
                    return address;
                }
            }
            throw new ValidationFailedException("Scraping private or local network addresses is not allowed.");
        } catch (ValidationFailedException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ValidationFailedException("Could not resolve URL host.");
        }
    }

    private void rejectPrivateAddress(URI uri) {
        if (securityProperties.isScraperAllowPrivateAddresses()) {
            return;
        }
        resolvePublicAddress(uri);
    }

    private void writeRequest(OutputStream outputStream, URI uri) throws IOException {
        String path = uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            path += "?" + uri.getRawQuery();
        }
        String host = uri.getHost();
        if (uri.getPort() > 0) {
            host += ":" + uri.getPort();
        }
        String request = "GET " + path + " HTTP/1.1\r\n"
            + "Host: " + host + "\r\n"
            + "User-Agent: " + USER_AGENT + "\r\n"
            + "Accept: " + ACCEPT + "\r\n"
            + "Accept-Language: de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7\r\n"
            + "Cache-Control: max-age=0\r\n"
            + "Upgrade-Insecure-Requests: 1\r\n"
            + "Sec-Fetch-Dest: document\r\n"
            + "Sec-Fetch-Mode: navigate\r\n"
            + "Sec-Fetch-Site: none\r\n"
            + "Sec-Fetch-User: ?1\r\n"
            + "Referer: https://www.amazon.de/\r\n"
            + "Cookie: i18n-prefs=EUR; lc-acbde=de_DE\r\n"
            + "Connection: close\r\n\r\n";
        outputStream.write(request.getBytes(StandardCharsets.ISO_8859_1));
        outputStream.flush();
    }

    private FetchResponse readResponse(InputStream inputStream) throws IOException {
        String headers = readHeaders(inputStream);
        String[] lines = headers.split("\\r?\\n");
        if (lines.length == 0 || !lines[0].startsWith("HTTP/")) {
            throw new ValidationFailedException("Scraper received an invalid HTTP response.");
        }
        int statusCode = Integer.parseInt(lines[0].split(" ", 3)[1]);
        List<Header> parsedHeaders = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf(':');
            if (separator > 0) {
                parsedHeaders.add(new Header(lines[i].substring(0, separator).toLowerCase(Locale.ROOT), lines[i].substring(separator + 1).trim()));
            }
        }
        byte[] body = isChunked(parsedHeaders) ? readChunkedBody(inputStream) : readBounded(inputStream, 1_048_576);
        return new FetchResponse(statusCode, parsedHeaders, body);
    }

    private String readHeaders(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;
        byte[] marker = new byte[] {'\r', '\n', '\r', '\n'};
        int next;
        while ((next = inputStream.read()) != -1) {
            buffer.write(next);
            matched = next == marker[matched] ? matched + 1 : (next == marker[0] ? 1 : 0);
            if (matched == marker.length) {
                return buffer.toString(StandardCharsets.ISO_8859_1);
            }
            if (buffer.size() > 16_384) {
                throw new ValidationFailedException("Scraper response headers are too large.");
            }
        }
        throw new ValidationFailedException("Scraper response ended before headers completed.");
    }

    private boolean isChunked(List<Header> headers) {
        return headers.stream().anyMatch(header -> header.name().equals("transfer-encoding") && header.value().toLowerCase(Locale.ROOT).contains("chunked"));
    }

    private byte[] readChunkedBody(InputStream inputStream) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        while (true) {
            String sizeLine = readLine(inputStream);
            int semicolon = sizeLine.indexOf(';');
            int size = Integer.parseInt((semicolon >= 0 ? sizeLine.substring(0, semicolon) : sizeLine).trim(), 16);
            if (size == 0) {
                readLine(inputStream);
                return body.toByteArray();
            }
            copyBounded(inputStream, body, size, 1_048_576);
            readLine(inputStream);
        }
    }

    private String readLine(InputStream inputStream) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int previous = -1;
        int next;
        while ((next = inputStream.read()) != -1) {
            if (previous == '\r' && next == '\n') {
                byte[] bytes = line.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.ISO_8859_1);
            }
            line.write(next);
            previous = next;
            if (line.size() > 8192) {
                throw new ValidationFailedException("Scraper response line is too large.");
            }
        }
        throw new ValidationFailedException("Scraper response ended unexpectedly.");
    }

    private byte[] readBounded(InputStream inputStream, int maxBytes) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            copyBytes(body, chunk, read, maxBytes);
        }
        return body.toByteArray();
    }

    private void copyBounded(InputStream inputStream, ByteArrayOutputStream body, int bytesToCopy, int maxBytes) throws IOException {
        byte[] chunk = new byte[8192];
        int remaining = bytesToCopy;
        while (remaining > 0) {
            int read = inputStream.read(chunk, 0, Math.min(chunk.length, remaining));
            if (read == -1) {
                throw new ValidationFailedException("Scraper response ended unexpectedly.");
            }
            copyBytes(body, chunk, read, maxBytes);
            remaining -= read;
        }
    }

    private void copyBytes(ByteArrayOutputStream body, byte[] chunk, int read, int maxBytes) {
        if (body.size() + read > maxBytes) {
            throw new ValidationFailedException("Fetched HTML is too large.");
        }
        body.write(chunk, 0, read);
    }

    private boolean isBlockedAddress(InetAddress address) {
        byte[] raw = address.getAddress();
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        if (raw.length == 4) {
            int first = raw[0] & 0xff;
            int second = raw[1] & 0xff;
            return first == 0
                || first == 10
                || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168)
                || (first == 100 && second >= 64 && second <= 127);
        }
        if (raw.length == 16) {
            int first = raw[0] & 0xff;
            int second = raw[1] & 0xff;
            // Only native global-unicast IPv6. Translation/tunnel ranges can encode
            // private IPv4 destinations (NAT64, 6to4, Teredo).
            return (first & 0xe0) != 0x20
                || (first == 0x20 && second == 0x02)
                || (first == 0x20 && second == 0x01 && raw[2] == 0 && raw[3] == 0)
                || isIpv4MappedBlocked(raw);
        }
        return false;
    }

    private boolean isIpv4MappedBlocked(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        if ((raw[10] & 0xff) != 0xff || (raw[11] & 0xff) != 0xff) {
            return false;
        }
        byte[] ipv4 = new byte[] { raw[12], raw[13], raw[14], raw[15] };
        try {
            return isBlockedAddress(InetAddress.getByAddress(ipv4));
        } catch (IOException ex) {
            return true;
        }
    }

    private String meta(HtmlPage document, String selector) {
        return attr(document, selector, "content");
    }

    private String attr(HtmlPage document, String selector, String attribute) {
        DomElement element = document.querySelector(selector);
        if (element == null) {
            return null;
        }
        return blankToNull(element.getAttribute(attribute));
    }

    private String text(HtmlPage document, String selector) {
        DomElement element = document.querySelector(selector);
        return element == null ? null : blankToNull(element.asNormalizedText());
    }

    private String text(DomElement root, String selector) {
        DomElement element = root.querySelector(selector);
        return element == null ? null : blankToNull(element.asNormalizedText());
    }

    private String ownText(DomElement root, String selector) {
        DomElement element = root.querySelector(selector);
        if (element == null) {
            return null;
        }
        StringBuilder ownText = new StringBuilder();
        for (DomNode child : element.getChildren()) {
            if (child instanceof DomText) {
                ownText.append(child.getTextContent());
            }
        }
        return blankToNull(ownText.toString());
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

    private String nonGenericPageTitle(String value) {
        String title = blankToNull(value);
        if (title == null) {
            return null;
        }
        if (title.equalsIgnoreCase("Amazon.de") || title.equalsIgnoreCase("Amazon.com")) {
            return null;
        }
        return title;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String absoluteUrl(HtmlPage document, String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        try {
            return new URI(document.getUrl().toString()).resolve(value).toString();
        } catch (IllegalArgumentException | URISyntaxException ex) {
            return value;
        }
    }

    private PriceMetadata firstPrice(PriceMetadata... candidates) {
        for (PriceMetadata candidate : candidates) {
            if (candidate.price() != null) {
                return candidate;
            }
        }
        return PriceMetadata.EMPTY;
    }

    private String amazonVisiblePrice(HtmlPage document) {
        DomElement price = document.querySelector(".a-price");
        if (price == null) {
            return null;
        }
        String offscreen = text(price, ".a-offscreen");
        if (offscreen != null) {
            return offscreen;
        }
        String whole = ownText(price, ".a-price-whole");
        String fraction = ownText(price, ".a-price-fraction");
        if (whole == null) {
            return null;
        }
        return fraction == null ? whole : whole + "." + fraction;
    }

    private String productGalleryImage(HtmlPage document) {
        DomElement image = document.querySelector("img[itemprop=\"image\"], .product img[src], .product-detail img[src], .os_detail_galmain[src]");
        if (image == null) {
            return null;
        }
        DomElement imageLink = image.closest("a[href]");
        String linkedImage = imageLink == null ? null : imageLink.getAttribute("href");
        if (looksLikeImageUrl(linkedImage)) {
            return linkedImage;
        }
        return image.getAttribute("src");
    }

    private String productDescription(HtmlPage document) {
        return text(document, "[itemprop=\"description\"], .productView-description, .product-description, .product-detail-description, #productDescription");
    }

    private boolean looksLikeImageUrl(String value) {
        String url = blankToNull(value);
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png") || lower.contains(".webp") || lower.contains(".gif");
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

    private ProductMetadata jsonLdProduct(HtmlPage document) {
        for (DomNode script : document.querySelectorAll("script[type=\"application/ld+json\"]")) {
            try {
                JsonNode root = objectMapper.readTree(script.getTextContent());
                JsonNode product = findProduct(root);
                if (product != null) {
                    OfferMetadata offer = offerMetadata(product.get("offers"));
                    return new ProductMetadata(
                        textValue(product.get("name")),
                        textValue(product.get("description")),
                        firstImage(product.get("image")),
                        offer.price(),
                        offer.priceCurrency()
                    );
                }
            } catch (JacksonException ignored) {
                // Bad JSON-LD should not make otherwise useful metadata fail.
            }
        }
        return ProductMetadata.EMPTY;
    }

    private JsonNode findProduct(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (isProduct(node.get("@type"))) {
            return node;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode product = findProduct(child);
                if (product != null) {
                    return product;
                }
            }
        }
        for (JsonNode child : node) {
            JsonNode product = findProduct(child);
            if (product != null) {
                return product;
            }
        }
        return null;
    }

    private boolean isProduct(JsonNode type) {
        if (type == null || type.isNull()) {
            return false;
        }
        if (type.isArray()) {
            for (JsonNode candidate : type) {
                if (isProduct(candidate)) {
                    return true;
                }
            }
            return false;
        }
        String value = textValue(type);
        return value != null && (value.equalsIgnoreCase("Product") || value.toLowerCase(Locale.ROOT).endsWith("/product"));
    }

    private String firstImage(JsonNode image) {
        if (image == null || image.isNull()) {
            return null;
        }
        if (image.isArray()) {
            for (JsonNode candidate : image) {
                String value = firstImage(candidate);
                if (value != null) {
                    return value;
                }
            }
            return null;
        }
        if (image.isObject()) {
            return firstNonBlank(textValue(image.get("url")), textValue(image.get("contentUrl")));
        }
        return textValue(image);
    }

    private OfferMetadata offerMetadata(JsonNode offers) {
        if (offers == null || offers.isNull()) {
            return OfferMetadata.EMPTY;
        }
        if (offers.isArray()) {
            for (JsonNode offer : offers) {
                OfferMetadata metadata = offerMetadata(offer);
                if (parsePrice(metadata.price()) != null) {
                    return metadata;
                }
            }
            return OfferMetadata.EMPTY;
        }
        return new OfferMetadata(textValue(offers.get("price")), textValue(offers.get("priceCurrency")));
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isNumber() ? node.asText() : blankToNull(node.asText(null));
    }

    private record ProductMetadata(String name, String description, String image, String price, String priceCurrency) {
        private static final ProductMetadata EMPTY = new ProductMetadata(null, null, null, null, null);
    }

    private record OfferMetadata(String price, String priceCurrency) {
        private static final OfferMetadata EMPTY = new OfferMetadata(null, null);
    }

    private record PriceMetadata(BigDecimal price, String priceCurrency) {
        private static final PriceMetadata EMPTY = new PriceMetadata(null, null);
    }

    private record Header(String name, String value) {}

    private record FetchResponse(int statusCode, List<Header> headers, byte[] body) {
        private String header(String name) {
            for (Header header : headers) {
                if (header.name().equalsIgnoreCase(name)) {
                    return header.value();
                }
            }
            return null;
        }
    }
}
