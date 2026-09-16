package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.StyleProfile;
import com.fanli.sakurazakatranslator.domain.TranslationRequest;
import com.fanli.sakurazakatranslator.domain.TranslationRequestFactory;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.TextFragment;

/** UI-thread owned selection for one preview. Replacing/closing it drops all prior text. */
public final class OcrSelection {
    private final Map<String, TextFragment> candidates = new LinkedHashMap<>();
    private final Set<String> selectedIds = new HashSet<>();

    public void replace(List<TextFragment> fragments) {
        Map<String, TextFragment> next = new LinkedHashMap<>();
        for (TextFragment fragment : TextAssembly.screenOrder(fragments)) {
            if (fragment.rawText == null || fragment.rawText.isBlank()) continue;
            if (next.putIfAbsent(fragment.id, fragment) != null) {
                throw new IllegalArgumentException("Duplicate OCR candidate id");
            }
        }
        clear();
        candidates.putAll(next);
    }

    public List<TextFragment> fragments() { return List.copyOf(candidates.values()); }

    public boolean setSelected(String id, boolean selected) {
        if (!candidates.containsKey(id)) return false;
        if (selected) selectedIds.add(id);
        else selectedIds.remove(id);
        return true;
    }

    public String selectedText() {
        return OcrSelectionFormatter.format(fragments(), selectedIds);
    }

    /** A local preview request, not network authorization or proof of message segmentation. */
    public Optional<TranslationRequest> request(StyleProfile style) {
        var messages = ConfirmedMessageFactory.fromFragments(fragments(), selectedIds);
        return messages.isEmpty() ? Optional.empty()
                : Optional.of(TranslationRequestFactory.confirmed(messages, style));
    }

    public void clear() {
        candidates.clear();
        selectedIds.clear();
    }
}
