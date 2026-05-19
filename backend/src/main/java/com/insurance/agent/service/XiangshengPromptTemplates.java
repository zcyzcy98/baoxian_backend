package com.insurance.agent.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 相声剧本创作 — 基于 Playbook 分析的多维度 prompt 模板。
 * 5 个内容维度 + 1 个可选语气风格，对应 30 个视频的结构化分析结果。
 */
public final class XiangshengPromptTemplates {

    private XiangshengPromptTemplates() {}

    // ───────────────────────── 维度定义 ─────────────────────────

    /** 钩子类型 */
    private static final Map<String, String> HOOK_TYPES;

    static {
        HOOK_TYPES = new LinkedHashMap<>();
        HOOK_TYPES.put("身份权威型", "干了N年保险，我发现……（最高频，建立专业可信度）");
        HOOK_TYPES.put("模拟偏见型", "别跟我提保险，保险都是骗人的（替观众说出偏见）");
        HOOK_TYPES.put("反常识判断型", "到底多大的冤种才会……（用反直觉论断开场）");
        HOOK_TYPES.put("故事悬念型", "今天遇到个老板/客户……（用故事场景引入）");
    }

    private static final Map<String, String> HOOK_GUIDE = Map.of(
            "身份权威型", """
                    - 开场必须包含"干了N年保险"身份锚点 + 悬念词（怪象/荒谬/发现/最怕/鬼话/谎言）
                    - 3秒内完成身份建立+悬念抛出，不要寒暄和自我介绍
                    - 抽象模板：「干了N年保险，我发现[群体]最[极端形容词]的一个[行为/现象]」
                    - 示例："干了八年保险，我发现客户最爱说的一句鬼话"
                    """,
            "反常识判断型", """
                    - 用一个反直觉的判断或类比开场，制造"为什么会这样"的好奇
                    - 用完全无关但人人有体感的场景做类比
                    - 抽象模板：「到底多[极端形容词]的[身份]才会[反常识行为]」
                    - 示例："劝人买保险比劝人分手还难"、"拿保险给客户做规划比让丈母娘嫁闺女还难"
                    """,
            "模拟偏见型", """
                    - 用第一人称演出客户最常说的拒绝/抱怨
                    - 逗哏先附和2-3秒，再用"错"或"那是你不懂"反转
                    - 抽象模板：「[拒绝态度表达]，[大众偏见金句]」
                    - 示例："我才30岁，身体好得很，买什么保险"、"等我有钱了再说"
                    """,
            "故事悬念型", """
                    - 用一个具体的人物/场景故事开场："今天遇到个老板……"、"有个客户跟我说……"
                    - 故事要有悬念，不要在开场给出结论
                    - 适合需要具体案例支撑的话题
                    """
    );

    /** 剧本结构 */
    private static final Map<String, String> STRUCTURES;

    static {
        STRUCTURES = new LinkedHashMap<>();
        STRUCTURES.put("反转五段式", "主流结构，钩子→立靶子→反转→论证→金句→致敬");
        STRUCTURES.put("并列论证式", "适合多卖点/多论点话题，用排比/三连替代反转");
        STRUCTURES.put("深度科普式", "信息密度型，适合复杂产品教育");
    }

