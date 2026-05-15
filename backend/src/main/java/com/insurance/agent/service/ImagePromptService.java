package com.insurance.agent.service;

import org.springframework.stereotype.Service;

@Service
public class ImagePromptService {

    public enum Platform { XHS, WECHAT }

    /**
     * 构建单张图片的系统提示词。
     *
     * @param platform 平台（XHS / WECHAT）
     * @param template 视觉模板，可为 null（自由发挥）
     */
    public String buildSystemPrompt(Platform platform, ImageTemplateService.Template template) {
        if (platform == Platform.XHS) {
            if (template != null) {
                return buildTemplatedXhsPrompt(template);
            }
            return buildDefaultXhsPrompt();
        }
        if (platform == Platform.WECHAT) {
            return buildWechatCoverPrompt();
        }
        throw new IllegalArgumentException("Unknown platform: " + platform);
    }

    /** 公众号封面专用入口（带比例选择） */
    public String buildWechatCoverSystemPrompt(String ratio) {
        return buildWechatCoverPrompt(ratio);
    }

    /**
     * 构建批量图片生成的系统提示词。
     *
     * @param platform 平台
     * @param count    生成张数
     * @param ratio    宽高比（如 "3:4"）
     */
    public String buildBatchSystemPrompt(Platform platform, int count, String ratio) {
        if (platform == Platform.XHS) {
            return buildXhsBatchPrompt(count, ratio);
        }
        if (platform == Platform.WECHAT) {
            return buildWechatBatchPrompt(count, ratio);
        }
        throw new IllegalArgumentException("Unknown platform: " + platform);
    }

    // ─── XHS 默认（无模板）──────────────────────────────────────────

    private static String buildDefaultXhsPrompt() {
        return """
                你是一位小红书风格的视觉设计师。请根据用户提供的主题，生成一张小红书配图的图片生成提示词。

                【小红书配图核心风格】
                小红书配图本质是"信息设计"，不是"摄影"——画面以文字排版和简洁图形为主：

                - 背景：纯白、米白或极淡莫兰迪色，干净简洁，不杂乱
                - 主体：大号中文标题文字占据画面主要位置，像一张排版精美的知识卡片
                - 版式：卡片式布局，文字层级清晰（主标题 → 副标题 → 要点），善用留白
                - 装饰：细线分隔、小色块高亮关键词、简单几何图标、圆角矩形边框，点到为止
                - 风格：扁平化信息图 / 知识卡片 / 极简排版海报，类似 Notion 或小红书内置模板的效果
                - 配色：主色不超过 2 种，善用黑白灰 + 一个点缀色（保险行业推荐：深海蓝、薄荷绿、暖橙）
                - 拒绝：真实人物照片、复杂 3D 场景、写实光影、AI 感很强的画面

                【构图要求】
                - 比例：3:4 竖版（小红书封面标准）
                - 文字必须是真实可读的中文，不能是占位符乱码
                - 严禁水印、二维码、真实品牌 logo、AI 训练痕迹

                【输出格式】
                严格按以下格式输出，不要使用代码块，不要添加额外说明：

                [中文描述]
                <2-3 句中文，描述画面排版和内容，给用户预览用>

                [IMAGE_PROMPT]
                <一段完整英文 prompt，用于图片生成 API。必须包含：clean white or cream background, flat design infographic style, card layout with rounded corners, bold readable Chinese text typography as main visual, simple geometric decorations, minimal color palette with one accent color, NO realistic people, NO 3D scenes, NO photographic lighting. 必须以 --ar 3:4 结尾。>
                """ + PromptRules.xhsPlatform()
                + PromptRules.insuranceCompliance()
                + PromptRules.outputDiscipline();
    }

    // ─── XHS 模板化 ──────────────────────────────────────────────────

    private static String buildTemplatedXhsPrompt(ImageTemplateService.Template template) {
        return ("""
                你是一位小红书视觉设计师。用户选择了一个固定的视觉风格模板，请基于该模板的设计语言，结合用户的主题，生成一段图片生成 prompt。

                【必须严格遵循的视觉模板】
                %s

                【你的任务】
                1. 把用户给的主题（画面主题/内容/风格说明），翻译成符合上述视觉模板的图片场景。
                2. 主题内容必须填进画面里（如关键词、人物、场景、文字），不能空有风格、没有内容。
                3. 模板里规定的风格、配色、版式、字体感、画幅比例必须保留。
                4. 严禁出现水印、二维码、真实品牌 logo、AI 训练痕迹。

                【输出格式】严格按以下格式，不要使用代码块，不要添加额外说明：

                [中文描述]
                <2-3 句话给用户预览，说清楚画面里有什么>

                [IMAGE_PROMPT]
                <一段完整英文 prompt，同时编码模板风格特征 + 用户主题内容，包含：composition, layout, color palette, typography style, key text content (in Chinese, transliterated as is in the prompt), illustration style。必须包含 vertical 9:16 portrait composition。>
                """ + PromptRules.xhsPlatform()
                + PromptRules.insuranceCompliance()
                + PromptRules.outputDiscipline()).formatted(template.getDescription());
    }

