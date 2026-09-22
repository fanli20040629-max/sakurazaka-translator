package com.fanli.sakurazakatranslator;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.fanli.sakurazakatranslator.domain.StyleProfile;
import com.fanli.sakurazakatranslator.translation.TranslationSettings;
import java.util.ArrayList;
import java.util.List;

/** Separate secure screen: never show saved credentials or include this page in the probe. */
public final class TranslationSettingsActivity extends Activity {
    private static volatile boolean visible;
    public static boolean isVisible() { return visible; }

    @Override protected void onCreate(Bundle state) {
        visible = true;
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        TranslationSettings settings = new TranslationSettings(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        label(content, "中文翻译设置");
        label(content, "默认在浮窗确认发送后，将选中文字和风格说明发送到 DeepSeek。不发送截图。");
        EditText model = input(content, "DeepSeek 模型名称", settings.model(), 80, false);
        label(content, "默认模型名需用你的账号实际验证；可按官方可用模型修改。");
        EditText key = input(content, "输入新 API Key（留空保留已保存的 Key）", "", 512, false);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false);
        key.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        TextView status = label(content, settings.hasKey() ? "已保存加密 Key；不会回显。" : "尚未设置 API Key。");
        label(content, "聊天标题与以下日文姓名精确匹配后自动选择风格。请填写实际显示的姓名；"
                + "别名每行一个，最多 10 个。不按正文提及或相似昵称猜测，同名冲突时需手动确认。");
        List<ProfileFields> profiles = new ArrayList<>();
        LinearLayout profileContainer = new LinearLayout(this);
        profileContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(profileContainer);
        ArrayAdapter<String> profileChoices = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, new ArrayList<>());
        for (StyleProfile profile : settings.profiles()) {
            profiles.add(addProfile(profileContainer, profiles.size(), profile));
            profileChoices.add("风格档案 " + profiles.size());
        }
        Button addProfile = button(content, "新增成员档案");
        addProfile.setEnabled(profiles.size() < TranslationSettings.MAX_PROFILES);
        addProfile.setOnClickListener(v -> {
            if (profiles.size() >= TranslationSettings.MAX_PROFILES) return;
            profiles.add(addProfile(profileContainer, profiles.size(), new StyleProfile(
                    "idol-" + profiles.size(), "成员档案 " + (profiles.size() + 1),
                    "自然口语，保留原文礼貌程度，不增加原文没有的亲密称呼。")));
            profileChoices.add("风格档案 " + profiles.size());
            addProfile.setEnabled(profiles.size() < TranslationSettings.MAX_PROFILES);
        });
        label(content, "手动选择时优先显示的档案（不会自动用于未识别的聊天）：");
        Spinner selected = new Spinner(this);
        selected.setAdapter(profileChoices);
        selected.setSelection(settings.selectedProfile());
        content.addView(selected);
        CheckBox quickTranslation = new CheckBox(this);
        quickTranslation.setText("开启快捷翻译：每次主动点击取字浮窗，若成员已自动匹配、全部正文候选均获推荐且无警告，"
                + "直接发送这些文字和当前风格到 DeepSeek（可能计费）。有疑点或补取长文时仍需确认。");
        quickTranslation.setChecked(settings.quickTranslation());
        content.addView(quickTranslation);
        Button save = button(content, "保存设置");
        save.setOnClickListener(v -> {
            try {
                StyleProfile[] savedProfiles = new StyleProfile[profiles.size()];
                for (int i = 0; i < profiles.size(); i++) {
                    ProfileFields fields = profiles.get(i);
                    savedProfiles[i] = new StyleProfile("idol-" + i, fields.name.getText().toString().trim(),
                            fields.guidance.getText().toString(), fields.aliases.getText().toString().lines()
                            .map(String::trim).filter(s -> !s.isEmpty()).distinct().toList());
                }
                settings.save(key.getText().toString().trim(), model.getText().toString().trim(),
                        selected.getSelectedItemPosition(), savedProfiles, quickTranslation.isChecked());
                key.setText("");
                status.setText("已保存；下次准备翻译时生效。");
            } catch (Exception e) {
                status.setText("保存失败：请检查姓名、别名（每个最多 60 字符、最多 10 个）、模型和 Key。"
                        + "设备密钥存储不可用时不要改用明文保存。");
            }
        });
        button(content, "删除已保存的 API Key").setOnClickListener(v -> {
            try {
                settings.removeKey();
                key.setText("");
                status.setText("API Key 已删除；风格设置保留。");
            } catch (Exception e) { status.setText("删除失败，请重试。"); }
        });
        button(content, "返回").setOnClickListener(v -> finish());
    }

    @Override protected void onStart() { visible = true; super.onStart(); }
    @Override protected void onStop() { super.onStop(); visible = false; }

    private ProfileFields addProfile(LinearLayout parent, int index, StyleProfile profile) {
        label(parent, "风格档案 " + (index + 1));
        EditText name = input(parent, "聊天页显示的日文姓名", profile.displayName, 60, false);
        EditText aliases = input(parent, "其他完整姓名写法（可留空，每行一个）",
                String.join("\n", profile.aliases), 610, true);
        EditText guidance = input(parent, "中文风格说明（可填 Blog 分析后的摘要）",
                profile.guidance, 2000, true);
        return new ProfileFields(name, aliases, guidance);
    }

    private record ProfileFields(EditText name, EditText aliases, EditText guidance) { }

    private TextView label(LinearLayout parent, String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(16);
        view.setPadding(0, 12, 0, 8);
        parent.addView(view);
        return view;
    }

    private EditText input(LinearLayout parent, String hint, String value, int limit, boolean multiline) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setText(value);
        view.setSingleLine(!multiline);
        view.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        parent.addView(view);
        return view;
    }

    private Button button(LinearLayout parent, String title) {
        Button view = new Button(this);
        view.setText(title);
        parent.addView(view);
        return view;
    }
}
