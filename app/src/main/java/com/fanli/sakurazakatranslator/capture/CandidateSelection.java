package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.StyleProfile;
import com.fanli.sakurazakatranslator.domain.PageToken;
import com.fanli.sakurazakatranslator.domain.TranslationRequest;
import com.fanli.sakurazakatranslator.domain.TranslationRequestFactory;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.TextFragment;

/** UI-thread owned node/OCR selection. All mutations carry the originating capture identity. */
public final class CandidateSelection {
    private final Map<String, TextFragment> candidates = new LinkedHashMap<>();
    private final Set<String> selectedIds = new HashSet<>();
    private PageToken page;
    private boolean ocrReceived;

    public void open(PageToken token, List<TextFragment> nodes) {
        Objects.requireNonNull(token, "pageToken");
        Map<String, TextFragment> next = index(nodes);
        if (next.values().stream().anyMatch(f -> f.source == ProbeModels.Source.OCR)) {
            throw new IllegalArgumentException("Expected node candidates");
        }
        clear();
        page = token;
        candidates.putAll(next);
    }

    /** Exactly one OCR completion per capture. Append atomically, retaining selected node IDs. */
    public boolean appendOcr(PageToken token, List<TextFragment> fragments) {
        if (!isCurrent(token) || ocrReceived) return false;
        Map<String, TextFragment> next = index(fragments);
        if (next.values().stream().anyMatch(f -> f.source != ProbeModels.Source.OCR)
                || next.keySet().stream().anyMatch(candidates::containsKey)) return false;
        candidates.putAll(next);
        ocrReceived = true;
        return true;
    }

    private static Map<String, TextFragment> index(List<TextFragment> fragments) {
        Map<String, TextFragment> next = new LinkedHashMap<>();
        for (TextFragment fragment : TextAssembly.screenOrder(fragments)) {
            if (fragment.rawText == null || fragment.rawText.isBlank()) continue;
            if (next.putIfAbsent(fragment.id, fragment) != null) {
                throw new IllegalArgumentException("Duplicate candidate id");
            }
        }
        return next;
    }

    public List<TextFragment> fragments() { return List.copyOf(candidates.values()); }

    public boolean setSelected(PageToken token, String id, boolean selected) {
        return id != null && setSelected(token, List.of(id), selected);
    }

    /** Validate the whole group before changing anything; true means one refresh is needed. */
    public boolean setSelected(PageToken token, List<String> ids, boolean selected) {
        if (!isCurrent(token) || !candidates.keySet().containsAll(ids)) return false;
        return selected ? selectedIds.addAll(ids) : selectedIds.removeAll(ids);
    }

    public String selectedText() {
        return CandidateTextFormatter.format(fragments(), selectedIds);
    }

    /** A local preview request, not network authorization or proof of message segmentation. */
    public Optional<TranslationRequest> request(PageToken token, StyleProfile style) {
        if (!isCurrent(token)) return Optional.empty();
        var messages = ConfirmedMessageFactory.fromFragments(fragments(), selectedIds);
        return messages.isEmpty() ? Optional.empty()
                : Optional.of(TranslationRequestFactory.confirmed(page, messages, style));
    }

    public boolean isCurrent(PageToken token) { return page != null && page.equals(token); }

    public void clear() {
        candidates.clear();
        selectedIds.clear();
        page = null;
        ocrReceived = false;
    }
}
