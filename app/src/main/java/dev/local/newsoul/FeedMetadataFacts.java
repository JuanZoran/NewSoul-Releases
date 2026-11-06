package dev.local.newsoul;

import org.json.JSONObject;

/** Diagnose missing metadata without recording field values or author identifiers. */
final class FeedMetadataFacts {
    static String valueType(JSONObject data, String field) {
        if (data == null || !data.has(field)) return "missing";
        Object value = data.opt(field);
        return value == null || value == JSONObject.NULL ? "null" : value.getClass().getSimpleName();
    }
    private FeedMetadataFacts() {}
}
