package app.listful.maps;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/maps")
public class MapController {
    private final String tileUrl;
    private final String attribution;

    public MapController(
            @Value("${LISTFUL_MAP_TILE_URL:https://tile.openstreetmap.org/{z}/{x}/{y}.png}") String tileUrl,
            @Value("${LISTFUL_MAP_ATTRIBUTION:© <a href='https://www.openstreetmap.org/copyright'>OpenStreetMap contributors</a>}") String attribution) {
        URI uri = URI.create(tileUrl.replace("{z}", "0").replace("{x}", "0").replace("{y}", "0"));
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || !tileUrl.contains("{z}") || !tileUrl.contains("{x}") || !tileUrl.contains("{y}")) {
            throw new IllegalArgumentException("LISTFUL_MAP_TILE_URL must be an HTTPS tile template with {z}, {x}, and {y}.");
        }
        this.tileUrl = tileUrl;
        this.attribution = attribution;
    }

    @GetMapping("/config")
    public Config config() {
        return new Config(tileUrl, attribution);
    }

    public record Config(String tileUrl, String attribution) {}
}
