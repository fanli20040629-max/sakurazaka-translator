package com.fanli.sakurazakatranslator.capture;

import android.content.Context;
import android.widget.*;
import com.fanli.sakurazakatranslator.domain.*;
import com.fanli.sakurazakatranslator.translation.TranslationSettings;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Explicit text-send confirmation and result display; no network or bitmap ownership. */
final class TranslationPanel extends LinearLayout {
    private final BiFunction<Boolean, StyleProfile, TranslationRequest> prepareRequest;
    private final Runnable cancelRequest;
    private final CheckBox grouping;
    private final CheckBox useDraft;
    private final Function<StyleProfile, TranslationRequest> prepareDraft;
    private final BooleanSupplier hasDraft;
    private final Supplier<StyleProfile> currentStyle;
    private final Button prepare, send, nearby;
    private final LinearLayout confirmation;
    private final TextView original, output;
    private TranslationRequest confirmed;
    private String model;
    private boolean hasSelection;
    private boolean busy;
    private TranslationResult translated;

    TranslationPanel(Context context, BiFunction<Boolean, StyleProfile, TranslationRequest> prepareRequest,
                     BiConsumer<TranslationRequest, String> sendRequest, Runnable cancelRequest,
                     BiConsumer<TranslationRequest, TranslationResult> showNearby,
                     Function<StyleProfile, TranslationRequest> prepareDraft, BooleanSupplier hasDraft,
                     Supplier<StyleProfile> currentStyle) {
        super(context);
        this.prepareRequest = prepareRequest;
        this.cancelRequest = cancelRequest;
        this.prepareDraft = prepareDraft;
        this.hasDraft = hasDraft;
        this.currentStyle = currentStyle;
        setOrientation(VERTICAL);
        grouping = new CheckBox(context);
        grouping.setText("按聊天列表结构合并（可取消，逐片段翻译）");
        grouping.setChecked(true);
        grouping.setOnCheckedChangeListener((button, checked) -> invalidateTranslation());
        addView(grouping);
        useDraft = new CheckBox(context);
        useDraft.setText("已核对长文草稿从开头到结尾完整，改用草稿翻译");
        addView(useDraft);
        prepare = button("准备中文翻译");
        prepare.setEnabled(false);
        prepare.setOnClickListener(v -> prepare());
        confirmation = new LinearLayout(context);
        confirmation.setOrientation(VERTICAL);
        original = label("");
        output = label("");
        confirmation.addView(original);
        send = new Button(context);
        send.setText("确认：发送这些文字到 DeepSeek");
        send.setOnClickListener(v -> {
            if (confirmed == null) return;
            busy = true;
            send.setEnabled(false);
            prepare.setEnabled(false);
            output.setText("正在翻译…可取消；原文保留在下方确认区。");
            sendRequest.accept(confirmed, model);
        });
        confirmation.addView(send);
        addView(confirmation);
        confirmation.setVisibility(GONE);
        Button cancel = button("取消翻译 / 清除译文");
        cancel.setOnClickListener(v -> {
            invalidateTranslation();
            output.setText("已取消显示。已经发出的请求可能仍由服务商处理或计费。");
        });
        addView(output);
        nearby = button("打开阅读卡片");
        nearby.setVisibility(GONE);
        nearby.setOnClickListener(v -> {
            if (confirmed != null && translated != null) showNearby.accept(confirmed, translated);
        });
        useDraft.setOnCheckedChangeListener((button, checked) -> invalidateTranslation());
        refreshDraft();
    }

    void setHasSelection(boolean value) {
        hasSelection = value;
        prepare.setEnabled((value || (useDraft.isChecked() && hasDraft.getAsBoolean())) && !busy);
    }

    void refreshDraft() {
        useDraft.setChecked(false);
        useDraft.setVisibility(hasDraft.getAsBoolean() ? VISIBLE : GONE);
        setHasSelection(hasSelection);
    }

    /** Only reached from an explicit capture click with saved quick-send consent and conservative input. */
    void quickTranslate() {
        if (busy || hasDraft.getAsBoolean()) return;
        prepare();
        if (confirmed != null && send.isEnabled()) send.performClick();
    }

