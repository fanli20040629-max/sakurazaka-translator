import com.fanli.sakurazakatranslator.domain.model.ChatMessage;
import com.fanli.sakurazakatranslator.domain.model.TextFragment;
import com.fanli.sakurazakatranslator.domain.translation.SymbolProtector;
import java.util.List;
import java.util.ArrayList;
import com.fanli.sakurazakatranslator.domain.model.Bounds;
import com.fanli.sakurazakatranslator.domain.model.PageToken;
import com.fanli.sakurazakatranslator.domain.assembly.MessageAssembler;
import com.fanli.sakurazakatranslator.domain.style.StyleProfile;
import com.fanli.sakurazakatranslator.domain.translation.TranslationRequest;
import com.fanli.sakurazakatranslator.domain.translation.TranslationResult;
import com.fanli.sakurazakatranslator.domain.translation.TranslationValidator;

/** 纯 Java 自测入口：后续接入 Gradle/JUnit 前可直接 javac 后运行。 */
public final class CoreModulesSelfTest {
    private static int checks;
    public static void main(String[] args) {
        SymbolProtector.ProtectedText p = SymbolProtector.protect("今日は楽しかった～♡\nまたね💗");
        require(SymbolProtector.validate(p, "今天很开心～♡\n下次见💗").isValid(), "符号应保留");
        require(!SymbolProtector.validate(SymbolProtector.protect("またね💗💗"), "下次见💗").isValid(),
                "两个爱心被删成一个必须校验失败");
        require(!SymbolProtector.validate(SymbolProtector.protect("～♡"), "♡～").isValid(), "符号换序应失败");
        require(!SymbolProtector.validate(SymbolProtector.protect("またね"), "再见💗").isValid(), "模型不可添加爱心");
        require(!SymbolProtector.validate(SymbolProtector.protect("👩🏽‍💻🇯🇵1️⃣"), "👩‍💻🇯🇵1️⃣").isValid(), "肤色变化应失败");
        require(SymbolProtector.validate(SymbolProtector.protect("👩🏽‍💻🇯🇵1️⃣"), "👩🏽‍💻🇯🇵1️⃣").isValid(), "组合 Emoji 原样通过");
        require(!SymbolProtector.validate(SymbolProtector.protect("a\nb\n"), "甲\n乙").isValid(), "换行减少应失败");
        require(SymbolProtector.validate(SymbolProtector.protect("a\r\nb"), "甲\n乙").isValid(), "跨平台换行等价");
        require(!SymbolProtector.validate(SymbolProtector.protect("1️⃣"), "2️⃣").isValid(), "键帽数字不能改变");
        require(!SymbolProtector.validate(SymbolProtector.protect(""), null).isValid(), "null 不当作成功的空译文");
        expectThrows(IllegalArgumentException.class, () -> SymbolProtector.protect("原文", List.of("")), "拒绝空保护片段");
        expectThrows(IllegalArgumentException.class, () -> SymbolProtector.protect("原文", List.of("不存在")), "保护片段必须出自原文");
        require(!SymbolProtector.validate(SymbolProtector.protect("またね (笑)", List.of("(笑)")), "再见 (哭)").isValid(),
                "显式标注的颜文字不得改写");
        messageAssembly();
        resultValidation();
        System.out.println("CoreModulesSelfTest: PASS (" + checks + " checks)");
    }

    private static final Bounds SCREEN = new Bounds(0, 0, 1080, 2340);
    private static final PageToken PAGE = new PageToken("example.target", 1, 2, 100);

    private static TextFragment fragment(String id, String text, int top, int traversal,
                                         TextFragment.Role role, boolean selected) {
        return new TextFragment(id, text, TextFragment.Source.NODE_TEXT, role,
                new Bounds(10, top, 300, top + 10), traversal, -1, selected);
    }

    private static ChatMessage message(List<TextFragment> fragments, boolean confirmed) {
        return new ChatMessage(PAGE, "m1", "idol-a", "测试人物", "", SCREEN,
                ChatMessage.MediaType.TEXT, fragments, List.of(), -1, confirmed);
    }

