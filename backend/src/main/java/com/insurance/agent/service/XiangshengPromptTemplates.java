package com.insurance.agent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 相声剧本创作 prompt 模板（极简版）。
 * 前端只保留语气风格一个可选维度。
 */
public final class XiangshengPromptTemplates {

    private XiangshengPromptTemplates() {}

    /** 语气风格（前端唯一保留的选项） */
    private static final Map<String, String> TONE_STYLES;

    static {
        TONE_STYLES = new LinkedHashMap<>();
        TONE_STYLES.put("通用", "标准普通话，职业病开场，理性幽默");
        TONE_STYLES.put("京片子", "胡同语气，儿化音，接地气的胡同比喻");
        TONE_STYLES.put("东北话", "豪爽幽默，天然笑点，实在接地气");
        TONE_STYLES.put("上海话", "吴侬软语，精明算计，市井比喻");
        TONE_STYLES.put("港剧腔", "TVB腔调，粤语，精英感十足");
        TONE_STYLES.put("学术冷幽默", "数据反讽，博弈论视角，冷静吐槽");
    }

    public static List<Map<String, Object>> dimensionOptions() {
        List<Map<String, Object>> dims = new java.util.ArrayList<>();
        Map<String, Object> dim = new LinkedHashMap<>();
        dim.put("key", "toneStyle");
        dim.put("label", "语气风格");
        List<Map<String, String>> items = new java.util.ArrayList<>();
        for (var entry : TONE_STYLES.entrySet()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("value", entry.getKey());
            item.put("desc", entry.getValue());
            items.add(item);
        }
        dim.put("options", items);
        dims.add(dim);
        return dims;
    }

    // ───────────────────────── 阶段一：台词创作 ─────────────────────────

    public static String stage1System(String hookType, String structure, String emotionArc,
                                       String audience, String topicDirection, String toneStyle,
                                       Integer duration) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                你写一段双人相声切片，主题是保险。考核标准：观众看完能复述出选题里的保险观点，且觉得好笑。

                A：主讲，懂保险，会自嘲。
                B：捧哏，装傻、打岔、误解、抢白，围绕保险本身抖包袱。

                【时长字数】90-150 秒，A+B 合计约 500-1000 字。对话要密、A/B 切换要勤，平均 1-2 句一换。

                【硬约束】
                1. 整篇出现保险相关词（保险/保单/保障/理赔/投保/险种/保费 等）≥ 10 次。
                2. 不允许连续 4 句完全不沾保险，前 4 句内必须引入保险。
                3. 一旦说"我比方/我举例/那我 XX"，下一两句必须真做，不许跳话题。
                4. 结尾不要总结、不要点题、不要升华、不要"所以/致敬/请珍惜"。最后一句本身就是一个包袱（回扣或反差）。
                5. 少用破折号和省略号，全篇加起来不超过 3 处。

                【输出格式】直接输出，每行一句：
                A：……
                B：……

                最后一行：预估时长：约 X 分 X 秒
                """);

        if (toneStyle != null && !toneStyle.isEmpty() && !"通用".equals(toneStyle)) {
            sb.append("\n【语气】").append(toneStyle).append("：").append(toneStyleHint(toneStyle)).append("\n");
        }

        return sb.toString();
    }

    private static String toneStyleHint(String style) {
        return switch (style) {
            case "京片子" -> "胡同腔，儿化音多，比喻接地气，常用：您、咱、得嘞、甭、局气。";
            case "上海话" -> "上海话味儿，精明算账，市井比喻，常用：侬、阿拉、勿、迭个。";
            case "东北话" -> "东北腔，自带笑点，常用：整、唠嗑、咋整、寻思寻思。";
            case "港剧腔" -> "TVB 腔，长短句结合，常用：咩、嘅、咁、唔该、搞掂。";
            case "学术冷幽默" -> "冷静吐槽，引博弈论/概率论但讲得像人话，数据具体。";
            default -> "标准普通话，理性幽默。";
        };
    }

    public static String stage1User(String topic) {
        return "选题：%s\n\n围绕这个选题写一段相声切片，让观众笑完能记住选题里的保险观点。".formatted(topic);
    }

    // ───────────────────────── 阶段二：Seedance 分镜剧本 ─────────────────────────

    public static String stage2System(Integer duration) {
        return """
                把用户给的相声台词转成 Seedance 2.0 分镜剧本。直接输出，无开场白，不提字幕。

                【规则】
                - 默认全程中景（双人入框，A 左 B 右），不要刻意安排特写。
                - 镜头时长按 5-7 字/秒匹配该镜头内台词量，台词少就切短。
                - 每 12-15 秒一组，最后一组可缩至 10 秒。
                - 画面比例 9:16 竖屏，模型 Seedance2.0。

                【每个镜头格式】
                镜头X｜Xs-Xs秒｜中景

                台词
                A：[台词]
                B：[台词]

                演员A 状态
                - 表情：
                - 动作：
                - 肢体：

                演员B 状态
                - 表情：
                - 动作：
                - 肢体：

                镜头语言：
                1. 景别
                2. 构图
                3. 镜头运动
                4. 焦点与节奏

                【输出顺序】
                ### 基本信息
                - 总时长：约X分X秒
                - 分镜组数：X组
                - 画面比例：9:16竖屏
                - 模型：Seedance2.0

                ### 分组总览
                | 组号 | 时间范围 | 核心情绪 |

                ### 分镜剧本正文
                逐组展开所有镜头。
                """;
    }

    public static String stage2User(String dialogue) {
        return "以下是相声台词，请将其转化为完整的 Seedance 分镜剧本：\n\n" + dialogue;
    }

    // ───────────────────────── 阶段三：按组拆分提示词 ─────────────────────────

    public static String stage3System() {
        return """
                把用户给的 Seedance 分镜剧本按「第X组」拆开输出。直接输出，无开场白，不提字幕。

                【规则】
                - 每组标题：### 第X组提示词
                - 标题下第一行：Xs-Xs秒 镜头：X、X。模型：Seedance2.0，视频规格：9:16竖版，共Xs
                - 不加分隔线，不加加粗，不加引用符号。

                【每组格式】
                ### 第X组提示词
                Xs-Xs秒 镜头：X、X。模型：Seedance2.0，视频规格：9:16竖版，共Xs
                镜头X｜Xs-Xs秒｜中景

                台词
                A：[台词]
                B：[台词]

                演员A 状态
                - 表情：
                - 动作：
                - 肢体：

                演员B 状态
                - 表情：
                - 动作：
                - 肢体：

                镜头语言：
                1. 景别
                2. 构图
                3. 镜头运动
                4. 焦点与节奏
                """;
    }

    public static String stage3User(String storyboard) {
        return "以下是完整的分镜剧本，请按组拆分为 Seedance 可用的分组提示词：\n\n" + storyboard;
    }

    // ───────────────────────── 保险合规 ─────────────────────────

    public static String insuranceCompliance() {
        return PromptRules.insuranceCompliance();
    }

    public static String outputDiscipline() {
        return PromptRules.outputDiscipline();
    }
}