    private static final Map<String, String> STRUCTURE_GUIDE = Map.of(
            "反转五段式", """
                    【反转五段式骨架】
                    | 段落 | 占比 | 功能 | 写作要求 |
                    |---|---|---|---|
                    | ① 钩子 | 0–10% | 一句话抛出反差/悬念 | 按钩子类型指导写 |
                    | ② 立靶子 | 10–25% | 让观众把"心里那句反对台词"听到 | 由B说出偏见原话，必须完整说一遍，不是概括 |
                    | ③ 反转触发 | 25–30% | 一句话翻转 | 必须用显式反转词："错"/"那是你不懂"/"话锋一转" |
                    | ④ 论证主体 | 30–65% | 用具象类比把论点钉死 | 至少1个能在脑中成像的场景（ICU/病床/借钱/4S店/网购） |
                    | ⑤ 金句升华 | 65–85% | 把保险重新定义为抽象价值 | 输出对仗/反差式金句，15-25字 |
                    | ⑥ 致敬CTA | 85–100% | 致敬保险人收尾 | 末句含「请珍惜身边那个还在……的人」 |

                    转折点位置规律：反转出现在10-25%处（14/19命中），快速完成"代偏见→反驳"抢留存。
                    """,
            "并列论证式", """
                    【并列论证式骨架】
                    | 段落 | 占比 | 功能 | 写作要求 |
                    |---|---|---|---|
                    | ① 钩子 | 0–10% | 开场 | 按钩子类型指导写 |
                    | ② 立靶子 | 10–20% | 铺垫 | 列举常见现象/偏见 |
                    | ③ 并列论点1 | 20–40% | 第一个支撑点 | 每个论点自带小钩子+小论证+小金句 |
                    | ④ 并列论点2 | 40–60% | 第二个支撑点 | 用排比/三连增强节奏 |
                    | ⑤ 并列论点3 | 60–80% | 第三个支撑点 | 与前两个形成递进或互补 |
                    | ⑥ 致敬收束 | 80–100% | 总结+致敬 | 致敬保险人 + 金句收束 |

                    适用场景：需要列举多个支撑点的话题（养老保险全面卖点、保险vs银行多维比较）。
                    每个论点段落必须自带一个金句，否则会枯燥。
                    """,
            "深度科普式", """
                    【深度科普式骨架 — 信息密度型】
                    | 段落 | 占比 | 功能 | 写作要求 |
                    |---|---|---|---|
                    | ① 钩子 | 0–5% | 开场 | 用数据/反常识事实开场 |
                    | ② 问题铺垫 | 5–15% | 建立认知缺口 | 列举观众常见的认知误区 |
                    | ③ 核心讲解 | 15–60% | 系统性教育 | 分3-5个要点逐一讲解，每个配数据/案例 |
                    | ④ 金句总结 | 60–80% | 提炼记忆点 | 至少1句高传播性金句 |
                    | ⑤ 致敬收束 | 80–100% | 情感闭环 | 致敬保险人 |

                    适用场景：复杂产品教育（年金险、终身寿险等需要系统讲解的话题）。
                    注意：时长控制在120秒以内，超过后完播率急剧下降。
                    """
    );

    /** 情绪弧线 */
    private static final Map<String, String> EMOTION_ARCS;

    static {
        EMOTION_ARCS = new LinkedHashMap<>();
        EMOTION_ARCS.put("偏见→反驳→致敬", "主弧形，对抗偏见类话题首选");
        EMOTION_ARCS.put("共鸣→筛选→自豪", "B端从业者向，职业认同类话题");
        EMOTION_ARCS.put("焦虑→方案→安心", "刚需痛点向，单一险种深度内容");
    }

    private static final Map<String, String> EMOTION_GUIDE = Map.of(
            "偏见→反驳→致敬", """
                    【情绪弧线：偏见→反驳→恐惧→致敬】
                    形状：平→反弹→深谷→平缓上扬→温暖收尾

                    关键触发节点：
                    - ~20%处：用"错！"或"那是你不懂"做认知颠覆
                    - ~50%处：用ICU/病床/借钱等具象场景制造恐惧/扎心
                    - ~75%处：输出对仗式金句完成"价值重定义"
                    - ~95%处：致敬保险人 + 观众鼓掌

                    适用：对抗"保险是骗局"偏见、强调家庭责任、反驳具体购买异议
                    """,
            "共鸣→筛选→自豪", """
                    【情绪弧线：共鸣痛点→筛选客户→职业自豪】
                    形状：自嘲→愤慨→清醒→自豪

                    关键触发节点：
                    - ~25%处：抛出"不做教育只做筛选"类筛选客户金句
                    - ~50%处：用"少吃一顿火锅 vs 家庭下半生托底"类不对等对比
                    - ~85%处：致敬同行 / 呼吁观众珍惜

                    适用：朋友圈传播向（从业者转给同行/犹豫客户）、行业节日借势、职业人设建设
                    """,
            "焦虑→方案→安心", """
                    【情绪弧线：具象焦虑→解决方案→安心】
                    形状：焦虑铺陈→恐惧爆发→给出钥匙→踏实落地

                    关键触发节点：
                    - ~30%处：连续3个排比场景渲染焦虑（用排比强化）
                    - ~55%处：抛出最扎心后果
                    - ~75%处："保单才是XX"完成价值重定义

                    适用：单一险种深度内容（养老保险、家庭责任保险、女性向产品）
                    """
    );

