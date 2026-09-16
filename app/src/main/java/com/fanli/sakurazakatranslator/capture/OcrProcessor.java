package com.fanli.sakurazakatranslator.capture;

import android.graphics.Bitmap;
import android.graphics.Rect;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;
import static com.fanli.sakurazakatranslator.capture.ProbeModels.*;

/** ML Kit adapter. The service owns task validity and keeps the bitmap alive until completion. */
public final class OcrProcessor implements AutoCloseable {
    private final TextRecognizer recognizer = TextRecognition.getClient(
            new JapaneseTextRecognizerOptions.Builder().build());

    public Task<Text> recognize(Bitmap bitmap) {
        return recognizer.process(InputImage.fromBitmap(bitmap, 0));
    }

    /** Each candidate is a line, not a confirmed message or automatically classified author/time. */
    public static List<TextFragment> fragments(Text text) {
        List<TextFragment> result = new ArrayList<>();
        if (text == null || text.getText() == null || text.getText().isBlank()) return result;
        int blockIndex = 0;
        for (Text.TextBlock block : text.getTextBlocks()) {
            blockIndex++;
            List<Text.Line> lines = block.getLines();
            if (lines == null || lines.isEmpty()) {
                result.add(TextAssembly.ocr("ocr:" + blockIndex + ":0", block.getText(),
                        bounds(block.getBoundingBox()), result.size()));
            } else {
                int lineIndex = 0;
                for (Text.Line line : lines) {
                    result.add(TextAssembly.ocr("ocr:" + blockIndex + ":" + (++lineIndex),
                            line.getText(), bounds(line.getBoundingBox()), result.size()));
                }
            }
        }
        return TextAssembly.screenOrder(result);
    }

    private static Bounds bounds(Rect rect) {
        return rect == null ? null : new Bounds(rect.left, rect.top, rect.right, rect.bottom);
    }

    /** Call only after all in-flight recognition tasks have completed. */
    @Override public void close() { recognizer.close(); }
}