    private static void messageAssembly() {
        TextFragment first = fragment("n2", "第一行", 20, 2, TextFragment.Role.BODY, true);
        TextFragment second = fragment("n10", "第二行", 40, 10, TextFragment.Role.BODY, true);
        var input = new ArrayList<>(List.of(second, first,
                fragment("button", "返回", 0, 0, TextFragment.Role.CONTROL, true),
                fragment("unknown", "登录提示", 5, 1, TextFragment.Role.UNKNOWN, true),
                fragment("unchecked", "未选择", 60, 11, TextFragment.Role.BODY, false), first));
        ChatMessage snapshot = message(input, true);
        input.clear();
        require("第一行\n第二行".equals(MessageAssembler.assemble(snapshot).sourceText()), "过滤控件并按屏幕顺序合并");
        expectThrows(UnsupportedOperationException.class, () -> snapshot.fragments().clear(), "消息不可被外部修改");
        require("同文\n同文".equals(MessageAssembler.assemble(message(List.of(
                fragment("a", "同文", 20, 1, TextFragment.Role.BODY, true),
                fragment("b", "同文", 40, 2, TextFragment.Role.BODY, true)), true)).sourceText()), "不同位置同文不能去重");
        require("先\n后".equals(MessageAssembler.assemble(message(List.of(
                fragment("n10", "后", 20, 10, TextFragment.Role.BODY, true),
                fragment("n2", "先", 20, 2, TextFragment.Role.BODY, true)), true)).sourceText()), "同坐标按数字遍历序号排序");
        expectThrows(IllegalStateException.class, () -> MessageAssembler.assemble(message(List.of(first), false)), "不猜消息边界");
        expectThrows(IllegalArgumentException.class, () -> MessageAssembler.assemble(message(List.of(first,
                fragment("n2", "冲突文本", 20, 2, TextFragment.Role.BODY, true)), true)), "重复 ID 冲突拒绝合并");
        expectThrows(IllegalArgumentException.class, () -> new Bounds(10, 10, 0, 0), "反向边界非法");
        expectThrows(IllegalArgumentException.class, () -> new TextFragment("x", "x", TextFragment.Source.OCR,
                TextFragment.Role.BODY, SCREEN, 0, Float.NaN, true), "NaN 不是置信度");
        require(!PAGE.equals(new PageToken("example.target", 1, 3, 100)), "切页代次改变页面身份");
        require(!PAGE.equals(new PageToken("example.target", 1, 2, 101)), "重新截图改变页面身份");
        require(MessageAssembler.assemble(message(List.of(
                fragment("time", "00:25", 20, 1, TextFragment.Role.METADATA, true)), true)).sourceText().isEmpty(), "时长不能翻译成正文");
        require(MessageAssembler.assemble(message(List.of(), true)).sourceText().isEmpty(), "空消息没有正文");
        var outside = fragment("outside", "导航", 2400, 1, TextFragment.Role.BODY, true);
        require(MessageAssembler.assemble(message(List.of(outside), true)).sourceText().isEmpty(), "消息范围外文字排除");
        require("  原文～\n💗  ".equals(MessageAssembler.assemble(message(List.of(
                fragment("raw", "  原文～\n💗  ", 20, 1, TextFragment.Role.BODY, true)), true)).sourceText()), "原文内部空白不改写");
    }

    private static void resultValidation() {
        StyleProfile style = new StyleProfile("idol-a", "1", "测试人物", "温柔的中文说明",
                List.of("忠于原文"), List.of("Blog 较正式"), List.of("MSG 较口语"),
                java.util.Map.of(), List.of());
        TranslationRequest request = new TranslationRequest("req-1", PAGE, "m1", "idol-a", "またね～💗", style, List.of());
        TranslationResult good = result(PAGE, "req-1", "m1", "1", "下次见～💗");
        require(TranslationValidator.validate(request, good).isValid(), "正确结果通过");
        require(!TranslationValidator.validate(request, result(new PageToken("example.target", 1, 3, 200),
                "req-1", "m1", "1", "下次见～💗")).isValid(), "旧页结果拒绝");
        require(!TranslationValidator.validate(request, result(PAGE, "req-2", "m1", "1", "下次见～💗")).isValid(), "旧请求拒绝");
        require(!TranslationValidator.validate(request, result(PAGE, "req-1", "m2", "1", "下次见～💗")).isValid(), "消息串位拒绝");
        require(!TranslationValidator.validate(request, result(PAGE, "req-1", "m1", "2", "下次见～💗")).isValid(), "风格版本不符拒绝");
        require(!TranslationValidator.validate(request, result(PAGE, "req-1", "m1", "1", "下次见～")).isValid(), "丢符号拒绝");
        require(!TranslationValidator.validate(request, result(PAGE, "req-1", "m1", "1", "  ")).isValid(), "空译文拒绝");
        require(!TranslationValidator.validate(request, null).isValid(), "无响应拒绝");
        require(!TranslationValidator.validate(request, new TranslationResult("req-1", PAGE, "m1", "下次见～💗",
                "test-only", "fixture", "idol-a", "1", true, 1000)).isValid(), "提供商待确认标志不得忽略");
        require("zh-Hans".equals(request.targetLanguage()), "目标固定简体中文");
        expectThrows(UnsupportedOperationException.class, () -> style.msgGuidance().clear(), "风格列表不可修改");
        expectThrows(IllegalArgumentException.class, () -> new TranslationRequest("r", PAGE, "m", "idol-b", "原文", style, List.of()), "人物不能串档案");
    }

    private static TranslationResult result(PageToken page, String requestId, String messageId, String version, String text) {
        return new TranslationResult(requestId, page, messageId, text, "test-only", "fixture", "idol-a", version, false, 1000);
    }

    private static void expectThrows(Class<? extends Throwable> type, Runnable action, String message) {
        try { action.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) { checks++; return; }
            throw new AssertionError(message, error);
        }
        throw new AssertionError(message);
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
}