    /** 目标受众 */
    private static final Map<String, String> AUDIENCES;

    static {
        AUDIENCES = new LinkedHashMap<>();
        AUDIENCES.put("C端潜在客户", "面向有购买力但对保险有偏见的消费者");
        AUDIENCES.put("双轨", "C端可被打动、B端也愿意转发（最强传播力）");
        AUDIENCES.put("B端从业者", "面向保险代理人，提供职业认同和转发素材");
    }

    private static final Map<String, String> AUDIENCE_GUIDE = Map.of(
            "C端潜在客户", """
                    【受众：C端潜在客户】
                    - 年龄25-55岁，有一定储蓄但对保险有偏见
                    - 语言风格：生活化、接地气、多用类比和场景
                    - 案例方向：ICU/病床/借钱/房贷/孩子教育/养老
                    - CTA方向：推动咨询/购买
                    - 禁忌：不要用行业术语，不要硬卖
                    """,
            "B端从业者", """
                    【受众：B端保险从业者】
                    - 需要职业认同感和行业自豪感
                    - 语言风格：同行视角、自嘲式共鸣
                    - 案例方向：行业委屈、被误解、筛选客户
                    - CTA方向：转发到朋友圈/工作群
                    - 核心：让从业者觉得"这条视频替我说了心里话"
                    """,
            "双轨", """
                    【受众：双轨（C端+B端）】
                    - 前半段对C端反驳偏见，后半段对B端致敬赋能
                    - 这是传播力最强的组合：一条视频既能说服潜在客户，又能让代理人主动转发
                    - 语言风格：前半段生活化，后半段职业共鸣
                    - CTA方向：先推动C端行动，再致敬B端促进转发
                    """
    );

    /** 话题方向 */
    private static final Map<String, String> TOPIC_DIRECTIONS;

    static {
        TOPIC_DIRECTIONS = new LinkedHashMap<>();
        TOPIC_DIRECTIONS.put("反驳偏见", "对抗'保险=骗局'的认知矫正");
        TOPIC_DIRECTIONS.put("情感共鸣", "尊严、底气、不拖累家人");
        TOPIC_DIRECTIONS.put("价值对比", "保险vs银行/理财的多维对比");
        TOPIC_DIRECTIONS.put("职业认同", "保险代理人职业价值/筛选客户");
        TOPIC_DIRECTIONS.put("购买时机", "趁早买、年轻时挑保险");
    }

    private static final Map<String, String> TOPIC_GUIDE = Map.of(
            "反驳偏见", """
                    【话题方向：反驳偏见】
                    核心策略：代偏见发声 → 用逻辑/类比/场景反驳
                    常见偏见靶子："保险都是骗的"/"有医保就够了"/"等有钱再说"/"我还年轻"
                    切入公式：「你以为X，其实Y」或「你嘴上说X，其实你心里Y」
                    """,
            "价值对比", """
                    【话题方向：价值对比】
                    核心策略：用对比让观众看到保险的真实价值
                    常见对比：保险vs银行存款/车险vs人身险/买手机vs买保险
                    切入公式：「X的人做Y，你却Z」
                    """,
            "情感共鸣", """
                    【话题方向：情感共鸣】
                    核心策略：用情感场景触动观众，强调保险 = 尊严/底气/责任
                    常见场景：ICU门口/病床前/借钱/看脸色/拖累家人
                    价值重定义方向：尊严/底气/退路/嫁妆/筛选条件
                    """,
            "职业认同", """
                    【话题方向：职业认同】
                    核心策略：自嘲行业委屈 → 重新定义职业价值 → 致敬升华
                    常见角度：被误解/被骂/筛选客户/不做教育只做筛选
                    适合B端传播和节日借势
                    """,
            "购买时机", """
                    【话题方向：购买时机】
                    核心策略：用时间/年龄制造紧迫感
                    核心金句方向："年轻买是你挑保险，岁数大了是保险挑你"
                    数据支撑：1岁vs30岁保费对比、保费倒挂等
                    """
    );

