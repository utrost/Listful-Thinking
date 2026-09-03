package app.listful.scraping;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.listful.domain.repository.ItemRepository;
import app.listful.domain.repository.ListRepository;
import app.listful.domain.repository.ListShareRepository;
import app.listful.domain.repository.NotificationRepository;
import app.listful.domain.repository.SettingRepository;
import app.listful.domain.repository.UserRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:scraper-test?mode=memory&cache=shared",
    "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
    "spring.flyway.enabled=true",
    "listful.registration-enabled=true",
    "listful.security.scraper-allow-private-addresses=true"
})
class ScraperControllerTests {
    @Autowired MockMvc mvc;
    @Autowired ItemRepository itemRepository;
    @Autowired ListRepository listRepository;
    @Autowired ListShareRepository listShareRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired SettingRepository settingRepository;
    @Autowired UserRepository userRepository;

    private HttpServer server;
    private volatile String lastUserAgent;
    private volatile String lastAcceptLanguage;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        notificationRepository.deleteAll();
        itemRepository.deleteAll();
        listShareRepository.deleteAll();
        listRepository.deleteAll();
        settingRepository.deleteAll();
        userRepository.deleteAll();
        session = register("owner");
        startFixtureServer();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void extractsOpenGraphMetadataWithBrowserLikeUserAgent() throws Exception {
        mvc.perform(post("/api/v1/utils/scrape")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + baseUrl() + "/og\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Lamy Safari Fountain Pen"))
            .andExpect(jsonPath("$.description").value("A reliable everyday fountain pen."))
            .andExpect(jsonPath("$.imageUrl").value(baseUrl() + "/images/lamy.jpg"))
            .andExpect(jsonPath("$.price").value(24.95));

