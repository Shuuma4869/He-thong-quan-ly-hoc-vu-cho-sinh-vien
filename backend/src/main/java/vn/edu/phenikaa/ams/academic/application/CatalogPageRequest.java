package vn.edu.phenikaa.ams.academic.application;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record CatalogPageRequest(int limit, String search, String afterCode, UUID afterId) {
    public static CatalogPageRequest parse(Integer limit, String search, String cursor) {
        int size = limit == null ? 50 : limit;
        String term = search == null ? "" : search.strip();
        if (size < 1 || size > 100 || term.length() > 100 || term.indexOf('\0') >= 0)
            throw invalid();
        if (cursor == null) return new CatalogPageRequest(size, term, null, null);
        try {
            if (cursor.length() > 280) throw invalid();
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals("1")) throw invalid();
            String code = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            UUID id = UUID.fromString(parts[2]);
            if (code.isBlank() || code.length() > 40 || code.indexOf('\0') >= 0
                    || !cursor.equals(encode(code, id))) throw invalid();
            return new CatalogPageRequest(size, term, code, id);
        } catch (IllegalArgumentException ex) {
            throw invalid();
        }
    }

    public static String encode(String code, UUID id) {
        return "1." + Base64.getUrlEncoder().withoutPadding().encodeToString(code.getBytes(StandardCharsets.UTF_8)) + "." + id;
    }

    public String searchPattern() {
        return "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giới hạn trang, từ khóa hoặc con trỏ không hợp lệ.");
    }
}