    /** 语气风格（可选，保留原有） */
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

    // ───────────────────────── 前端维度选项 ─────────────────────────

    /** 返回所有维度选项，供前端展示 */
    public static List<Map<String, Object>> dimensionOptions() {
        List<Map<String, Object>> dims = new java.util.ArrayList<>();
        dims.add(buildDim("hookType", "钩子类型", HOOK_TYPES));
        dims.add(buildDim("structure", "剧本结构", STRUCTURES));
        dims.add(buildDim("emotionArc", "情绪弧线", EMOTION_ARCS));
        dims.add(buildDim("audience", "目标受众", AUDIENCES));
        dims.add(buildDim("topicDirection", "话题方向", TOPIC_DIRECTIONS));
        dims.add(buildDim("toneStyle", "语气风格", TONE_STYLES));
        return dims;
    }

    private static Map<String, Object> buildDim(String key, String label, Map<String, String> options) {
        Map<String, Object> dim = new LinkedHashMap<>();
        dim.put("key", key);
        dim.put("label", label);
        List<Map<String, String>> items = new java.util.ArrayList<>();
        for (var entry : options.entrySet()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("value", entry.getKey());
            item.put("desc", entry.getValue());
            items.add(item);
        }
        dim.put("options", items);
        return dim;
    }

    // ───────────────────────── 阶段一：台词创作 ─────────────────────────

    /**
     * 阶段一 system prompt — 根据用户选择的多维度拼装完整的台词创作指令。
     */
    public static String stage1System(String hookType, String structure, String emotionArc,
                                       String audience, String topicDirection, String toneStyle,
                                       Integer duration) {
        StringBuilder sb = new StringBuilder();

        sb.append("你是一位专业的相声剧本创作者，擅长将保险行业话题改编成双人相声切片台词。\n");
        sb.append("请为用户生成一篇完整的相声台词。\n\n");

        if (duration != null && duration > 0) {
            int wordLimit = duration * 5;
            sb.append("═══════════════════════════════════════\n");
            sb.append("【目标时长】约").append(duration).append("秒\n");
            sb.append("【字数参考】约").append(wordLimit).append("字（含A和B所有台词），最多超出到目标的1.2倍\n");
            sb.append("【段落结构】");
            if (duration <= 60) {
                sb.append("60秒极简版：钩子→立靶子→1个核心反转→金句→致敬，共4-5轮对话，每轮A+B合计不超过3句。\n");
            } else if (duration <= 90) {
                sb.append("90秒标准版：钩子→立靶子→1-2个核心反转→金句→致敬，共6-8轮对话，每轮A+B合计3-5句。\n");
            } else {
                sb.append(duration + "秒完整版：钩子→立靶子→2-3个反转论证→金句→致敬，共8-12轮对话。\n");
            }
            sb.append("═══════════════════════════════════════\n\n");
        }

        sb.append("【核心角色】\n");
        sb.append("A：主讲人，代表保险从业者的视角，负责输出观点、案例、金句\n");
        sb.append("B：搭档/捧哏，负责替观众说出偏见、质疑、共情\n\n");

        // 各维度指导
        appendGuide(sb, "钩子类型", hookType, HOOK_GUIDE);
        appendGuide(sb, "剧本结构", structure, STRUCTURE_GUIDE);
        appendGuide(sb, "情绪弧线", emotionArc, EMOTION_GUIDE);
        appendGuide(sb, "目标受众", audience, AUDIENCE_GUIDE);
        appendGuide(sb, "话题方向", topicDirection, TOPIC_GUIDE);

        // 语气风格（可选）
        if (toneStyle != null && !toneStyle.isEmpty() && !"通用".equals(toneStyle)) {
            sb.append("【语气风格：").append(toneStyle).append("】\n");
            sb.append(toneStyleBody(toneStyle));
            sb.append("\n");
        }

        // 通用写作原则
        sb.append("""
                【通用写作原则】
                1. 控制时长：台词总量以目标时长为参考，可以小幅超出，但不要严重超时
                2. B永远替观众说话：B的偏见要"毒"，让观众一秒点头，第二秒被打脸
                3. A最重的话语气最平：不用煽情词，用事实、场景、数字
                4. 比喻来自生活：好比喻不需要解释，观众秒懂
                5. 每段留金句：每段最后一句可单独截图发朋友圈
                6. 结尾不煽情，只升华：态度比呼吁更有力
                7. 金句必须对仗工整、朗朗上口、15-25字以内
                8. 先附和再反转：A必须先说"对""没错""你说得对"，再用"但是""错"翻转
                9. 结尾必须致敬保险人：末句含「请珍惜身边那个还在……的人」或「致敬每一位了不起的保险人」

                【禁止事项】
                - 不要提到任何字幕相关内容
                - 不要写"字幕：XXX"或任何字幕指导
                - 不要使用过于书面化的表达
                - 不要硬卖产品，要通过故事和场景自然引导

                【输出格式】严格按以下格式输出，不要任何前置说明：
                每句台词必须单独一行，A和B之间必须换行，不允许把多句台词写在同一行。

                A：台词内容
                B：台词内容
                A：台词内容
                B：台词内容

                最后一行输出：预估时长：约X分X秒
                """);

        return sb.toString();
    }