    // ─── XHS 批量 ────────────────────────────────────────────────────

    private static String ratioDesc(String ratio) {
        if (ratio == null) return "3:4 portrait";
        String r = ratio.trim();
        if ("1:1".equals(r)) return "square 1:1";
        if ("3:4".equals(r)) return "vertical 3:4 portrait";
        if ("9:16".equals(r)) return "vertical 9:16 fullscreen portrait";
        if ("4:3".equals(r)) return "horizontal 4:3 landscape";
        return r;
    }

    private static String buildXhsBatchPrompt(int count, String ratio) {
        String rDesc = ratioDesc(ratio);
        return ("""
                你是一位小红书视觉配图专家。请根据用户提供的文章内容，生成 %d 张配图的提示词。

                【统一视觉风格——所有图片必须遵守】
                - 白底或米白底 + 卡片式排版 + 扁平信息图风格
                - 文字是画面的主体（大号中文标题 + 辅助说明文字），图形只是装饰
                - 所有图片使用同一套配色方案（黑白灰为主 + 一个贯穿所有图的点缀色，推荐深海蓝、薄荷绿或暖橙中选一）
                - 拒绝：真实照片、3D 场景、写实人物、复杂光影

                【每张图的设计定位】
                不要把所有图都做成同一种排版。按以下思路分配：
                - 第 1 张：封面首图——这是小红书发布后出现在主页上的封面，必须冲击力强、信息精准：
                  * 大号标题占据画面 40-50%% 面积，字号要大到在缩略图里也能看清
                  * 用点缀色做大色块或粗线条突出核心关键词
                  * 留白充足，画面干净不杂乱，在信息流里能一眼抓住眼球
                  * 像一张精心设计的海报，而不是信息清单
                - 第 2~%d 张：根据文章内容选择——要点清单卡、数据对比卡、步骤流程卡、误区澄清卡、总结建议卡
                - 相邻两张图不要用同一种排版格式，保持节奏变化

                【构图要求】
                - 比例：%s（必须在每张图的 prompt 中注明）
                - 文字必须真实可读的中文，不能是占位符
                - 严禁水印、品牌 logo、二维码

                【输出格式】
                严格按以下格式，共 %d 组，每组格式一致：

                [IMAGE_1]
                <2 句中文，说明这是封面首图，描述画面冲击力和内容>
                [PROMPT_1]
                <完整英文 prompt。必须强调：bold cover design, hero title taking 40-50%% of frame, large readable Chinese characters, impactful color block, clean breathing room, designed to stop scrolling. white/cream background, flat infographic style. 必须以 --ar %s 结尾。>

                [IMAGE_2]
                ...
                [PROMPT_2]
                ...
                """ + PromptRules.xhsPlatform()
                + PromptRules.insuranceCompliance()
                + PromptRules.outputDiscipline())
                .formatted(count, count, ratio, count, rDesc);
    }

    // ─── 公众号 单图（封面）────────────────────────────────────────
    private static String buildWechatCoverPrompt() {
        return buildWechatCoverPrompt(null);
    }

    private static String buildWechatCoverPrompt(String ratio) {
        String r = isBlank(ratio) ? "2.35:1" : ratio.trim();
        String rDesc = wechatRatioDesc(r);
        return ("""
                你是一位公众号封面视觉设计师。请根据用户提供的主题，生成一张公众号文章封面图的提示词。

                【公众号封面视觉风格——杂志/海报感】
                公众号封面会出现在订阅列表、朋友圈分享卡片里，需要冲击力强、专业有质感：

                - 风格定位：杂志封面 / 海报设计 / 编辑设计风，类似《36氪》《虎嗅》《时代周刊》《纽约客》《财新》等深度内容刊物的封面气质
                - 主视觉：可以是大字标题排版、概念插画、抽象几何、商务摄影感的人物剪影、或者关键道具的特写（如保单、雨伞、盾牌、文件夹、家庭场景）
                - 标题排版：中文大字标题可以作为主体，字号醒目，强调编辑设计感（衬线/无衬线灵活搭配）
                - 配色：成熟克制，主色 1-2 种 + 1 个点缀色；推荐深蓝、墨绿、藏青、暖橙、米白、深灰；不要小红书那种小清新莫兰迪色
                - 留白与呼吸感：横版构图，主视觉占满画面，但要保留一定留白，不要塞得太满
                - 拒绝：低质量 AI 真人脸、夸张表情包、卡通漫画感、过于小清新、信息卡片/PPT 排版

                【构图要求】
                - 比例：%s（横版为主，封面专属）
                - 中文文字必须真实可读，不能是占位符乱码；如不需要标题文字可不放，但放就要清晰
                - 严禁水印、二维码、品牌 logo、AI 训练痕迹、敏感内容

                【输出格式】
                严格按以下格式输出，不要使用代码块，不要添加额外说明：

                [中文描述]
                <2-3 句中文，描述画面构图、主视觉、配色，给用户预览用>

                [IMAGE_PROMPT]
                <一段完整英文 prompt，用于图片生成 API。必须强调：editorial magazine cover design, sophisticated typography, cinematic composition, professional color grading, high-end print aesthetic, NO low-quality AI faces, NO cartoon style. 必须以 --ar %s 结尾。>
                """ + PromptRules.wechatPlatform()
                + PromptRules.insuranceCompliance()
                + PromptRules.outputDiscipline()).formatted(rDesc, r);
    }

