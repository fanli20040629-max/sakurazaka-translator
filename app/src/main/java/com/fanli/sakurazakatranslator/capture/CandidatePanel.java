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
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** Renders one capture. The service owns page validity, selection, bitmap lifetime and all work. */
final class CandidatePanel extends LinearLayout {
    private final BiConsumer<String, Boolean> onSelection;
    private final TextView status;
    private final TextView summary;
    private final TextView selectedText;
    private final LinearLayout ocrRows;
    private final ImageView image;
    private final Button imageToggle;
    private final TranslationPanel translation;

    CandidatePanel(Context context, List<TextFragment> nodes, String diagnostics,
                   BiConsumer<String, Boolean> onSelection,
                   BiFunction<Boolean, StyleProfile, TranslationRequest> prepareRequest,
                   BiConsumer<TranslationRequest, String> sendRequest, Runnable cancelRequest) {
        super(context);
        this.onSelection = onSelection;
        setOrientation(VERTICAL);
        addView(label("选择需要翻译的原文（确认发送前仅在本地）\n"
                + "节点通常更能保留表情，但仍需核对是否包含作者、时间或屏外内容。", 15));
        summary = label("尚未选择片段", 16);
        addView(summary);
        selectedText = label("", 17);
        addCollapsible("已选原文", selectedText);
        translation = new TranslationPanel(context, prepareRequest, sendRequest, cancelRequest);
        addView(translation);

        addView(label("节点文字 · 默认不选", 17));
        LinearLayout auxiliary = column();
        int mainCount = 0;
        for (TextFragment fragment : nodes) {
            boolean secondary = fragment.role == Role.METADATA || fragment.role == Role.CONTROL
                    || fragment.role == Role.MEDIA_CANDIDATE;
            addCandidate(secondary ? auxiliary : this, fragment);
            if (!secondary) mainCount++;
        }
        if (mainCount == 0) addView(label("没有正文节点候选，可核对下方辅助信息或图片文字。", 15));
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
        box.setOnCheckedChangeListener((button, checked) -> onSelection.accept(fragment.id, checked));
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