    private static void appendGuide(StringBuilder sb, String dimLabel, String value, Map<String, String> guideMap) {
        if (value != null && !value.isEmpty()) {
            String guide = guideMap.get(value);
            if (guide != null) {
                sb.append("【").append(dimLabel).append("：").append(value).append("】\n");
                sb.append(guide).append("\n");
            }
        }
    }

    public static String stage1User(String topic) {
        return "选题：%s\n\n请基于以上选题，生成完整的相声台词。".formatted(topic);
    }

    // ───────────────────────── AI 智能推荐 ─────────────────────────

    /**
     * AI推荐 system prompt — 分析用户输入的主题，推荐最佳维度组合。
     */
    public static String recommendSystem() {
        return """
                你是一位保险短视频内容策略专家，擅长分析选题并匹配最佳创作方案。
                用户会给你一个保险相关的选题/主题，请分析后推荐最适合的创作维度组合。

                你需要从以下5个维度各选一个最佳选项，并给出简短理由：

                【钩子类型】（选1个）
                - 身份权威型：适合需要建立专业可信度的话题（最高频）
                - 反常识判断型：适合有明显认知反差的话题
                - 模拟偏见型：适合反驳常见偏见的话题
                - 故事悬念型：适合需要具体案例支撑的话题

                【剧本结构】（选1个）
                - 反转五段式：主流结构，适合大多数话题
                - 并列论证式：适合多卖点/多论点话题
                - 深度科普式：适合复杂产品教育

                【情绪弧线】（选1个）
                - 偏见→反驳→致敬：对抗偏见类话题首选
                - 共鸣→筛选→自豪：B端从业者向/职业认同类
                - 焦虑→方案→安心：刚需痛点/单一险种深度内容

                【目标受众】（选1个）
                - C端潜在客户：面向消费者
                - B端从业者：面向保险代理人
                - 双轨：C端+B端兼顾，传播力最强

                【话题方向】（选1个）
                - 反驳偏见：对抗"保险=骗局"的认知矫正
                - 价值对比：保险vs银行/理财
                - 情感共鸣：尊严、底气、不拖累家人
                - 职业认同：保险代理人职业价值
                - 购买时机：趁早买、年轻时挑保险

                【语气风格】（选1个，可选，默认"通用"）
                - 通用：标准普通话，理性幽默
                - 京片子：胡同语气，儿化音
                - 上海话：吴侬软语，精明算计
                - 东北话：豪爽幽默，天然笑点
                - 港剧腔：TVB腔调，精英感
                - 学术冷幽默：数据反讽，博弈论视角

                【输出格式】严格按以下JSON格式输出，不要任何其他内容：
                {
                  "hookType": "选项值",
                  "structure": "选项值",
                  "emotionArc": "选项值",
                  "audience": "选项值",
                  "topicDirection": "选项值",
                  "toneStyle": "通用",
                  "reason": "简短分析理由（100字以内）"
                }
                """;
    }

    public static String recommendUser(String topic) {
        return "选题：%s\n\n请分析这个选题，推荐最适合的创作维度组合。".formatted(topic);
    }

    // ───────────────────────── 语气风格详情 ─────────────────────────