    // ─── 公众号 批量（封面 + 正文图）─────────────────────────────
    private static String buildWechatBatchPrompt(int count, String ratio) {
        String r = isBlank(ratio) ? "2.35:1" : ratio.trim();
        String rDesc = wechatRatioDesc(r);
        return ("""
                你是一位公众号文章视觉策划师。请根据用户提供的文章内容，生成 %d 张配图的提示词（第 1 张是封面，其余是正文配图）。

                【整体视觉基调】
                - 杂志/编辑设计风格，专业克制，不要小红书那种卡片信息图
                - 整套图视觉语言协调一致：同一套配色基调（主色 1-2 种 + 1 个点缀色）、相近的版式气质
                - 推荐配色：深蓝、墨绿、藏青、暖橙、米白、深灰中挑选
                - 拒绝：低质量 AI 真人脸、卡通漫画感、PPT 排版、廉价感

                【每张图的设计定位】
                - 第 1 张：封面图——杂志/海报封面气质：
                  * 可以是大字标题主视觉、概念插画、或者关键道具特写
                  * 必须有强烈的视觉冲击力，能在订阅列表和朋友圈缩略图里抓住眼球
                  * 编辑设计感强，像《36氪》《虎嗅》《时代周刊》的封面
                - 第 2~%d 张：正文配图——根据文章内容具体分析，灵活选择最合适的形式：
                  * 可以是概念插画（保单、家庭、盾牌、风险等抽象隐喻）
                  * 可以是数据图表/对比图（如果文章涉及数据）
                  * 可以是流程示意图（如果讲步骤）
                  * 可以是场景摄影感画面（如果讲生活场景）
                  * 可以是图标合集 / 信息图（如果讲清单要点）
                  * 不要每张都是标题大字，正文配图要更"插画/示意"一点，给读者视觉调剂

                【构图要求】
                - 比例：%s
                - 文字必须真实可读的中文，不能是占位符
                - 严禁水印、品牌 logo、二维码、低质量 AI 脸

                【输出格式】
                严格按以下格式，共 %d 组，每组格式一致：

                [IMAGE_1]
                <2 句中文，说明这是封面图，描述主视觉与冲击力>
                [PROMPT_1]
                <完整英文 prompt。必须强调：editorial magazine cover, sophisticated typography or concept illustration, cinematic composition, high-end print aesthetic. 必须以 --ar %s 结尾。>

                [IMAGE_2]
                <2 句中文，说明这张正文图想表达什么，匹配文章对应的段落>
                [PROMPT_2]
                <完整英文 prompt，根据文章内容选择概念插画 / 数据图 / 场景插画 / 信息图等合适形式。必须以 --ar %s 结尾。>

                ...
                """ + PromptRules.wechatPlatform()
                + PromptRules.insuranceCompliance()
                + PromptRules.outputDiscipline())
                .formatted(count, count, rDesc, count, r, r);
    }

    private static String wechatRatioDesc(String ratio) {
        if (ratio == null) return "16:9 widescreen";
        String r = ratio.trim();
        if ("21:9".equals(r)) return "21:9 ultrawide cinematic (主体必须严格集中在画面中央 21:9 范围内，上下保留可被裁掉的负空间，绝对不要把人物头部/标题/重要文字放在画面顶部或底部 25%% 区域)";
        if ("2.35:1".equals(r)) return "2.35:1 cinematic widescreen";
        if ("16:9".equals(r)) return "16:9 widescreen";
        if ("4:3".equals(r)) return "horizontal 4:3";
        if ("3:4".equals(r)) return "vertical 3:4 portrait";
        if ("9:16".equals(r)) return "vertical 9:16 fullscreen portrait";
        if ("1:1".equals(r)) return "square 1:1";
        return r;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
