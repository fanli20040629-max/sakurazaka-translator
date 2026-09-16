package com.fanli.sakurazakatranslator.domain;


/** User-editable translation tone; it contains guidance, never credentials. */
public final class StyleProfile {
    public final String id;
    public final String displayName;
    public final String guidance;

    public StyleProfile(String id, String displayName, String guidance) {
        this.id = require(id, "id");
        this.displayName = require(displayName, "displayName");
        this.guidance = guidance == null ? "" : guidance.trim();
    }

    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
        return value;
    }
}
