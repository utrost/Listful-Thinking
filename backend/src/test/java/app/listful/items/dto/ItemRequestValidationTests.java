package app.listful.items.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ItemRequestValidationTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsHttpImageUrlsWithTheLegacyLengthLimit() {
        assertThat(violationsFor("https://images.example.test/photo.png")).isZero();
        assertThat(violationsFor("http://images.example.test/photo.png")).isZero();
        assertThat(violationsFor("https://example.test/" + "a".repeat(1_979))).isZero();
    }

    @Test
    void rejectsNonHttpImageValuesAndHttpUrlsOverTheLegacyLimit() {
        assertThat(violationsFor("javascript:alert(1)")).isPositive();
        assertThat(violationsFor("not an image URL")).isPositive();
        assertThat(violationsFor("https://example.test/" + "a".repeat(1_980))).isPositive();
    }

    @Test
    void acceptsOnlyBoundedRasterImageDataUrls() {
        for (String mimeType : new String[] {"image/png", "image/jpeg", "image/webp", "image/gif"}) {
            assertThat(violationsFor("data:" + mimeType + ";base64,dGVzdA==")).isZero();
        }

        assertThat(violationsFor("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=")).isPositive();
        assertThat(violationsFor("data:text/html;base64,PGgxPm5vPC9oMT4=")).isPositive();
        assertThat(violationsFor("data:image/png;base64,not valid base64!")).isPositive();
        assertThat(violationsFor("data:image/png;base64," + "A".repeat(5_000_000))).isPositive();
    }

    private int violationsFor(String imageUrl) {
        ItemRequest request = new ItemRequest(
            "Camera", null, null, imageUrl, null, null, null, null, null, null, null, null
        );
        return validator.validate(request).size();
    }
}
