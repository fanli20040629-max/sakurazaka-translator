package com.fanli.sakurazakatranslator.capture;

import com.fanli.sakurazakatranslator.domain.PageToken;
import com.fanli.sakurazakatranslator.domain.StyleProfile;
import java.util.Set;
import static com.fanli.sakurazakatranslator.capture.MemberResolver.Status;

public final class MemberSessionSelfTest {
    private static int checks;
    private static final StyleProfile A = new StyleProfile("a", "花野あかり", "自然口语");
    private static final StyleProfile B = new StyleProfile("b", "月野しおり", "礼貌口语");
    private static final PageToken PAGE = new PageToken(1, "test.app", 7, 1);
    private static final PageToken NEXT = new PageToken(2, "test.app", 7, 2);
    private static final MemberResolver.Resolution MISSING =
            new MemberResolver.Resolution(Status.MISSING, null, "", null, Set.of());

    public static void main(String[] args) {
        MemberSession session = new MemberSession();
        check(!session.observe(null), "failed title read must preserve retained content");
        check(!session.observe(matched(A)), "first identity is not a switch");
        session.begin(PAGE, matched(A));
        check(session.style(PAGE) == A && session.canQuickTranslate(PAGE), "automatic member selects style");
        check(session.style(NEXT) == null && !session.canQuickTranslate(NEXT), "old capture is isolated");
        check(session.matches(PAGE, matched(A)), "same identity validates before send");
        check(!session.matches(PAGE, matched(B)), "same-window member switch rejects old send");
        check(!session.matches(PAGE, MISSING), "lost title blocks automatic send");
        check(session.bindDraft(PAGE), "draft binds to current member");
        session.endCapture();
        check(session.style(PAGE) == null, "closing capture removes manual and automatic selection");
        check(!session.observe(MISSING), "missing title is not a switch");
        session.begin(NEXT, MISSING);
        check(session.style(NEXT) == null && !session.canAppendDraft(NEXT), "missing title never inherits old style");
        check(!session.choose(PAGE, B), "queued old manual choice is ignored");
        check(session.choose(NEXT, A) && !session.canQuickTranslate(NEXT), "manual choice cannot quick-send");
        check(!session.canAppendDraft(NEXT), "manual unverified identity cannot silently reuse automatic draft");
        check(!session.observe(matched(A)), "restored original title after scrolling must not clear retained draft");
        session.begin(NEXT, matched(A));
        check(session.canAppendDraft(NEXT), "restored A title resumes same-member draft");
        check(session.observe(matched(B)), "reliable B is a member change");
        session.begin(NEXT, matched(B));
        check(!session.canAppendDraft(NEXT) && !session.bindDraft(NEXT), "B cannot append to A draft");
        session.clearDraft();
        check(session.bindDraft(NEXT), "cleared draft can bind to B");
        check(session.choose(NEXT, A) && session.style(NEXT) == A, "manual correction changes style");
        check(session.matches(NEXT, matched(B)), "manual style does not overwrite actual title identity");
        check(!session.matches(NEXT, matched(A)), "corrected style still cannot bypass a later title switch");
        var unknown = new MemberResolver.Resolution(Status.UNKNOWN, "name:雪野こはる", "雪野こはる", null, Set.of("title"));
        check(session.observe(unknown), "unknown reliable member also changes identity");
        session.clearDraft();
        session.begin(PAGE, unknown);
        check(session.style(PAGE) == null, "unknown member does not inherit B style");
        session.choose(PAGE, A);
        check(session.matches(PAGE, unknown) && !session.matches(PAGE, MISSING), "manual unknown title remains bound");
        check(session.bindDraft(PAGE), "unknown title can start a manually confirmed draft");
        session.begin(NEXT, unknown);
        check(session.choose(NEXT, A), "unknown title requires a fresh manual confirmation");
        check(session.canAppendDraft(NEXT), "the same exact unknown title can continue its confirmed draft");
        session.clear();
        check(!session.observe(matched(A)), "full lifecycle clear forgets former member");
        session.begin(NEXT, MISSING);
        session.choose(NEXT, B);
        check(session.matches(NEXT, MISSING) && session.bindDraft(NEXT), "explicit manual missing-title workflow works");
        check(session.canAppendDraft(NEXT), "manual draft remains usable in the same capture");
        check(session.reliableIdentityChanged(matched(A)),
                "new reliable title invalidates a draft started without a title");
        check(session.observe(matched(A)), "event observation detects a reliable title after manual missing-title capture");
        check(!session.matches(NEXT, matched(A)), "new reliable evidence invalidates missing-title confirmation");
        session.endCapture();
        session.begin(PAGE, MISSING);
        check(session.style(PAGE) == null, "manual choice never carries into next capture");
        System.out.println("MemberSessionSelfTest PASS (" + checks + " checks)");
    }

    private static MemberResolver.Resolution matched(StyleProfile profile) {
        return new MemberResolver.Resolution(Status.MATCHED, "profile:" + profile.id,
                profile.displayName, profile, Set.of("title"));
    }
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
}
