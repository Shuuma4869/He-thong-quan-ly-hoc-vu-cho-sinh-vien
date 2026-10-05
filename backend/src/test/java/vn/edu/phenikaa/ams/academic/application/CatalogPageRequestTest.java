package vn.edu.phenikaa.ams.academic.application;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class CatalogPageRequestTest {
    @Test void defaultsTrimAndLiteralSearch() {
        var page = CatalogPageRequest.parse(null, "  a!%_  ", null);
        assertThat(page.limit()).isEqualTo(50);
        assertThat(page.search()).isEqualTo("a!%_");
        assertThat(page.searchPattern()).isEqualTo("%a!!!%!_%");
    }
    @Test void cursorRoundTripWithUnicodeAndPunctuation() {
        var id = UUID.randomUUID();
        var page = CatalogPageRequest.parse(100, "", CatalogPageRequest.encode("ĐT.|01", id));
        assertThat(page.afterCode()).isEqualTo("ĐT.|01");
        assertThat(page.afterId()).isEqualTo(id);
    }
    @ParameterizedTest @ValueSource(ints = {-1, 0, 101, Integer.MAX_VALUE})
    void rejectsLimit(int value) {
        assertThatThrownBy(() -> CatalogPageRequest.parse(value, null, null)).isInstanceOf(ResponseStatusException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"", "bad", "1.!.bad", "2.QQ.00000000-0000-0000-0000-000000000000", "1.QQ.1-1-1-1-1"})
    void rejectsCursor(String cursor) {
        assertThatThrownBy(() -> CatalogPageRequest.parse(null, null, cursor)).isInstanceOf(ResponseStatusException.class);
    }
    @Test void rejectsLongSearchAndNullByte() {
        assertThatThrownBy(() -> CatalogPageRequest.parse(null, "a".repeat(101), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CatalogPageRequest.parse(null, "a\0", null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> CatalogPageRequest.parse(null, null, "a".repeat(281))).isInstanceOf(ResponseStatusException.class);
    }
}
