package com.fanli.sakurazakatranslator.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** User-editable translation tone; it contains guidance, never credentials. */
public final class StyleProfile {
    public final String id;
    public final String displayName;
    public final String guidance;
    public final List<String> aliases;

    public StyleProfile(String id, String displayName, String guidance) {
        this(id, displayName, guidance, List.of());
    }

    public StyleProfile(String id, String displayName, String guidance, List<String> aliases) {
        this.id = require(id, "id");
        this.displayName = require(displayName, "displayName");
        this.guidance = guidance == null ? "" : guidance.trim();
        if (aliases == null) aliases = List.of();
        if (aliases.size() > 10) throw new IllegalArgumentException("aliases");
        List<String> checkedAliases = new ArrayList<>(aliases.size());
        for (String alias : aliases) {
            if (alias == null || alias.trim().isEmpty()) throw new IllegalArgumentException("alias");
            String trimmed = alias.trim();
            if (trimmed.length() > 60) throw new IllegalArgumentException("alias");
            checkedAliases.add(trimmed);
        }
        this.aliases = Collections.unmodifiableList(checkedAliases);
    }

    private static String require(String value, String name) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(name);
        return value;
    }
}
