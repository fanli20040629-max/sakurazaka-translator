package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.PageToken;
import com.fanli.sakurazakatranslator.domain.StyleProfile;
import java.util.Objects;

/** UI-owned member continuity. Missing evidence suspends selection; it never means another member. */
public final class MemberSession {
    private String lastIdentity, draftIdentity;
    private PageToken draftPage;
    private PageToken page;
    private MemberResolver.Resolution captured;
    private StyleProfile manual;

    /** True when reliable evidence changes ownership or replaces a draft with no title anchor. */
    public boolean observe(MemberResolver.Resolution resolution) {
        if (resolution == null) return false;
        String identity = resolution.identityKey();
        if (identity == null) return false;
        // A temporary missing-title capture must not replace the retained draft's known owner.
        boolean unanchoredDraft = draftIdentity != null && draftIdentity.startsWith("manual:");
        boolean changed = unanchoredDraft || (lastIdentity != null ? !lastIdentity.equals(identity)
                : reliableIdentityChanged(resolution));
        lastIdentity = identity;
        return changed;
    }

    public void begin(PageToken token, MemberResolver.Resolution resolution) {
        page = Objects.requireNonNull(token);
        captured = Objects.requireNonNull(resolution);
        manual = null;
    }

    public boolean choose(PageToken token, StyleProfile profile) {
        if (!current(token) || profile == null) return false;
        manual = profile;
        return true;
    }

    public StyleProfile style(PageToken token) {
        if (!current(token)) return null;
        return manual != null ? manual : captured.profile();
    }

    public boolean canQuickTranslate(PageToken token) {
        return current(token) && manual == null && captured.status() == MemberResolver.Status.MATCHED;
    }

    /** Recheck the captured identity immediately before using its text, never just the chosen style. */
    public boolean matches(PageToken token, MemberResolver.Resolution fresh) {
        if (style(token) == null || fresh == null) return false;
        if (captured.identityKey() != null) return captured.identityKey().equals(fresh.identityKey());
        return manual != null && fresh.identityKey() == null && captured.status() == fresh.status();
    }

    /** True when a fresh title is reliable evidence that the captured identity is different. */
    public boolean reliableIdentityChanged(MemberResolver.Resolution fresh) {
        if (captured == null || fresh == null || fresh.identityKey() == null) return false;
        return captured.identityKey() == null || !fresh.identityKey().equals(captured.identityKey());
    }

    public boolean canAppendDraft(PageToken token) {
        String identity = identity(token);
        if (identity == null) return false;
        if (draftIdentity == null) return true;
        if (draftIdentity.equals(identity) && captured != null) {
            if (captured.status() == MemberResolver.Status.MATCHED) return true;
            // An unknown but stable exact title may continue only after the user confirms
            // the style again for this capture. A missing title has no cross-capture anchor.
            if (captured.identityKey() != null && manual != null) return true;
        }
        return draftPage != null && draftPage.equals(token) && draftIdentity.equals(identity);
    }

    public boolean bindDraft(PageToken token) {
        if (!canAppendDraft(token)) return false;
        draftIdentity = identity(token);
        draftPage = token;
        return true;
    }

    private String identity(PageToken token) {
        StyleProfile profile = style(token);
        if (profile == null) return null;
        return captured.identityKey() != null ? captured.identityKey() : "manual:" + profile.id;
    }

    private boolean current(PageToken token) { return page != null && page.equals(token); }
    public void clearDraft() { draftIdentity = null; draftPage = null; }
    public void endCapture() { page = null; captured = null; manual = null; }
    public void clear() { endCapture(); lastIdentity = null; clearDraft(); }
}
