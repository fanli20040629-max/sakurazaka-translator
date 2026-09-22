package com.fanli.sakurazakatranslator;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(32));

        TextView title = text("櫻坂翻译助手 · 中文翻译试用版", 24);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title);
        root.addView(text("先在本页验证节点、截图和 OCR，再测试目标 App。", 16));

        EditText targetPackage = new EditText(this);
        targetPackage.setHint("目标 App 包名，例如 jp.example.app");
        targetPackage.setSingleLine(true);
        targetPackage.setText(ProbePreferences.targetPackage(this));
        root.addView(targetPackage, new LinearLayout.LayoutParams(-1, -2));
        Button save = new Button(this);
        save.setText("保存目标包名");
        save.setOnClickListener(v -> {
            ProbePreferences.saveTargetPackage(this, targetPackage.getText().toString());
            Toast.makeText(this, "目标包名已保存；切换页面后生效", Toast.LENGTH_SHORT).show();
        });
        root.addView(save);

        CheckBox syntheticMode = new CheckBox(this);
        syntheticMode.setText("启用本页合成测试（仅当前测试页显示探针）");
        syntheticMode.setChecked(ProbePreferences.syntheticMode(this));
        syntheticMode.setOnCheckedChangeListener((button, checked) -> {
            ProbePreferences.saveSyntheticMode(this, checked);
            Toast.makeText(this, checked ? "合成测试已开启" : "合成测试已关闭",
                    Toast.LENGTH_SHORT).show();
        });
        root.addView(syntheticMode);
        root.addView(text("关闭合成测试后，助手只响应已保存且当前位于前台的目标包名。目标包名必须先在设备上确认，再手动保存。", 14));

        Button settings = new Button(this);
        settings.setText(getString(R.string.open_accessibility_settings));
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings);

        Button translationSettings = new Button(this);
        translationSettings.setText("设置 DeepSeek 和偶像风格");
        translationSettings.setOnClickListener(v -> startActivity(
                new Intent(this, TranslationSettingsActivity.class)));
        root.addView(translationSettings);

        root.addView(text("合成节点样本", 20));
        addSyntheticMessage(root, 0, "こんにちは～💗", "改行と絵文字を残してね👩🏽‍💻");
        addSyntheticMessage(root, 1, "こんにちは～💗", "別のメッセージです。");
        root.addView(text("櫻坂翻译助手へようこそ。\n改行を含む日本語の長文です。画面上の文章を順番どおりに読み取り、途中で欠けないことを確認してください。", 18));
        root.addView(text("同じ文章を残してください。", 18));
        root.addView(text("同じ文章を残してください。", 18));
        TextView edge = text("画面の端にある文章です。", 18);
        edge.setGravity(Gravity.END);
        root.addView(edge, new LinearLayout.LayoutParams(-1, -2));

        root.addView(text("以下是图片内文字。无障碍节点不应包含它，OCR 应识别它。", 15));
        root.addView(new JapaneseImageView(), new LinearLayout.LayoutParams(-1, dp(180)));
        root.addView(text("跨屏长节点样本（虚构内容，核对是否能一次读到结尾）", 18));
        addSyntheticMessage(root, 2, "ここから長いメッセージの始まりです。",
                ("今日はリハーサルでした。いつも応援してくれてありがとう～💗\n\n").repeat(16)
                        + "ここが最後です。おやすみなさい🌙");
        scroll.addView(root);
        setContentView(scroll);
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.BLACK);
        view.setPadding(0, dp(10), 0, dp(10));
        return view;
    }

    /** Two explicit list items let the device test grouping without using subscription content. */
    private void addSyntheticMessage(LinearLayout parent, int row, String first, String second) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        item.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setCollectionItemInfo(new AccessibilityNodeInfo.CollectionItemInfo.Builder()
                        .setRowIndex(row).setRowSpan(1).setColumnIndex(0).setColumnSpan(1).build());
            }
        });
        item.addView(text(first, 18));
        item.addView(text(second, 18));
        parent.addView(item);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class JapaneseImageView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        JapaneseImageView() {
            super(MainActivity.this);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(245, 240, 225));
            paint.setColor(Color.rgb(25, 25, 25));
            paint.setTextSize(dp(22));
            canvas.drawText("画像内だけの日本語です。", dp(18), dp(70), paint);
            paint.setTextSize(dp(18));
            canvas.drawText("OCR で二行目も確認します。", dp(18), dp(120), paint);
        }
    }
}