    private void prepare() {
        invalidateTranslation();
        TranslationSettings settings = new TranslationSettings(getContext());
        StyleProfile style = currentStyle.get();
        if (style == null) {
            output.setText("尚未确认当前成员。请核对上方姓名，或手动选择并确认本页成员后再翻译。");
            return;
        }
        confirmed = useDraft.isChecked() ? prepareDraft.apply(style)
                : prepareRequest.apply(grouping.isChecked(), style);
        if (confirmed == null) {
            output.setText("请先选择原文；页面变化后需要重新取字。");
            return;
        }
        model = settings.model();
        StringBuilder text = new StringBuilder("将发送到 DeepSeek（").append(model)
                .append("）\n风格：").append(style.displayName)
                .append("\n风格说明：").append(style.guidance)
                .append("\n只发送以下文字及风格，不发送截图。\n"
                        + "分组仅是建议，请核对作者、时间、重复内容和表情。\n");
        for (int i = 0; i < confirmed.messages.size(); i++) {
            ChatMessage message = confirmed.messages.get(i);
            text.append("\n【").append(i + 1).append("】");
            if (message.warnings.contains("GROUPING_SUGGESTED")) text.append("（建议合并）");
            if ("OCR".equals(message.source)) text.append("（OCR：表情待核对）");
            if (message.warnings.contains("POSSIBLY_CLIPPED")) text.append("（首尾可能不完整）");
            if (message.warnings.contains("MANUAL_MULTISCREEN")) text.append("（手动补取，完整性由你核对）");
            text.append("\n").append(message.originalText).append("\n");
        }
        original.setText(text);
        confirmation.setVisibility(VISIBLE);
        send.setEnabled(true);
    }

    /** Changes to the selection invalidate both the visible confirmation and any in-flight result. */
    void invalidateTranslation() {
        cancelRequest.run();
        busy = false;
        confirmed = null;
        translated = null;
        nearby.setVisibility(GONE);
        confirmation.setVisibility(GONE);
        original.setText("");
        output.setText("");
        setHasSelection(hasSelection);
    }

    void showResult(TranslationRequest request, TranslationResult result) {
        if (confirmed != request) return;
        busy = false;
        translated = result;
        nearby.setVisibility(VISIBLE);
        StringBuilder text = new StringBuilder("中文译文（请对照含义和语气；原文表情缺失无法自动恢复）\n");
        for (int i = 0; i < request.messages.size(); i++) {
            text.append("\n【").append(i + 1).append("】\n原文：")
                    .append(request.messages.get(i).originalText)
                    .append("\n中文：").append(result.translations.get(i).text()).append("\n");
        }
        output.setText(text);
        setHasSelection(hasSelection);
        send.setEnabled(false);
    }

    void showFailure(String code) {
        if ("MEMBER_UNCONFIRMED".equals(code)) {
            invalidateTranslation();
            output.setText("暂时无法确认当前成员，已结束等待并取消旧发送确认。请重新取字核对；"
                    + "已发出的请求可能已计费，没有自动重试。");
            return;
        }
        busy = false;
        String message = switch (code) {
            case "KEY_MISSING" -> "请先在助手首页设置 API Key。";
            case "CONFIGURATION" -> "设置或加密 Key 无法读取，请在首页重新保存。";
            case "AUTH" -> "API Key 无效或没有权限，请核对。";
            case "BALANCE" -> "账号余额不足，请查看服务商账户。";
            case "RATE_LIMIT" -> "请求过于频繁或额度受限，请稍后再试。";
            case "TIMEOUT" -> "请求超时；没有自动重试。";
            case "BUSY" -> "上一请求尚未结束，请稍后手动重试。";
            case "VALIDATION" -> "返回内容、消息编号或表情校验未通过；保留原文，请减少选择后重试。";
            case "INPUT_LIMIT" -> "所选文字太多，请减少后重试（最多 24 项、约 6000 字符）。";
            case "REQUEST" -> "接口拒绝请求，请核实模型名和账号是否支持此接口。";
            case "SERVER" -> "服务商暂时不可用，请稍后再试。";
            case "REDIRECT" -> "接口要求跳转，已停止发送以保护 Key。";
            case "RESPONSE_LIMIT" -> "返回内容过大，已停止处理。";
            case "CANCELED" -> "请求已取消。";
            case "MEMBER_CHANGED" -> "聊天成员已变化，已清除旧草稿和译文，请重新取字。";
            default -> "网络请求失败，请检查连接后手动重试。";
        };
        output.setText(message + "\n原文仍保留；重新发送可能再次计费。");
        send.setEnabled(confirmed != null);
        setHasSelection(hasSelection);
    }

    private Button button(String title) {
        Button view = new Button(getContext());
        view.setText(title);
        addView(view);
        return view;
    }

    private TextView label(String text) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(16);
        view.setTextIsSelectable(true);
        view.setTextColor(android.graphics.Color.DKGRAY);
        return view;
    }
}
