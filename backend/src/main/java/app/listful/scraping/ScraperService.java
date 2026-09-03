package app.listful.scraping;

import app.listful.api.ValidationFailedException;
import app.listful.config.SecurityHardeningProperties;
import app.listful.scraping.dto.ScrapeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

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
            Document document = fetchDocument(uri);
            return extract(document);
        } catch (IOException ex) {
            throw new ValidationFailedException("Could not fetch URL metadata.");
        }
    }

    ScrapeResponse extract(Document document) {
        return new ScrapeResponse(
            firstNonBlank(
                meta(document, "meta[property=og:title]"),
                meta(document, "meta[name=twitter:title]"),
                text(document, "#productTitle"),
                nonGenericPageTitle(text(document, "title"))
            ),
            firstNonBlank(
                productDescription(document),
                meta(document, "meta[property=og:description]"),
                meta(document, "meta[name=description]")
            ),
            absoluteUrl(document, firstNonBlank(
                meta(document, "meta[property=og:image]"),
                meta(document, "meta[name=twitter:image]"),
                attr(document, "#landingImage", "data-old-hires"),
                attr(document, "#landingImage", "src"),
                productGalleryImage(document)
            )),
            firstPrice(
                meta(document, "meta[property=product:price:amount]"),
                jsonLdOfferPrice(document),
                attr(document, "[itemprop=price]", "content"),
                text(document, "[itemprop=price]"),
                amazonVisiblePrice(document)
            )
        );
    }

    private Document fetchDocument(URI startUri) throws IOException {
        URI uri = startUri;
        for (int redirect = 0; redirect <= 5; redirect++) {
            FetchResponse response = fetchOnce(uri);
            if (response.statusCode() >= 300 && response.statusCode() < 400) {
                uri = validateRedirectLocation(uri, response.header("location"));
                continue;
            }
            return Jsoup.parse(new ByteArrayInputStream(response.body()), StandardCharsets.UTF_8.name(), uri.toString());
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
            return (first & 0xfe) == 0xfc
                || (first == 0xfe && (second & 0xc0) == 0x80)
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

    private String text(Element root, String selector) {
        Element element = root.selectFirst(selector);
        return element == null ? null : blankToNull(element.text());
    }

    private String ownText(Element root, String selector) {
        Element element = root.selectFirst(selector);
        return element == null ? null : blankToNull(element.ownText());
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

    private String amazonVisiblePrice(Document document) {
        Element price = document.selectFirst(".a-price");
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

    private String productGalleryImage(Document document) {
        Element image = document.selectFirst("img[itemprop=image], .product img[src], .product-detail img[src], .os_detail_galmain[src]");
        if (image == null) {
            return null;
        }
        Element imageLink = image.closest("a[href]");
        String linkedImage = imageLink == null ? null : imageLink.attr("href");
        if (looksLikeImageUrl(linkedImage)) {
            return linkedImage;
        }
        return image.attr("src");
    }

    private String productDescription(Document document) {
        return text(document, "[itemprop=description], .productView-description, .product-description, .product-detail-description, #productDescription");
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
