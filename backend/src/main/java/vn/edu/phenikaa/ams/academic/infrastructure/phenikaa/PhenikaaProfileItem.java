package vn.edu.phenikaa.ams.academic.infrastructure.phenikaa;

import tools.jackson.databind.JsonNode;
import vn.edu.phenikaa.ams.academic.application.port.ProfileObservation;
import static vn.edu.phenikaa.ams.academic.infrastructure.phenikaa.PhenikaaClientException.Code.UNEXPECTED_SCHEMA;

record PhenikaaProfileItem(String studentNumber, String programName) {
    static PhenikaaProfileItem from(JsonNode item, String expectedLearnerId) {
        if (item == null || !item.isObject()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        if (!expectedLearnerId.equals(text(item, "ID", true))) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        return new PhenikaaProfileItem(text(item, "MASO", true), text(item, "NGANH", false));
    }

    private static String text(JsonNode item, String field, boolean required) {
        JsonNode value = item.get(field);
        if ((value == null || value.isNull()) && !required) return null;
        if (value == null || !value.isString()) throw new PhenikaaClientException(UNEXPECTED_SCHEMA);
        return value.asString();
    }

    ProfileObservation normalize() {
        try { return new ProfileObservation(studentNumber, programName); }
        catch (IllegalArgumentException ex) { throw new PhenikaaClientException(UNEXPECTED_SCHEMA); }
    }

    @Override public String toString() { return "PhenikaaProfileItem[redacted]"; }
}
