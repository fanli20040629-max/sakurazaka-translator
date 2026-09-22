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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
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
import java.util.function.Supplier;
import java.util.function.Consumer;
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
    private CheckBox sameLongMessage;
    private Runnable refreshLongMessage;
    private final Map<String, CheckBox> fragmentBoxes = new HashMap<>();
    private final List<GroupToggle> groupToggles = new ArrayList<>();
    private boolean updatingGroups;

    CandidatePanel(Context context, List<TextFragment> nodes, List<MessageGrouper.SelectionGroup> groups,
                   String diagnostics,
                   BiConsumer<List<String>, Boolean> onSelection,
                   BiFunction<Boolean, StyleProfile, TranslationRequest> prepareRequest,
                   BiConsumer<TranslationRequest, String> sendRequest, Runnable cancelRequest,
                   BiConsumer<TranslationRequest, TranslationResult> showNearby,
                   LongMessageActions longMessages, MemberResolver.Resolution member,
                   List<StyleProfile> profiles, int preferredProfile, Supplier<StyleProfile> currentStyle,
                   Consumer<StyleProfile> chooseMember) {
        super(context);
        this.onSelection = onSelection;
        setOrientation(VERTICAL);
        addView(label("选择需要翻译的原文（确认发送前仅在本地）\n"
                + "节点通常更能保留表情，但仍需核对是否包含作者、时间或屏外内容。", 15));
        summary = label("尚未选择片段", 16);
        addView(summary);
        selectedText = label("", 17);
        addCollapsible("已选原文", selectedText);
        translation = new TranslationPanel(context, prepareRequest, sendRequest, cancelRequest, showNearby,
                longMessages::prepare, () -> !longMessages.text().isEmpty(), currentStyle);
        addMemberControls(member, profiles, preferredProfile, chooseMember);
        addLongMessageControls(longMessages);
        addView(translation);

        addView(label("节点文字 · 仅推荐结构明确的正文，请核对", 17));
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

    void showSelection(String text, TranslationRequest request, Set<String> selectedIds) {
        updatingGroups = true;
        try {
            fragmentBoxes.forEach((id, box) -> box.setChecked(selectedIds.contains(id)));
        } finally { updatingGroups = false; }
        updateGroups();
        int count = request == null ? 0 : request.messages.size();
        String warning = request != null && request.messages.stream()
                .anyMatch(m -> m.warnings.contains("CROSS_SOURCE_UNVERIFIED"))
                ? "\n已混选节点和图片文字，请核对重复内容；按来源分组展示。" : "";
        summary.setText("已选择 " + count + " 个片段（不是已确认的消息条数）" + warning);
        selectedText.setText(text.isEmpty() ? "无" : text);
        translation.setHasSelection(request != null);
    }

    void invalidateTranslation() {
        translation.invalidateTranslation();
        if (sameLongMessage != null) sameLongMessage.setChecked(false);
    }
    void showTranslation(TranslationRequest request, TranslationResult result) {
        translation.showResult(request, result);
    }
    void showTranslationFailure(String code) { translation.showFailure(code); }
    void quickTranslate() { translation.quickTranslate(); }

    void addLocalReadingDemo(Runnable action) {
        Button demo = new Button(getContext());
        demo.setText("免 API 测试阅读卡（虚构短文 / 长文）");
        demo.setOnClickListener(view -> action.run());
        addView(demo, 0);
    }

    private void addMemberControls(MemberResolver.Resolution member, List<StyleProfile> profiles, int preferredProfile,
                                   Consumer<StyleProfile> chooseMember) {
        String description = switch (member.status()) {
            case MATCHED -> "当前成员：" + member.name() + " · 已自动匹配风格";
            case UNKNOWN -> "当前标题：" + member.name() + " · 尚无匹配档案，请手动确认";
            case AMBIGUOUS -> "成员姓名或档案匹配有冲突，请手动核对本页成员";
            case MISSING -> "未确认成员：没有可靠的聊天标题，请手动核对本页成员";
        };
        TextView memberStatus = label(description, 16);
        addView(memberStatus);
        LinearLayout manual = column();
        manual.addView(label("只对本次取字生效；重新取字后重新识别。不能凭正文中提到的人名选择。"
                + "若补取提示身份不一致，请先清除旧草稿，再重新收集。", 14));
        Spinner picker = new Spinner(getContext());
        picker.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item,
                profiles.stream().map(profile -> profile.displayName).toList()));
        int selected = Math.max(0, Math.min(preferredProfile, profiles.size() - 1));
        if (member.profile() != null) {
            for (int i = 0; i < profiles.size(); i++) {
                if (profiles.get(i).id.equals(member.profile().id)) { selected = i; break; }
            }
        }
        picker.setSelection(selected);
        manual.addView(picker);
        Button confirm = new Button(getContext());
        confirm.setText("确认本页成员 / 更正风格");
        confirm.setOnClickListener(view -> {
            int index = picker.getSelectedItemPosition();
            if (index < 0 || index >= profiles.size()) return;
            invalidateTranslation();
            chooseMember.accept(profiles.get(index));
            refreshLongMessage.run();
            memberStatus.setText("本次手动确认：" + profiles.get(index).displayName + " · 不启用快捷发送");
        });
        manual.addView(confirm);
        if (member.status() == MemberResolver.Status.MATCHED) addCollapsible("手动更正成员风格", manual);
        else addView(manual);
    }

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
        if (fragment.warnings.contains("POSSIBLY_CLIPPED")) source += " · 首尾可能在屏外，请核对全文";
        parent.addView(label(source, 13));
    }

    interface LongMessageActions {
        String text();
        String appendSelection();
        TranslationRequest prepare(StyleProfile style);
        void clear();
        void continueCapture();
    }

    private void addLongMessageControls(LongMessageActions actions) {
        LinearLayout section = column();
        section.addView(label("只用于同一条跨屏消息：从开头取字，下一屏保留重叠。"
                + "每屏只选该消息；不会自动翻页或上传图片。未知标题需每次重新确认，标题缺失不能跨屏续接。", 14));
        TextView draft = label(actions.text().isEmpty() ? "尚无长文草稿" : actions.text(), 16);
        TextView feedback = label("", 14);
        refreshLongMessage = () -> {
            draft.setText(actions.text().isEmpty() ? "尚无长文草稿" : actions.text());
            feedback.setText("");
            translation.refreshDraft();
        };
        CheckBox sameMessage = new CheckBox(getContext());
        sameLongMessage = sameMessage;
        sameMessage.setText("已核对：所选片段属于同一条长消息（首次从开头选）");
        sameMessage.setTextColor(Color.DKGRAY);
        section.addView(sameMessage);
        Button append = new Button(getContext());
        append.setText("将所选内容加入长文草稿");
        append.setEnabled(false);
        sameMessage.setOnCheckedChangeListener((button, checked) -> append.setEnabled(checked));
        append.setOnClickListener(view -> {
            translation.invalidateTranslation();
            feedback.setText(actions.appendSelection());
            draft.setText(actions.text().isEmpty() ? "尚无长文草稿" : actions.text());
            sameMessage.setChecked(false);
            translation.refreshDraft();
        });
        section.addView(append);
        section.addView(feedback);
        section.addView(draft);
        Button continueButton = new Button(getContext());
        continueButton.setText("收起，滚动后再点浮窗补取");
        continueButton.setOnClickListener(view -> actions.continueCapture());
        section.addView(continueButton);
        Button clear = new Button(getContext());
        clear.setText("清除长文草稿");
        clear.setOnClickListener(view -> {
            translation.invalidateTranslation();
            actions.clear();
            translation.refreshDraft();
            sameMessage.setChecked(false);
            draft.setText("尚无长文草稿");
            feedback.setText("已清除；不会发送到云端。");
        });
        section.addView(clear);
        addCollapsible("长消息补取 / 草稿（仅本地）", section);
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
