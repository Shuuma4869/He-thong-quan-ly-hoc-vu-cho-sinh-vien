package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import tools.jackson.databind.JsonNode;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.*;

record PhenikaaEnvelope(boolean success, String encodedData) {
    static PhenikaaEnvelope from(JsonNode root) {
        if (root == null || !root.isObject() || !root.path("Success").isBoolean())
            throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        if (!root.path("Success").booleanValue()) return new PhenikaaEnvelope(false, null);
        JsonNode data = root.path("Data").path("B");
        if (!data.isString() || data.stringValue().isEmpty()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        return new PhenikaaEnvelope(true, data.stringValue());
    }

    @Override public String toString() { return "PhenikaaEnvelope[redacted]"; }
}
