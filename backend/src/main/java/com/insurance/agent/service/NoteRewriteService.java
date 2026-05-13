package com.insurance.agent.service;

import com.insurance.agent.dto.RewriteRequest;
import com.insurance.agent.dto.RewriteResponse;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NoteRewriteService {

    public static final String MODE_COPY = "copy";
    public static final String MODE_SYNONYM = "synonym";
    public static final String MODE_STYLE_HEALING = "style_healing";
    public static final String MODE_STYLE_DRYGOODS = "style_drygoods";
    public static final String MODE_STYLE_COMPLAINT = "style_complaint";
    public static final String MODE_REWRITE = "rewrite";
    public static final String MODE_MP = "mp_style";
    // 公众号专属模式
    public static final String MODE_MP_TO_XHS = "mp_to_xhs";
    public static final String MODE_MP_TO_SHORT = "mp_to_short";
    public static final String MODE_MP_SUMMARY = "mp_summary";
    public static final String MODE_MP_REWRITE = "mp_rewrite";
    public static final String MODE_MP_OPTIMIZE = "mp_optimize";

    private static final Map<String, String> MODE_LABELS = new LinkedHashMap<>();
    static {
        MODE_LABELS.put(MODE_COPY, "复制原文案");
        MODE_LABELS.put(MODE_SYNONYM, "同义改写");
        MODE_LABELS.put(MODE_STYLE_HEALING, "风格转换 - 治愈风");
        MODE_LABELS.put(MODE_STYLE_DRYGOODS, "风格转换 - 干货风");
        MODE_LABELS.put(MODE_STYLE_COMPLAINT, "风格转换 - 吐槽风");
        MODE_LABELS.put(MODE_REWRITE, "完全重写(保留核心观点)");
        MODE_LABELS.put(MODE_MP, "转公众号风格(扩写)");
        // 公众号专属模式
        MODE_LABELS.put(MODE_MP_TO_XHS, "公众号转小红书");
        MODE_LABELS.put(MODE_MP_TO_SHORT, "公众号转短文案/朋友圈");
        MODE_LABELS.put(MODE_MP_SUMMARY, "公众号摘要/大纲提取");
        MODE_LABELS.put(MODE_MP_REWRITE, "公众号深度改写");
        MODE_LABELS.put(MODE_MP_OPTIMIZE, "公众号标题+开头优化");
    }

    private static final Pattern XHS_TOPIC = Pattern.compile("\\u200b?#([^#\\[\\]\\n]+?)\\[话题\\]#\\u200b?");

    private final DeepSeekService deepSeek;

    public NoteRewriteService(DeepSeekService deepSeek) {
        this.deepSeek = deepSeek;
    }

    public Map<String, String> listModes() {
        return MODE_LABELS;
    }

    public RewriteResponse rewrite(RewriteRequest req) {
        String mode = (req.getMode() == null || req.getMode().isBlank())
                ? MODE_COPY
                : req.getMode().trim().toLowerCase();
        if (!MODE_LABELS.containsKey(mode)) mode = MODE_COPY;

        String originalTitle = nullSafe(req.getOriginalTitle());
        String originalContent = nullSafe(req.getOriginalContent());

        if (MODE_COPY.equals(mode)) {
            return new RewriteResponse(
                    originalTitle,
                    cleanXhsTopics(originalContent),
                    mode,
                    "原文(未调用 LLM)");
        }

        String system = buildSystemPrompt(mode, req.getRequirements());
        String user = buildUserPrompt(originalTitle, cleanXhsTopics(originalContent));
        String llmOutput = deepSeek.chat(system, user, req.getModel());
        ParsedOutput parsed = parseLlmOutput(llmOutput, mode);
        return new RewriteResponse(
                parsed.title.isBlank() ? originalTitle : parsed.title,
                parsed.content,
                mode,
                deepSeek.resolveModel(req.getModel()));
    }

    private String buildSystemPrompt(String mode, String userRequirements) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位资深的小红书 / 公众号内容改写助手, 专注于保险科普内容。\n\n");
        sb.append("【任务】\n");
        switch (mode) {
            case MODE_SYNONYM -> sb.append("""
                    保持原文核心观点、信息点和段落结构, 只在句子层面改写表达方式。
                    要求:
                    1. 所有数字、产品名(如百万医疗、重疾险等)、专业术语必须保留;
                    2. 段落结构、列表编号、emoji 风格保持一致;
                    3. 改写后字数与原文相当(±20%)。
                    """);
            case MODE_STYLE_HEALING -> sb.append("""
                    把原文改写成"治愈温暖"的语气: 多用共情句、第二人称"你"、柔软的措辞。
                    去掉激烈情绪词(避雷/坑/打脸), 换成更温柔的表达(可以注意/留个心眼)。
                    保留所有核心信息和数字。
                    """);
            case MODE_STYLE_DRYGOODS -> sb.append("""
                    把原文改写成"干货专业"的语气: 减少 emoji, 强化数字/规则/对比, 多用要点列表。
                    口吻要像保险经纪人, 客观、克制、有信息密度。保留所有原文信息点。
                    """);
            case MODE_STYLE_COMPLAINT -> sb.append("""
                    把原文改写成"吐槽调侃"的语气: 加强反讽, 多用"我直接好家伙"" 拜托了"" 谁懂啊"这类网感口语。
                    内容信息保留, 但语气更轻松、更带刺。
                    """);
            case MODE_REWRITE -> sb.append("""
                    只保留原文的核心观点和关键信息, 完全重新组织语言、结构、举例。
                    要求:
                    1. 不要照抄原文任何句子;
                    2. 字数 400-700 字;
                    3. 至少包含 3 个清晰的小节(可用 emoji 或编号区分);
                    4. 末尾追加 5-8 个相关话题标签, 用 # 开头。
                    """);
            case MODE_MP -> sb.append("""
                    把原文改写成"微信公众号"风格(更长, 更书面化):
                    1. 字数 1200-1800 字;
                    2. 结构: 引言 → 痛点铺陈 → 核心要点(分小节标题) → 实操建议 → 结语;
                    3. 段落比小红书更长, 但仍要分段, 不要大段堆砌;
                    4. 减少小红书风的 emoji 堆砌, 但可保留少量, 增加书面感;
                    5. 标题改写成公众号风的标题(更正式, 钩子更克制);
                    6. 末尾不需要话题标签。
                    """);
            case MODE_MP_TO_XHS -> sb.append("""
                    把公众号文章压缩改写成"小红书"风格:
                    1. 字数 600-900 字;
                    2. 结构: 强钩子开头 → 分点干货(每点有 emoji) → 互动收尾;
                    3. 语气亲切, 用第二人称"你", 多用口语化表达;
                    4. 加入适当的 emoji, 让内容更活泼;
                    5. 标题改造成小红书爆款风格(带数字/痛点/反问);
                    6. 末尾加 5-8 个相关话题标签。
                    """);
            case MODE_MP_TO_SHORT -> sb.append("""
                    把公众号文章压缩改写成"短文案/朋友圈"风格:
                    1. 字数 150-300 字;
                    2. 提取最核心的 1-3 个观点, 去掉所有细节和铺垫;
                    3. 口语化, 适合朋友圈阅读;
                    4. 标题要醒目, 可以用 emoji;
                    5. 可选: 末尾加 2-3 个话题标签。
                    """);
            case MODE_MP_SUMMARY -> sb.append("""
                    把公众号文章整理成"摘要/大纲":
                    1. 输出两部分: [核心摘要] 和 [详细大纲];
                    2. 核心摘要: 100-200 字, 一句话概括全文;
                    3. 详细大纲: 用 bullet point 列出全文的结构和核心观点;
                    4. 保留所有关键数据和专业术语;
                    5. 不要丢失任何重要信息点。
                    """);
            case MODE_MP_REWRITE -> sb.append("""
                    深度改写公众号文章:
                    1. 保留原文所有核心观点、数据、案例;
                    2. 完全重新组织语言结构和表达顺序;
                    3. 字数与原文相当(±20%);
                    4. 可以调整段落顺序, 但逻辑要连贯;
                    5. 确保改写后不改变原意, 但表达完全不同。
                    """);
            case MODE_MP_OPTIMIZE -> sb.append("""
                    优化公众号的"标题"和"开头":
                    1. 标题: 提供 3 个不同风格的候选标题(数字型/痛点型/反问型);
                    2. 开头: 重写前 200 字, 做成强钩子开头;
                    3. 标题要抓眼球, 开头要有吸引力;
                    4. 保留原文核心主题不变;
                    5. 标题每个控制在 25-30 字以内。
                    
                    【输出格式】
                    [标题]
                    <标题1>
                    <标题2>
                    <标题3>

                    [开头]
                    <优化后的开头>
                    """);
            default -> sb.append("保持原文不变, 输出原文。\n");
        }

        if (userRequirements != null && !userRequirements.isBlank()
                && !"不改变原始文案".equals(userRequirements.trim())) {
            sb.append("\n【用户附加要求】\n").append(userRequirements.trim()).append("\n");
        }

        sb.append(PromptRules.rewriteDiscipline());
        sb.append(PromptRules.factuality());
        sb.append(PromptRules.insuranceCompliance());
        if (MODE_MP.equals(mode) || MODE_MP_REWRITE.equals(mode) || MODE_MP_OPTIMIZE.equals(mode)
                || MODE_MP_SUMMARY.equals(mode)) {
            sb.append(PromptRules.wechatPlatform());
        } else if (MODE_MP_TO_XHS.equals(mode) || MODE_REWRITE.equals(mode)
                || MODE_STYLE_HEALING.equals(mode) || MODE_STYLE_DRYGOODS.equals(mode)
                || MODE_STYLE_COMPLAINT.equals(mode)) {
            sb.append(PromptRules.xhsPlatform());
        }

        sb.append("""

                【输出格式】严格按以下两段输出, 不要任何前后说明:
                [标题]
                <改写后的标题>

                [正文]
                <改写后的正文(可使用 markdown)>
                """);
        sb.append(PromptRules.outputDiscipline());
        return sb.toString();
    }

    private String buildUserPrompt(String originalTitle, String originalContent) {
        StringBuilder sb = new StringBuilder();
        if (originalTitle != null && !originalTitle.isBlank()) {
            sb.append("【原标题】\n").append(originalTitle).append("\n\n");
        }
        sb.append("【原正文】\n").append(originalContent);
        return sb.toString();
    }

    private ParsedOutput parseLlmOutput(String output, String mode) {
        ParsedOutput p = new ParsedOutput();
        if (output == null) {
            p.title = "";
            p.content = "";
            return p;
        }
        String text = output.trim();
        
        // 处理公众号标题优化模式（有多个标题和单独的开头）
        if (MODE_MP_OPTIMIZE.equals(mode)) {
            int titleIdx = text.indexOf("[标题]");
            int bodyIdx = text.indexOf("[开头]");
            if (titleIdx >= 0 && bodyIdx > titleIdx) {
                String titlesPart = text.substring(titleIdx + "[标题]".length(), bodyIdx).trim();
                String openingPart = text.substring(bodyIdx + "[开头]".length()).trim();
                // 标题用多个换行分隔，取第一行作为主标题
                String firstTitle = titlesPart.split("\n")[0].trim();
                p.title = firstTitle;
                p.content = "【标题候选】\n" + titlesPart + "\n\n【优化开头】\n" + openingPart;
            } else {
                p.title = "";
                p.content = text;
            }
            return p;
        }
        
        // 处理摘要模式
        if (MODE_MP_SUMMARY.equals(mode)) {
            p.title = "摘要";
            p.content = text;
            return p;
        }
        
        // 普通模式（单个标题 + 正文）
        int titleIdx = text.indexOf("[标题]");
        int bodyIdx = text.indexOf("[正文]");
        if (titleIdx >= 0 && bodyIdx > titleIdx) {
            p.title = text.substring(titleIdx + "[标题]".length(), bodyIdx).trim();
            p.content = text.substring(bodyIdx + "[正文]".length()).trim();
        } else {
            p.title = "";
            p.content = text;
        }
        return p;
    }
    
    private ParsedOutput parseLlmOutput(String output) {
        // 兼容旧调用方式
        return parseLlmOutput(output, null);
    }

    private String cleanXhsTopics(String content) {
        if (content == null) return "";
        Matcher m = XHS_TOPIC.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("#" + m.group(1)));
        }
        m.appendTail(sb);
        return sb.toString().replaceAll("[\\u200b\\ufeff]", "").trim();
    }

    private String nullSafe(String s) { return s == null ? "" : s; }

    private static class ParsedOutput {
        String title;
        String content;
    }
}