    private static String toneStyleBody(String style) {
        return switch (style) {
            case "京片子" -> """
                    写作规则：
                    1. B的偏见要"欠揍"：越像胡同大爷大妈那种"我懂"，打脸效果越强
                    2. A的比喻要胡同化："4S店"、"体检卡"、"去医院"，全换成生活场景
                    3. 最重的话音调儿最平：甭管多沉，语调得像"今儿天真好"
                    4. 收尾得局气：结尾致敬但不带跪感，北京人不玩虚的
                    5. 常用词：您、咱、得嘞、甭、这味儿、明白人儿、局气
                    """;
            case "上海话" -> """
                    写作规则：
                    1. B的偏见要精明：上海人说话自带"我精我懂"劲儿
                    2. A的比喻要市井化：买菜、逛城隍庙、算铜钿
                    3. 算账要精：上海人讲逻辑、讲数字、讲性价比
                    4. 收尾要有腔调：不煽情，但要体面
                    5. 常用词：侬、啥体、勿、格、阿拉、老、迭个
                    """;
            case "东北话" -> """
                    写作规则：
                    1. B的偏见要"虎"：东北话说出来自带笑点，偏见越"虎"越有反差
                    2. A的比喻要东北化："整"、"整明白了"、"唠嗑"、"上医院"
                    3. 幽默要接地气：东北话的天然幽默感，不需要刻意
                    4. 收尾要实在：东北人不玩虚的，干就完了
                    5. 常用词：整、嘎嘎、唠嗑、咋整、俺、整明白了、寻思寻思
                    """;
            case "港剧腔" -> """
                    写作规则：
                    1. B的偏见要"港味"：TVB腔、"唔该"、"係咁意"、精英感
                    2. A的比喻要精英化："风险管理"、"保障"、"专业服务"
                    3. 语气要有TVB范儿：长短句结合、有节奏感
                    4. 收尾要有仪式感：港剧那种有feel的结尾
                    5. 常用词：咩、嘅、咁、喔、唔该、叻、搞掂、係咁架啦
                    """;
            case "学术冷幽默" -> """
                    写作规则：
                    1. B的偏见要有"直觉谬误"：代表普通人最直觉、最容易犯的认知错误
                    2. A的分析要有学术感：引用博弈论、概率论、行为经济学概念，但讲得像人话
                    3. 数据要具体：百分比、案例、数字，一个都不能少
                    4. 收尾要有学者腔：不煽情，但有温度
                    5. 常用词：根据研究、从数据来看、认知偏差、系统性风险、概率分布、期望值
                    """;
            default -> "标准普通话，理性幽默，节奏控制：开场快→反转中快→深层中速→金句慢速→收尾中速。\n";
        };
    }

    // ───────────────────────── 阶段二：Seedance 分镜剧本 ─────────────────────────

    public static String stage2System(Integer duration) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                你是一位专业的视频分镜师，擅长将相声台词转化为 Seedance 2.0 可用的分镜提示词。
                请将用户提供的相声台词转化为完整的 Seedance 分镜剧本。
                【重要】直接输出分镜剧本内容，不要任何开场白、自我介绍或前置说明。
                【重要】不要提到任何字幕相关内容。

                【核心规则】
                - 景别配比：中景80% / 特写20%（严格遵守）
                - 特写仅用于全片情绪最重的2-4个节点
                - 每15秒一组，最后一组可缩至10秒
                - 灯光节奏：开场暖黄 → 沉重段落压暗 → 结尾回暖
                - 画面比例：9:16竖屏
                - 模型：Seedance2.0
                """);

        if (duration != null && duration > 0) {
            int groups = (int) Math.ceil(duration / 15.0);
            sb.append("【目标视频时长】").append(duration).append("秒，分镜组数：").append(groups).append("组\n");
            sb.append("严格按该时长切分组数和时间范围，不要超出。\n");
        }

        sb.append("""
                【每组15秒，按以下五段式结构输出每个镜头】

                镜头X｜Xs-Xs秒｜【中景 / 特写】

                台词
                A：[台词]
                B：[台词]

                演员A 状态
                - 表情：[具体表情，如：眉头微皱，眼神略带疲惫和感慨]
                - 动作：[含道具的具体动作，如：右手将折扇轻轻敲在左手掌心，随后低头轻叹一口气]
                - 肢体：[整体姿态与重心，如：身体略前倾，重心在左脚，右手持扇自然垂下]

