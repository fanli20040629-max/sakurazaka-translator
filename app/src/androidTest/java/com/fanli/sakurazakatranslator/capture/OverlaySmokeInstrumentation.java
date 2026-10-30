package com.fanli.sakurazakatranslator.capture;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Spinner;
import com.fanli.sakurazakatranslator.domain.StyleProfile;
import com.fanli.sakurazakatranslator.domain.TranslationRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** No API key or accessibility grant needed. Runs on Android, never on desktop test stubs. */
public final class OverlaySmokeInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> {
            try { checkMemberSelectionWithoutActivity(); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) {
            result.putString("stream", "FAIL: " + failure.get());
            finish(Activity.RESULT_CANCELED, result);
        } else {
            result.putString("stream", "PASS: member choices and confirmation work without an Activity token; no sends.");
            finish(Activity.RESULT_OK, result);
        }
    }

    private void checkMemberSelectionWithoutActivity() {
        // Like AccessibilityService, this themed application Context has no Activity window token.
        Context context = new ContextThemeWrapper(getTargetContext(),
                com.fanli.sakurazakatranslator.R.style.Theme_SakurazakaTranslator);
        List<StyleProfile> profiles = List.of(new StyleProfile("a", "架空メンバーA", ""),
                new StyleProfile("b", "架空メンバーB", ""));
        AtomicReference<StyleProfile> chosen = new AtomicReference<>();
        int[] sends = {0};
        CandidatePanel panel = new CandidatePanel(context, List.of(), List.of(), "synthetic",
                (ids, selected) -> { }, (group, style) -> null,
                (request, model) -> sends[0]++, () -> { }, (request, response) -> { },
                new CandidatePanel.LongMessageActions() {
                    public String text() { return ""; }
                    public String appendSelection() { return ""; }
                    public TranslationRequest prepare(StyleProfile style) { return null; }
                    public void clear() { }
                    public void continueCapture() { }
                }, new MemberResolver.Resolution(MemberResolver.Status.MISSING, null, "", null, Set.of()),
                profiles, 0, chosen::get, profile -> { chosen.set(profile); return true; }, 0);
        List<View> views = new ArrayList<>();
        collect(panel, views);
        // On the old implementation this attempts to open a dialog with no Activity token.
        for (View view : views) if (view instanceof Spinner) view.performClick();
        List<RadioButton> choices = views.stream().filter(RadioButton.class::isInstance)
                .map(RadioButton.class::cast).toList();
        require(choices.size() == 2, "two inline member choices");
        Button confirm = (Button) views.stream().filter(view -> view instanceof Button button
                && button.getText().toString().equals("确认本页成员 / 更正风格")).findFirst().orElseThrow();
        for (int i = 0; i < 20; i++) {
            int index = i % choices.size();
            StyleProfile previous = chosen.get();
            choices.get(index).performClick();
            require(chosen.get() == previous, "selecting alone must not confirm or send");
            confirm.performClick();
            require(chosen.get() == profiles.get(index), "confirmed member must match choice");
        }
        require(sends[0] == 0, "changing members must not send a translation");
    }

    private static void collect(View view, List<View> result) {
        result.add(view);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), result);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