        org.assertj.core.api.Assertions.assertThat(lastUserAgent)
            .contains("Mozilla")
            .contains("Chrome");
        org.assertj.core.api.Assertions.assertThat(lastAcceptLanguage)
            .contains("de-DE");
    }

    @Test
    void extractsJsonLdOfferPriceWhenNoOpenGraphPriceExists() throws Exception {
        mvc.perform(post("/api/v1/utils/scrape")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + baseUrl() + "/jsonld\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Plotter Paper"))
            .andExpect(jsonPath("$.price").value(12.50));
    }

    @Test
    void extractsAmazonProductDetailFallbacksWhenGenericMetadataIsUseless() throws Exception {
        mvc.perform(post("/api/v1/utils/scrape")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + baseUrl() + "/amazon\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Knipex Zangenschlüssel 180 mm"))
            .andExpect(jsonPath("$.imageUrl").value("https://example.test/knipex.jpg"))
            .andExpect(jsonPath("$.price").value(42.99));
    }

    @Test
    void extractsProductGalleryImageWhenSocialMetadataIsMissing() throws Exception {
        mvc.perform(post("/api/v1/utils/scrape")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + baseUrl() + "/fotoimpex\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("FILMOMAT PhotoPlug Verschlusszeiten-Tester"))
            .andExpect(jsonPath("$.imageUrl").value(baseUrl() + "/shop/images/products/main/Filmomat_Photo_Plug_Verschlusszeit_Tester.jpg"))
            .andExpect(jsonPath("$.price").value(39.90));
    }

    @Test
    void prefersProductDescriptionOverGenericShopDescription() throws Exception {
        mvc.perform(post("/api/v1/utils/scrape")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"" + baseUrl() + "/bigcommerce-product\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Set mit 5 Notizbüchern aus nachhaltigem Papier (B5)"))
            .andExpect(jsonPath("$.description").value("Unser 5er-Set Notizbücher aus Planted Tree Paper wird aus Bäumen hergestellt, die speziell für die Papierproduktion angebaut werden."))
            .andExpect(jsonPath("$.imageUrl").value("https://cdn.example.test/notebook.jpg"))
            .andExpect(jsonPath("$.price").value(5.95));
    }

    @Test
    void rejectsNonHttpUrls() throws Exception {
        mvc.perform(post("/api/v1/utils/scrape")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"file:///etc/passwd\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("validation_failed"))
            .andExpect(jsonPath("$.message", containsString("HTTP")));
    }

    private void startFixtureServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/og", exchange -> {
            lastUserAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            lastAcceptLanguage = exchange.getRequestHeaders().getFirst("Accept-Language");
            respond(exchange, """
                <!doctype html><html><head>
                  <meta property=\"og:title\" content=\"Lamy Safari Fountain Pen\">
                  <meta property=\"og:description\" content=\"A reliable everyday fountain pen.\">
                  <meta property=\"og:image\" content=\"/images/lamy.jpg\">
                  <meta property=\"product:price:amount\" content=\"24.95\">
                  <title>Fallback title</title>
                </head><body></body></html>
                """);
        });
        server.createContext("/jsonld", exchange -> respond(exchange, """
            <!doctype html><html><head>
              <title>Plotter Paper</title>
              <script type=\"application/ld+json\">{"@type":"Product","name":"Plotter Paper","offers":{"@type":"Offer","price":"12.50"}}</script>
            </head><body><span itemprop=\"price\">99.99</span></body></html>
            """));
        server.createContext("/amazon", exchange -> respond(exchange, """
            <!doctype html><html><head><title>Amazon.de</title></head><body>
              <span id=\"productTitle\"> Knipex Zangenschlüssel 180 mm </span>
              <img id=\"landingImage\" src=\"https://example.test/knipex.jpg\" />
              <span class=\"a-price\"><span class=\"a-price-whole\">42<span class=\"a-price-decimal\">,</span></span><span class=\"a-price-fraction\">99</span></span>
            </body></html>
            """));
        server.createContext("/fotoimpex", exchange -> respond(exchange, """
            <!doctype html><html><head>
              <title>FILMOMAT PhotoPlug Verschlusszeiten-Tester</title>
              <meta name=\"description\" content=\"Camera shutter speed tester.\" />
            </head><body>
              <div class=\"os_detail_picbigdiv\">
                <a class=\"os_detail_gallink os_detail_picpop\" href=\"/shop/images/products/main/Filmomat_Photo_Plug_Verschlusszeit_Tester.jpg\">
                  <img class=\"os_detail_galmain\" src=\"/shop/images/products/main/detail/Filmomat_Photo_Plug_Verschlusszeit_Tester.jpg\" alt=\"Bild 1 - FILMOMAT PhotoPlug Verschlusszeiten-Tester\" />
                </a>
              </div>
              <span itemprop=\"price\">39,90 €</span>
            </body></html>
            """));
        server.createContext("/bigcommerce-product", exchange -> respond(exchange, """
            <!doctype html><html><head>
              <title>Set mit 5 Notizbüchern aus nachhaltigem Papier (B5) | MUJI</title>
              <meta property=\"og:title\" content=\"Set mit 5 Notizbüchern aus nachhaltigem Papier (B5)\">
              <meta property=\"og:description\" content=\"Entdecken Sie schlichte, funktionale und hochwertige Haushaltswaren, Kleidung und Lifestyle-Essentials von MUJI.\">
              <meta property=\"og:image\" content=\"https://cdn.example.test/notebook.jpg\">
              <meta property=\"product:price:amount\" content=\"5.95\">
            </head><body>
              <div class=\"productView-description text-body-base-normal\">
                <p>Unser 5er-Set Notizbücher aus Planted Tree Paper wird aus Bäumen hergestellt, die speziell für die Papierproduktion angebaut werden.</p>
              </div>
            </body></html>
            """));
        server.start();
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private MockHttpSession register(String username) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.test\",\"password\":\"correct horse staple\"}"))
            .andExpect(status().isCreated())
            .andReturn().getRequest().getSession(false);
    }
}
