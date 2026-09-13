package com.fanli.sakurazakatranslator.capture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable, Android-free data used by extraction, assembly and tests. */
public final class ProbeModels {
    private ProbeModels() { }

    public enum Role { BODY, METADATA, CONTROL, MEDIA_CANDIDATE, UNKNOWN }
    public enum Source { NODE_TEXT, NODE_DESCRIPTION, OCR }

    public static final class Bounds {
        public final int left, top, right, bottom;
        public Bounds(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
        public boolean isEmpty() { return right <= left || bottom <= top; }
        public int width() { return Math.max(0, right - left); }
        public int height() { return Math.max(0, bottom - top); }
        @Override public String toString() {
            return left + "," + top + "," + right + "," + bottom;
        }
    }

    public static final class NodeRecord {
        public final String id, parentId, rawText, rawDescription, className, viewId;
        public final int childIndex, traversalIndex, windowId;
        public final Bounds screenBounds, windowBounds;
        public final boolean visible, clickable, collectionItem;
        public NodeRecord(String id, String parentId, int childIndex, int traversalIndex,
                          int windowId, String rawText, String rawDescription,
                          String className, String viewId, Bounds screenBounds,
                          Bounds windowBounds, boolean visible, boolean clickable,
                          boolean collectionItem) {
            this.id = id; this.parentId = parentId; this.childIndex = childIndex;
            this.traversalIndex = traversalIndex; this.windowId = windowId;
            this.rawText = rawText; this.rawDescription = rawDescription;
            this.className = className; this.viewId = viewId;
            this.screenBounds = screenBounds; this.windowBounds = windowBounds;
            this.visible = visible; this.clickable = clickable;
            this.collectionItem = collectionItem;
        }
    }

    public static final class TextFragment {
        public final String id, rawText, displayText, messageId, decisionReason;
        public final Source source;
        public final Role role;
        public final Bounds bounds;
        public final List<String> provenance, warnings;
        public final boolean selected;
        public TextFragment(String id, String rawText, String displayText, Source source,
                            Role role, String messageId, Bounds bounds,
                            List<String> provenance, List<String> warnings,
                            String decisionReason, boolean selected) {
            this.id = id; this.rawText = rawText; this.displayText = displayText;
            this.source = source; this.role = role; this.messageId = messageId;
            this.bounds = bounds;
            this.provenance = immutable(provenance); this.warnings = immutable(warnings);
            this.decisionReason = decisionReason; this.selected = selected;
        }
        private static List<String> immutable(List<String> values) {
            return Collections.unmodifiableList(new ArrayList<>(values == null
                    ? Collections.emptyList() : values));
        }
        public TextFragment withSelected(boolean value) {
            return new TextFragment(id, rawText, displayText, source, role, messageId,
                    bounds, provenance, warnings, decisionReason, value);
        }
    }

    public static final class NodeSnapshot {
        public final int windowId;
        public final List<NodeRecord> nodes;
        public final List<TextFragment> fragments;
        public final boolean truncated, layoutUnconfirmed;
        public final String warning;
        public NodeSnapshot(int windowId, List<NodeRecord> nodes, List<TextFragment> fragments,
                            boolean truncated, boolean layoutUnconfirmed, String warning) {
            this.windowId = windowId;
            this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
            this.fragments = Collections.unmodifiableList(new ArrayList<>(fragments));
            this.truncated = truncated; this.layoutUnconfirmed = layoutUnconfirmed;
            this.warning = warning;
        }
    }
}