                演员B 状态
                - 表情：[具体表情]
                - 动作：[具体动作]
                - 肢体：[整体姿态]

                镜头语言：
                1. [景别说明]
                2. [构图，如：双人全身入框，A在左B在右]
                3. [镜头运动，如：镜头固定 / 缓缓推近 / 略微上摇后回落]
                4. [焦点与节奏，如：焦点在A，B自然站立在右侧 / 画面说完后静止停留X秒]

                【景别写法规则】
                中景（80%场景）：双人全身入框，A在左B在右，镜头固定为主
                特写（20%场景）：仅用于全片情绪最重的2-4个节点（金句说出、情绪最高点等）

                【道具情绪对照表】
                - 开场/轻叙：轻轻敲在掌心；低头轻叹后抬头
                - 轻松/调侃：悠闲地轻抛再接住；在掌心缓缓转动
                - 叙述/铺垫：展开扇了两下后合上；扇骨轻敲掌心
                - 强调/反问：猛地打开，扇面指向对方；高高举起挥动一圈
                - 沉重/承受：双手握扇垂于腹前；缓缓夹入臂弯
                - 情感转折：递给对方（象征托付）；握紧后静止
                - 收尾/闭合：从对方手中取回；缓缓合拢握于胸前鞠躬

                【灯光节奏对照表】
                - 开场：暖黄色聚光灯，色调温暖
                - 轻松包袱段：暖黄保持，明亮
                - 情绪转折点：灯光开始略微压暗
                - 沉重叙述段：灯光压暗，色调偏冷
                - 最低点静场：压至最暗，只保留面部光
                - 情绪升华段：灯光回升，聚光感增强
                - 收尾段：灯光回暖，柔和收尾

                【输出格式】必须严格按以下顺序输出，顺序不可打乱：

                最先输出：基本信息
                ### 基本信息
                - 总时长：约X分X秒
                - 分镜组数：X组
                - 画面比例：9:16竖屏
                - 模型：Seedance2.0

                紧接着输出：分组总览表格
                ### 分组总览
                | 组号 | 时间范围 | 核心情绪 | 灯光基调 |

                最后输出：分镜剧本正文
                ### 分镜剧本正文
                逐组展开所有镜头。

                顺序必须是：基本信息 → 分组总览 → 分镜剧本正文。不要把正文放在前面。
                """);

        return sb.toString();
    }

    public static String stage2User(String dialogue) {
        return "以下是相声台词，请将其转化为完整的 Seedance 分镜剧本：\n\n" + dialogue;
    }

    // ───────────────────────── 阶段三：按组拆分提示词 ─────────────────────────

    public static String stage3System() {
        return """
                你是一位视频分镜提示词整理师，擅长将完整的 Seedance 分镜剧本按组拆分为可直接使用的提示词。
                请将用户提供的分镜剧本按「第X组」为单位逐一拆出。
                【重要】直接输出分组提示词内容，不要任何开场白、自我介绍或前置说明。
                【重要】不要提到任何字幕相关内容。

                【拆分规则】
                - 每组标题用 markdown 三级标题格式：### 第X组提示词
                - 每组标题下第一行：Xs-Xs秒 镜头：X、X。模型：Seedance2.0，视频规格：9:16竖版，共Xs
                - 所有组依次输出，不附加后期制作总表
                - 不使用 --- 分隔线
                - 台词、演员状态不使用加粗（**）或引用符号（>）

                【每组输出格式】
                ### 第X组提示词
                Xs-Xs秒 镜头：X、X。模型：Seedance2.0，视频规格：9:16竖版，共Xs
                镜头X｜Xs-Xs秒｜【中景 / 特写】

                台词
                A：[台词]
                B：[台词]

                演员A 状态
                - 表情：[具体描述]
                - 动作：[含道具的具体动作]
                - 肢体：[整体姿态与重心]

                演员B 状态
                - 表情：[具体描述]
                - 动作：[具体动作]
                - 肢体：[整体姿态]

                镜头语言：
                1. [景别说明]
                2. [构图]
                3. [镜头运动]
                4. [焦点与节奏]
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
