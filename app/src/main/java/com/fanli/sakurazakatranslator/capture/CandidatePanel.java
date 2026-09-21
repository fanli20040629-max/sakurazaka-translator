package com.fanli.sakurazakatranslator.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.fanli.sakurazakatranslator.domain.TranslationRequest;
import com.fanli.sakurazakatranslator.domain.TranslationResult;
import com.fanli.sakurazakatranslator.domain.StyleProfile;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Renders one capture. The service owns page validity, selection, bitmap lifetime and all work. */
final class CandidatePanel extends LinearLayout {
    private final BiConsumer<List<String>, Boolean> onSelection;
    private final TextView status;
    private final TextView summary;
    private final TextView selectedText;
    private final LinearLayout ocrRows;
    private final ImageView image;
    private final Button imageToggle;
    private final TranslationPanel translation;
    private final Map<String, CheckBox> fragmentBoxes = new HashMap<>();
    private final List<GroupToggle> groupToggles = new ArrayList<>();
    private boolean updatingGroups;

    CandidatePanel(Context context, List<TextFragment> nodes, List<MessageGrouper.SelectionGroup> groups,
                   String diagnostics,
                   BiConsumer<List<String>, Boolean> onSelection,
                   BiFunction<Boolean, StyleProfile, TranslationRequest> prepareRequest,
                   BiConsumer<TranslationRequest, String> sendRequest, Runnable cancelRequest,
                   BiConsumer<TranslationRequest, TranslationResult> showNearby) {
        super(context);
        this.onSelection = onSelection;
        setOrientation(VERTICAL);
        addView(label("选择需要翻译的原文（确认发送前仅在本地）\n"
                + "节点通常更能保留表情，但仍需核对是否包含作者、时间或屏外内容。", 15));
        summary = label("尚未选择片段", 16);
        addView(summary);
        selectedText = label("", 17);
        addCollapsible("已选原文", selectedText);
        translation = new TranslationPanel(context, prepareRequest, sendRequest, cancelRequest, showNearby);
        addView(translation);

        addView(label("节点文字 · 默认不选", 17));
        Map<String, MessageGrouper.SelectionGroup> groupStarts = new HashMap<>();
        for (var group : groups) groupStarts.put(group.fragmentIds().get(0), group);
        Set<String> groupedIds = new HashSet<>();
        for (var group : groups) groupedIds.addAll(group.fragmentIds());
        LinearLayout individual = column();
        LinearLayout auxiliary = column();
        int mainCount = 0;
        for (TextFragment fragment : nodes) {
            if (groupStarts.containsKey(fragment.id)) addGroup(groupStarts.get(fragment.id));
            boolean secondary = fragment.role == Role.METADATA || fragment.role == Role.CONTROL
                    || fragment.role == Role.MEDIA_CANDIDATE;
            addCandidate(secondary ? auxiliary : groupedIds.contains(fragment.id) ? individual : this, fragment);
            if (!secondary) mainCount++;
        }
        if (mainCount == 0) addView(label("没有正文节点候选，可核对下方辅助信息或图片文字。", 15));
        if (!groups.isEmpty()) addCollapsible("逐片段调整（可取消组内某一行）", individual);
        if (auxiliary.getChildCount() > 0) addCollapsible("辅助信息（仅当确为正文时勾选）", auxiliary);

        status = label("图片识别准备中…", 15);
        addView(status);
        ocrRows = column();
        addView(ocrRows);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true);
        image.setMaxHeight(Math.round(240 * getResources().getDisplayMetrics().density));
        imageToggle = addCollapsible("本次截图对照", image);
        imageToggle.setEnabled(false);
        addCollapsible("诊断信息", label(diagnostics, 13));
    }

    void attachImage(Bitmap bitmap) {
        image.setImageBitmap(bitmap);
        imageToggle.setEnabled(true);
        status.setText("截图成功 · " + bitmap.getWidth() + "×" + bitmap.getHeight() + "\n图片识别中…");
    }

    /** Update just the OCR section: do not recreate the card or reset node checkboxes/scroll. */
    void showOcr(List<TextFragment> fragments, long elapsedMs) {
        ocrRows.removeAllViews();
        status.setText("图片文字 · " + fragments.size() + " 个片段 · 采集总耗时 " + elapsedMs + "ms\n"
                + "可能缺少或误认表情；作者、时间仍需手动排除。已有节点原文时，避免重复选择同一内容。\n"
                + "两种来源分别排序，尚未自动匹配消息。");
        for (TextFragment fragment : fragments) addCandidate(ocrRows, fragment);
    }

    void showFailure(String reason) {
        status.setText(reason + "\n已读取的节点仍可选择；没有自动重试。");
    }

    void showSelection(String text, TranslationRequest request) {
        int count = request == null ? 0 : request.messages.size();
        String warning = request != null && request.messages.stream()
                .anyMatch(m -> m.warnings.contains("CROSS_SOURCE_UNVERIFIED"))
                ? "\n已混选节点和图片文字，请核对重复内容；按来源分组展示。" : "";
        summary.setText("已选择 " + count + " 个片段（不是已确认的消息条数）" + warning);
        selectedText.setText(text.isEmpty() ? "无" : text);
        translation.setHasSelection(request != null);
    }

    void invalidateTranslation() { translation.invalidateTranslation(); }
    void showTranslation(TranslationRequest request, TranslationResult result) {
        translation.showResult(request, result);
    }
    void showTranslationFailure(String code) { translation.showFailure(code); }

    /** Unbind before the service releases its preview resource lease, even when collapsed. */
    void releaseImage() { image.setImageDrawable(null); }

    private void addCandidate(LinearLayout parent, TextFragment fragment) {
        CheckBox box = new CheckBox(getContext());
        box.setText(fragment.rawText);
        box.setTextSize(17);
        box.setTextColor(Color.BLACK);
        fragmentBoxes.put(fragment.id, box);
        box.setOnCheckedChangeListener((button, checked) -> {
            if (updatingGroups) return;
            onSelection.accept(List.of(fragment.id), checked);
            updateGroups();
        });
        parent.addView(box);
        String source = switch (fragment.source) {
            case NODE_TEXT -> "节点文字";
            case NODE_DESCRIPTION -> "节点描述 · 可能包含辅助信息";
            case OCR -> "图片识别 · 表情和符号未核实";
        };
        if (fragment.role == Role.METADATA || fragment.role == Role.CONTROL) {
            source += " · 勾选表示按正文纳入";
        } else if (fragment.role == Role.UNKNOWN || fragment.role == Role.MEDIA_CANDIDATE) {
            source += " · 内容类型待核对";
        }
        if (fragment.warnings.contains("NODE_FIELDS_DIFFER")) source += " · 两个节点字段不一致";
        if (fragment.warnings.contains("BOUNDARY_MISSING")
                || fragment.warnings.contains("OCR_BOUNDARY_MISSING")) source += " · 位置不明";
        if (fragment.warnings.contains("NODE_TRAVERSAL_TRUNCATED")) source += " · 读取达到上限";
        parent.addView(label(source, 13));
    }

    private void addGroup(MessageGrouper.SelectionGroup group) {
        CheckBox box = new CheckBox(getContext());
        box.setTextColor(Color.BLACK);
        box.setTextSize(17);
        box.setText("整组选择 · " + group.fragmentIds().size() + " 段（请核对）\n" + group.text());
        groupToggles.add(new GroupToggle(group, box));
        box.setOnCheckedChangeListener((button, checked) -> {
            if (updatingGroups) return;
            updatingGroups = true;
            try {
                for (String id : group.fragmentIds()) {
                    CheckBox fragment = fragmentBoxes.get(id);
                    if (fragment != null) fragment.setChecked(checked);
                }
            } finally { updatingGroups = false; }
            // Child checkboxes are now consistent. Notify the service once for the entire edit.
            onSelection.accept(group.fragmentIds(), checked);
            updateGroups();
        });
        addView(box);
    }

    private void updateGroups() {
        updatingGroups = true;
        try {
            for (GroupToggle toggle : groupToggles) {
                long count = toggle.group.fragmentIds().stream()
                        .filter(id -> fragmentBoxes.containsKey(id) && fragmentBoxes.get(id).isChecked()).count();
                toggle.box.setChecked(count == toggle.group.fragmentIds().size());
                toggle.box.setText("整组选择 · 已选 " + count + "/" + toggle.group.fragmentIds().size()
                        + " 段（请核对）\n" + toggle.group.text());
            }
        } finally { updatingGroups = false; }
    }

    private record GroupToggle(MessageGrouper.SelectionGroup group, CheckBox box) { }

    private Button addCollapsible(String title, View content) {
        Button toggle = new Button(getContext());
        toggle.setText("展开" + title);
        content.setVisibility(GONE);
        toggle.setOnClickListener(view -> {
            boolean show = content.getVisibility() != VISIBLE;
            content.setVisibility(show ? VISIBLE : GONE);
            toggle.setText((show ? "收起" : "展开") + title);
        });
        addView(toggle);
        addView(content, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return toggle;
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(VERTICAL);
        return layout;
    }

    private TextView label(String text, int size) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(Color.DKGRAY);
        label.setLineSpacing(0, 1.15f);
        return label;
    }
}
