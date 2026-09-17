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
        label(content, "仅在浮窗确认发送后，将选中文字和风格说明发送到 DeepSeek。不发送截图。");
        EditText model = input(content, "DeepSeek 模型名称", settings.model(), 80, false);
        label(content, "默认模型名需用你的账号实际验证；可按官方可用模型修改。");
        EditText key = input(content, "输入新 API Key（留空保留已保存的 Key）", "", 512, false);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false);
        key.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        TextView status = label(content, settings.hasKey() ? "已保存加密 Key；不会回显。" : "尚未设置 API Key。");
        EditText[] names = new EditText[2], guidance = new EditText[2];
        for (int i = 0; i < 2; i++) {
            StyleProfile profile = settings.profile(i);
            label(content, "风格档案 " + (i + 1));
            names[i] = input(content, "偶像名称", profile.displayName, 60, false);
            guidance[i] = input(content, "中文风格说明（可填 Blog 分析后的摘要）", profile.guidance, 2000, true);
        }
        label(content, "本次使用哪份风格（切换偶像时请手动更换）：");
        Spinner selected = new Spinner(this);
        selected.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"风格档案 1", "风格档案 2"}));
        selected.setSelection(settings.selectedProfile());
        content.addView(selected);
        Button save = button(content, "保存设置");
        save.setOnClickListener(v -> {
            try {
                StyleProfile[] profiles = new StyleProfile[2];
                for (int i = 0; i < 2; i++) profiles[i] = new StyleProfile("idol-" + i,
                        names[i].getText().toString().trim(), guidance[i].getText().toString());
                settings.save(key.getText().toString().trim(), model.getText().toString().trim(),
                        selected.getSelectedItemPosition(), profiles);
                key.setText("");
                status.setText("已保存；下次准备翻译时生效。");
            } catch (Exception e) {
                status.setText("保存失败：请检查名称、模型和 Key。设备密钥存储不可用时不要改用明文保存。");
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
