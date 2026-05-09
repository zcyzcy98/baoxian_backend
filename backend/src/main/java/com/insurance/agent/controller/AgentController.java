package com.insurance.agent.controller;

import com.insurance.agent.dto.AgentRequest;
import com.insurance.agent.dto.AgentResponse;
import com.insurance.agent.service.ComplianceCheckService;
import com.insurance.agent.service.DeepSeekService;
import com.insurance.agent.service.ImageGenerationService;
import com.insurance.agent.service.ImageTemplateService;
import com.insurance.agent.service.LibTvService;
import com.insurance.agent.service.SeedanceService;
import com.insurance.agent.dto.DouyinNote;
import com.insurance.agent.dto.XhsNote;
import com.insurance.agent.service.DouyinExtractService;
import com.insurance.agent.service.KnowledgeQaService;
import com.insurance.agent.service.MediaToDocService;
import com.insurance.agent.service.XhsComplianceService;
import com.insurance.agent.service.AuthService;
import com.insurance.agent.service.CreditsService;
import com.insurance.agent.service.GeneratedContentService;
import com.insurance.agent.service.XhsExtractService;
import com.insurance.agent.service.XhsSampleRagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final DeepSeekService deepSeek;
    private final ComplianceCheckService complianceCheckService;
    private final XhsComplianceService xhsCompliance;
    private final ImageGenerationService imageGeneration;
    private final ImageTemplateService imageTemplates;
    private final LibTvService libTv;
    private final SeedanceService seedance;
    private final XhsSampleRagService xhsSampleRag;
    private final KnowledgeQaService knowledgeQa;
    private final MediaToDocService mediaToDoc;
    private final XhsExtractService xhsExtract;
    private final DouyinExtractService douyinExtract;
    private final GeneratedContentService generatedContent;
    private final AuthService authService;
    private final CreditsService creditsService;

    public AgentController(DeepSeekService deepSeek,
                           ComplianceCheckService complianceCheckService,
                           XhsComplianceService xhsCompliance,
                           ImageGenerationService imageGeneration,
                           ImageTemplateService imageTemplates,
                           LibTvService libTv,
                           SeedanceService seedance,
                           XhsSampleRagService xhsSampleRag,
                           KnowledgeQaService knowledgeQa,
                           MediaToDocService mediaToDoc,
                           XhsExtractService xhsExtract,
                           DouyinExtractService douyinExtract,
                           GeneratedContentService generatedContent,
                           AuthService authService,
                           CreditsService creditsService) {
        this.deepSeek = deepSeek;
        this.complianceCheckService = complianceCheckService;
        this.xhsCompliance = xhsCompliance;
        this.imageGeneration = imageGeneration;
        this.imageTemplates = imageTemplates;
        this.libTv = libTv;
        this.seedance = seedance;
        this.xhsSampleRag = xhsSampleRag;
        this.knowledgeQa = knowledgeQa;
        this.mediaToDoc = mediaToDoc;
        this.xhsExtract = xhsExtract;
        this.douyinExtract = douyinExtract;
        this.generatedContent = generatedContent;
        this.authService = authService;
        this.creditsService = creditsService;
    }

    @GetMapping("/image-templates")
    public ResponseEntity<?> listImageTemplates() {
        return ResponseEntity.ok(imageTemplates.list());
    }

    @PostMapping("/title")
    public ResponseEntity<AgentResponse> generateTitle(@RequestBody AgentRequest req) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("主题不能为空", null));
        }
        String system = """
                你是一位小红书爆文标题专家，专注于保险科普内容。请根据以下参数生成一个高点击率的标题。

                【输入参数】
                - 情绪基调：{{情绪}}（可选：反讽/打脸 | 揭秘/内幕 | 避坑/后悔 | 觉醒/反思 | 愤怒/吐槽 | 安心/种草）
                - 人设：{{人设}}（可选：保险从业者-脱粉 | 行业观察者 | 普通用户-避坑党 | 单身年轻人）
                - 核心场景：{{场景}}（如：给父母买保险 / 重疾险 / 百万医疗险 / 行业内幕）

                【标题写作规则】
                1. 字数控制在 15-25 字之间
                2. 必须包含强情绪词，如：大实话 / 内幕 / 排雷 / 坑 / 才敢说 / 真相 / 辞职后 / 进了公司才知道
                3. 人设要前置或嵌入，如"我是卖保险的""从业9年""辞职后"
                4. 可适当加 1-2 个 emoji，放在开头或结尾，不要堆砌
                5. 末尾可加感叹号或用疑问句制造悬念
                6. 禁止使用"大家""朋友们"等通用词，要有身份代入感

                【示例风格参考】
                - "我是卖保险的，但有些话还是要说"
                - "辞职后才敢讲的重疾险内幕"
                - "果然，真正的保险大佬都在评论区！"
                - "保险公司最怕被知道的产品秘密，我直接打包告诉你"

                请输出 5 个候选标题，按点击率预估从高到低排列。
                注意输出不要出现 markdown 格式。

                【合规红线（小红书金融广告规则）】
                标题中严禁出现以下任何表述，否则视为违规：
                - 承诺类：保本保息、保本、保收益、保息、稳赚不赔、稳赚、零风险、投资无风险、亏损包赔、安全无忧、理财无忧、本金无忧、百分百本息保障
                - 绝对化：限时优惠、一律最低价、利息最低、额度最高
                - 焦虑营销：以具体疾病名称（如乳腺癌）搭配保险产品进行标题炒作
                - 荐股类：荐股、荐基、T+0
                """;
        String user = "主题: " + req.getTopic();
        boolean ragMode = "rag-xhs".equals(req.getModel());
        String finalSystem = ragMode ? system + xhsSampleRag.buildContext(user) : system;
        String content = deepSeek.chat(finalSystem, user, ragMode ? "chat" : req.getModel());
        String modelLabel = ragMode ? "deepseek-chat + 爆款RAG" : deepSeek.resolveModel(req.getModel());

        AgentResponse resp = new AgentResponse(content, modelLabel);
        resp.setComplianceWarnings(toWarningMaps(xhsCompliance.check(content)));
        generatedContent.save("xhs_title", req.getTopic(), content, null, null, null, modelLabel);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/text")
    public ResponseEntity<AgentResponse> generateText(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("主题不能为空", null));
        }
        String system = """
                你是一位小红书保险科普博主，擅长用真实视角写出高互动的干货笔记。请根据以下参数创作一篇完整正文。


                【输入参数】
                - 标题：{{标题}}
                - 情绪基调：{{情绪}}
                - 人设：{{人设}}
                - 核心场景/险种：{{场景}}
                
                【任务】
                根据用户提供的:
                -已确定的标题
                -已确定的情绪类型
                -RAG 检索到的素材片段(可能是金句/真实情境/矛盾冲突等)
                按对应情绪类型的正文结构,产出一篇完整的小红书笔记正文。
                
                
           

                【正文结构要求】
                按以下五段式结构输出：

                ① 开场钩子（2-3句）
                - 用共鸣痛点或反常识观点开头
                - 情绪要强烈，让读者觉得"说的就是我"
                - 示例风格："有准备买保险的姐妹吗？求你今天这篇一定要看完！"

                ② 核心观点/避坑点（3-5条）
                - 用数字编号 + emoji 分点列出
                - 每条包含：❌ 常见误区 → ✅ 正确做法
                - 干货要具体，给出数字/标准/产品要素，不要泛泛而谈
                - 示例风格："❌ 重疾险不是确诊即赔 → ✅ 需达到合同约定状态才赔，等待期内确诊不赔"

                ③ 分场景细化建议（按1-2个具体人群展开）
                - 如：普通打工人 / 有孩子的家庭 / 55岁以上父母 / 体检异常人群
                - 给出可落地的配置方案（保什么+预算参考）

                ④ 互动收尾（2-3句）
                - 弱化推销感，以"有问必答"姿态结尾
                - 示例："不管是自己的保险还是爸妈的，都可以问我，主打有问必答~"
                - 加一句免责声明：*具体费率及保单金额以实际为准

                ⑤ Hashtag（6-10个）
                - 必含：#保险
                - 根据场景补充：#重疾险 #百万医疗险 #意外险 #保险避坑 #宝宝保险 #父母保险 等
                
                【写作风格要求】
                - 口语化、有温度，像在和闺蜜聊天
                - 不要写成说明书，每一条要有"为什么这样"的解释
                - 情绪词要自然融入，不要堆砌感叹号
                - 总字数控制在 600-900 字
                - 最终输出不要出现"① 开场钩子（2-3句）、② 核心观点/避坑点（3-5条）、③ 分场景细化建议（按1-2个具体人群展开）、④ 互动收尾（2-3句）、⑤ Hashtag（6-10个）"

                【合规红线（小红书金融广告规则）】
                正文中严禁出现以下任何表述：
                - 承诺类：保本保息、保本、保收益、保息、稳赚不赔、稳赚、零风险、投资无风险、亏损包赔、安全无忧、理财无忧、本金无忧、百分百本息保障、收益保护协议、签约保收益
                - 绝对化：限时优惠、一律最低价、利息最低
                - 保险违规：分红率（作为承诺用语）、投资回报率（作为承诺用语）、返还保费
                - 贷款违规：以贷养贷、免息贷款、不看征信、黑户也可
                - 焦虑营销：借助具体疾病焦虑推销保险、整容贷、彩礼贷、升舱贷
                """;
        StringBuilder sb = new StringBuilder("主题: ").append(req.getTopic());
        if (!isBlank(req.getStyle())) {
            sb.append("\n风格: ").append(req.getStyle());
        }
        String userQuery = sb.toString();
        boolean ragMode = "rag-xhs".equals(req.getModel());
        String finalSystem = ragMode ? system + xhsSampleRag.buildContext(userQuery) : system;
        String content = deepSeek.chat(finalSystem, userQuery, ragMode ? "chat" : req.getModel());
        String modelLabel = ragMode ? "deepseek-chat + 爆款RAG" : deepSeek.resolveModel(req.getModel());

        AgentResponse resp = new AgentResponse(content, modelLabel);
        resp.setComplianceWarnings(toWarningMaps(xhsCompliance.check(content)));
        generatedContent.save("xhs_post", req.getTopic(), content, null, null, null, modelLabel);
        creditsService.deduct(resolveUserId(auth), 3, "xhs_text", req.getTopic());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/text-compliance-check")
    public ResponseEntity<AgentResponse> checkTextCompliance(@RequestBody AgentRequest req) {
        if (isBlank(req.getContent())) {
            return ResponseEntity.badRequest().body(new AgentResponse("待检测文本不能为空", null));
        }

        ComplianceCheckService.ComplianceCheckResult result = complianceCheckService.check(req.getContent());
        StringBuilder sb = new StringBuilder();
        sb.append("## 检测结论\n\n")
                .append(result.summary())
                .append("\n\n");

        if (!result.hasRisk()) {
            sb.append("### 命中结果\n\n")
                    .append("- 未发现明确命中的违规词/话术\n");
            if (result.relatedRules() != null && !result.relatedRules().isEmpty()) {
                sb.append("\n### RAG 补充复核\n\n");
                int idx = 1;
                for (ComplianceCheckService.RelatedRule item : result.relatedRules()) {
                    sb.append(idx++)
                            .append(". **相似规则/词条**：")
                            .append(blankFallback(item.phrase(), "词库未填写"))
                            .append("\n")
                            .append("   **相似度**：")
                            .append(String.format("%.1f%%", item.similarity() * 100))
                            .append("\n");
                    if (!isBlank(item.violationReason())) {
                        sb.append("   **违规原因**：")
                                .append(item.violationReason())
                                .append("\n");
                    }
                    if (!isBlank(item.sensitiveTips())) {
                        sb.append("   **敏感词语/风险提示**：")
                                .append(item.sensitiveTips())
                                .append("\n");
                    }
                    if (!isBlank(item.replacementSuggestion())) {
                        sb.append("   **合规替换建议**：")
                                .append(item.replacementSuggestion())
                                .append("\n");
                    }
                }
            }
            sb.append("\n### 建议\n\n")
                    .append("- 未命中不代表完全无风险，建议继续人工复核绝对化承诺、收益承诺、诱导成交、疾病相关敏感表述");
        } else {
            sb.append("### 命中结果\n\n");
            int idx = 1;
            for (ComplianceCheckService.MatchedPhrase item : result.matchedPhrases()) {
                sb.append(idx++)
                        .append(". **命中词/话术**：")
                        .append(item.matchedPhrase())
                        .append("\n")
                        .append("   **违规原因**：")
                        .append(blankFallback(item.violationReason(), "词库未填写"))
                        .append("\n");
                if (!isBlank(item.sensitiveTips())) {
                    sb.append("   **敏感词语/风险提示**：")
                            .append(item.sensitiveTips())
                            .append("\n");
                }
                if (!isBlank(item.replacementSuggestion())) {
                    sb.append("   **合规替换建议**：")
                            .append(item.replacementSuggestion())
                            .append("\n");
                }
                sb.append("   **命中位置**：第 ")
                        .append(item.firstIndex() + 1)
                        .append(" 个字符附近\n")
                        .append("   **RAG召回**：")
                        .append(item.recalledByRag() ? "已召回" : "未召回，靠精确匹配补齐")
                        .append("\n");
            }
        }

        AgentResponse resp = new AgentResponse(sb.toString(), "compliance-rag-check");
        resp.setCleanedContent(result.cleanedContent());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/image")
    public ResponseEntity<AgentResponse> generateImage(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return generateImageWithProvider(req, false, auth);
    }

    @PostMapping("/image/seedream")
    public ResponseEntity<AgentResponse> generateSeedreamImage(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        return generateImageWithProvider(req, true, auth);
    }

    private ResponseEntity<AgentResponse> generateImageWithProvider(AgentRequest req, boolean useSeedream, String auth) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("画面主题不能为空", null));
        }

        ImageTemplateService.Template template = imageTemplates.find(req.getTemplateId());

        String system = (template != null)
                ? buildTemplatedImageSystemPrompt(template)
                : buildDefaultImageSystemPrompt();

        StringBuilder user = new StringBuilder("画面主题: ").append(req.getTopic());
        if (!isBlank(req.getStyle())) user.append("\n附加风格说明: ").append(req.getStyle());

        String content = deepSeek.chat(system, user.toString(), req.getModel());

        String prompt = extractImagePrompt(content);
        // 模板图按竖版比例生成，ImageGenerationService 会转换为 9:16。
        String size = (template != null) ? "1440x2560" : null;
        String imageUrl = useSeedream
                ? imageGeneration.generateSeedream(prompt)
                : imageGeneration.generate(prompt, size);

        AgentResponse resp = new AgentResponse(content, deepSeek.resolveModel(req.getModel()));
        resp.setImageUrl(imageUrl);
        String imageModel = deepSeek.resolveModel(req.getModel()) + " + "
                + (useSeedream ? imageGeneration.seedreamModelLabel() : imageGeneration.modelLabel());
        resp.setModel(imageModel);
        generatedContent.save("image", req.getTopic(), content, imageUrl, null, null, imageModel);
        creditsService.deduct(resolveUserId(auth), 3, "xhs_images", "单张 AI 配图");
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/gzh-title")
    public ResponseEntity<AgentResponse> generateGzhTitle(@RequestBody AgentRequest req) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("主题不能为空", null));
        }
        String system = """
                你是一位公众号爆款标题专家，专注于保险科普内容。请根据主题生成 5 个高点击率的公众号文章标题。

                【公众号标题规则】
                1. 字数控制在 18-28 字
                2. 风格偏专业有温度，不要过于猎奇或煽情
                3. 可用数字/场景/对比/疑问句等钩子
                4. 避免"大家""朋友们"等泛指，要有具体感
                5. 示例风格参考：
                   - "年薪 30 万，医保还够用吗？一文说清三重保障漏洞"
                   - "孩子 3 岁前买什么保险最划算？一张表帮你算清楚"
                   - "重疾险理赔率只有 30%？误解背后的真相说出来"
                   - "父母 60 岁了，还能买保险吗？这 4 种选择值得了解"

                请输出 5 个候选标题，按点击率预估从高到低排列，每行一个，不带编号和 markdown 格式。
                """;
        String user = "主题: " + req.getTopic()
                + (isBlank(req.getStyle()) ? "" : "\n方向: " + req.getStyle());
        String content = deepSeek.chat(system, user, req.getModel());
        generatedContent.save("gzh_title", req.getTopic(), content, null, null, null, deepSeek.resolveModel(req.getModel()));
        return ResponseEntity.ok(new AgentResponse(content, deepSeek.resolveModel(req.getModel())));
    }

    @PostMapping("/gzh-text")
    public ResponseEntity<AgentResponse> generateGzhText(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("主题不能为空", null));
        }
        int wordCount = 2000;
        if (req.getWordCount() != null && req.getWordCount() > 0) {
            wordCount = Math.min(Math.max(req.getWordCount(), 1500), 4000);
        }
        String system = ("""
                你是一位保险公众号资深编辑，擅长写出专业、客观、有温度的保险科普文章。
                用户已确定好标题，请直接输出与标题高度匹配的正文，不要重复输出标题本身。

                【正文结构要求】
                1. 引言/开场（150-200字）：具体场景或数据切入，与标题呼应
                2. 核心内容（分 3-5 小节，每节有小标题）：有干货、有案例、有数字
                3. 总结/建议（150-200字）：提炼全文，给出可落地的行动建议
                4. 免责声明：*本文仅作科普，具体保险责任以条款为准

                【写作风格要求】
                - 专业、客观，不夸大宣传，不制造焦虑
                - 语言流畅，每段不宜过长，适合手机阅读
                - 适当用小标题、加粗、数字编号等排版
                - 总字数控制在 %d 字左右
                - 通俗易懂，必要时解释专业术语
                - 真实感强，可适当加数据或案例

                【输出格式】
                直接输出正文内容，不要输出任何前言、说明或标题行。
                """).formatted(wordCount);
        String user = "标题: " + req.getTopic()
                + (isBlank(req.getStyle()) ? "" : "\n写作方向补充: " + req.getStyle());
        String content = deepSeek.chat(system, user, req.getModel());
        generatedContent.save("gzh_article", req.getTopic(), content, null, null, null, deepSeek.resolveModel(req.getModel()));
        creditsService.deduct(resolveUserId(auth), 5, "gzh_text", req.getTopic());
        return ResponseEntity.ok(new AgentResponse(content, deepSeek.resolveModel(req.getModel())));
    }

    @PostMapping("/wechat-create")
    public ResponseEntity<AgentResponse> generateWechatArticle(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("主题不能为空", null));
        }
        
        // 验证字数要求：1500-4000，默认2000
        int wordCount = 2000;
        if (req.getWordCount() != null && req.getWordCount() > 0) {
            wordCount = req.getWordCount();
            if (wordCount < 1500) wordCount = 1500;
            if (wordCount > 4000) wordCount = 4000;
        }
        
        String system = """
                你是一位保险公众号资深编辑，擅长写出专业、客观、有温度的保险科普文章。请根据以下要求创作完整的公众号文章。

                【文章结构要求】
                按以下结构输出：
                1. 【标题】：一个吸引人的标题，20-30字，带悬念或痛点
                2. 【引言/开场】：150-200字，用具体场景或数据引出主题
                3. 【核心内容】：分3-5个小节，每小节有标题，有干货有案例
                4. 【总结/建议】：150-200字，总结全文并给出可落地的建议
                5. 【免责声明】：*本文仅作科普，具体保险责任以条款为准

                【写作风格要求】
                - 专业、客观，不夸大宣传，不制造焦虑
                - 语言流畅，适合公众号阅读，每段不要太长
                - 适当用小标题、加粗、数字编号等排版
                - 总字数控制在 {{wordCount}} 字左右
                - 用词通俗易懂，不要堆砌专业术语，必要时要解释
                - 要有真实感和说服力，可适当加一些数据或案例

                【输出格式】
                先输出【标题】，然后换行输出【正文】。
                """.replace("{{wordCount}}", String.valueOf(wordCount));
        
        StringBuilder user = new StringBuilder("主题: ").append(req.getTopic());
        if (!isBlank(req.getStyle())) {
            user.append("\n风格: ").append(req.getStyle());
        }
        String content = deepSeek.chat(system, user.toString(), req.getModel());
        
        AgentResponse resp = new AgentResponse(content, deepSeek.resolveModel(req.getModel()));
        
        // 如果用户要求配图或封面，就生成相应的图片
        if (req.isNeedCover() || req.isNeedImages()) {
            try {
                // 生成封面（2.35:1，常用尺寸 1280x544 或 940x400）
                if (req.isNeedCover()) {
                    String coverSystem = """
                            你是一位公众号封面设计专家，请根据文章主题生成适合的封面图描述词。
                            要求：
                            - 风格简洁、专业，适合保险科普公众号
                            - 颜色以蓝色、绿色、白色为主，不要太花哨
                            - 要有文字区域，方便后期加文章标题
                            - 画面要清晰，适合 2.35:1 的比例
                            - 避免敏感内容，合规合规
                            """;
                    String coverContent = deepSeek.chat(coverSystem, "主题: " + req.getTopic(), req.getModel());
                    String coverPrompt = extractImagePrompt(coverContent);
                    String coverUrl = imageGeneration.generate(coverPrompt, "1280x544"); // 2.35:1 比例
                    resp.setCoverUrl(coverUrl);
                }
                
                // 生成配图（2.35:1 比例，适合公众号文章配图）
                if (req.isNeedImages()) {
                    String imageSystem = """
                            你是一位公众号配图设计专家，请根据文章主题生成适合的配图描述词。
                            要求：
                            - 风格简洁、专业，适合保险科普公众号
                            - 颜色以蓝色、绿色、白色为主，不要太花哨
                            - 适合 2.35:1 的比例
                            - 画面要清晰，避免敏感内容
                            - 可以适当有一些保险相关的元素，如保单、家庭、盾牌等
                            """;
                    String imageContent = deepSeek.chat(imageSystem, "主题: " + req.getTopic(), req.getModel());
                    String imagePrompt = extractImagePrompt(imageContent);
                    String imageUrl = imageGeneration.generate(imagePrompt, "1280x544"); // 2.35:1 比例
                    resp.setImageUrl(imageUrl);
                }
            } catch (Exception e) {
                // 图片生成失败不影响文章，只记录日志
                System.err.println("图片生成失败: " + e.getMessage());
            }
        }

        creditsService.deduct(resolveUserId(auth), 5, "gzh_text", req.getTopic());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/video-script")
    public ResponseEntity<AgentResponse> generateVideoScript(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("视频主题不能为空", null));
        }
        String system = """
                你是一位短视频(抖音/视频号/小红书视频)口播脚本写手, 专注于保险科普内容。请为用户生成一篇可直接拍摄的完整口播脚本。

                【脚本要求】
                1. 时长: 用户指定优先, 否则默认 60-90 秒(约 450-700 字)
                2. 结构必须按以下顺序:
                   - 钩子(0-3秒): 用强抓力开场 — 数据/反问/反常识/痛点共鸣 二选一
                   - 痛点铺陈(3-15秒): 用一个具体场景或案例引共鸣
                   - 干货核心(15-60秒): 3-5 个分点干货, 每点 ❌误区 → ✅正解
                   - 行动召唤(60秒后): 引导评论/收藏/关注, 弱化推销
                3. 语言要求:
                   - 口语化, 每句不超过 20 字
                   - 用方括号标注语气/动作: [严肃] [手势指向] [停顿1秒] [拿起合同道具]
                   - 不要书面语和长句, 适合口播节奏
                4. 必须包含至少 1 处情绪转折(从严肃到温暖, 或从吐槽到建议)
                5. 末尾追加 3-5 个相关话题标签

                【输出格式】
                直接输出脚本正文, 不要任何前后说明、不要使用代码块包裹。
                """;
        StringBuilder user = new StringBuilder("视频主题: ").append(req.getTopic());
        if (!isBlank(req.getStyle())) user.append("\n风格: ").append(req.getStyle());
        if (!isBlank(req.getDuration())) user.append("\n时长: ").append(req.getDuration());
        String content = deepSeek.chat(system, user.toString(), req.getModel());
        generatedContent.save("video_script", req.getTopic(), content, null, null, null, deepSeek.resolveModel(req.getModel()));
        creditsService.deduct(resolveUserId(auth), 8, "video_script", req.getTopic());
        return ResponseEntity.ok(new AgentResponse(content, deepSeek.resolveModel(req.getModel())));
    }

    @PostMapping("/video-to-script")
    public ResponseEntity<AgentResponse> generateVideoToScript(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        boolean hasVideoUrl = !isBlank(req.getVideoUrl());
        boolean hasTranscript = !isBlank(req.getContent());
        if (!hasVideoUrl && !hasTranscript) {
            return ResponseEntity.badRequest().body(new AgentResponse("视频链接不能为空", null));
        }

        // 如果是分析模式，先提取视频CDN链接，然后用火山方舟API进行真正的视频分析
        if ("analysis".equals(req.getOutputFormat())) {
            String videoContent = "";
            String videoUrl = null;
            String videoDesc = "";
            
            if (hasVideoUrl) {
                String lowerUrl = req.getVideoUrl().toLowerCase();
                try {
                    if (lowerUrl.contains("xiaohongshu.com") || lowerUrl.contains("xhslink.cn")) {
                        // 小红书：提取视频CDN和文案信息
                        com.insurance.agent.dto.XhsNote note = xhsExtract.extract(req.getVideoUrl());
                        if (note != null) {
                            videoUrl = note.getVideoUrl();
                            StringBuilder sb = new StringBuilder();
                            if (!isBlank(note.getTitle())) sb.append("标题: ").append(note.getTitle()).append("\n");
                            if (!isBlank(note.getContent())) sb.append("文案: ").append(note.getContent()).append("\n");
                            if (!isBlank(note.getAuthorName())) sb.append("作者: ").append(note.getAuthorName()).append("\n");
                            videoDesc = sb.toString();
                            log.info("[爆款分析] 小红书提取成功 - 视频URL: {}, 有视频: {}", 
                                    isBlank(videoUrl) ? "无" : "有(" + videoUrl.length() + "字符)", !isBlank(videoUrl));
                        }
                    } else if (lowerUrl.contains("douyin.com") || lowerUrl.contains("iesdouyin.com")) {
                        // 抖音：提取视频CDN和文案信息
                        com.insurance.agent.dto.DouyinNote note = douyinExtract.extract(req.getVideoUrl());
                        if (note != null) {
                            videoUrl = note.getVideoDownloadUrl();
                            StringBuilder sb = new StringBuilder();
                            if (!isBlank(note.getTitle())) sb.append("标题/文案: ").append(note.getTitle()).append("\n");
                            if (!isBlank(note.getNickname())) sb.append("作者: ").append(note.getNickname()).append("\n");
                            videoDesc = sb.toString();
                            log.info("[爆款分析] 抖音提取成功 - 视频URL: {}, 有视频: {}", 
                                    isBlank(videoUrl) ? "无" : "有(" + videoUrl.length() + "字符)", !isBlank(videoUrl));
                        }
                    }
                } catch (Exception e) {
                    log.warn("[爆款分析] 平台提取失败: {}", e.getMessage(), e);
                }
            }

            if (!isBlank(videoUrl)) {
                // 有真实视频CDN → 调用火山方舟API进行真正的视频分析
                try {
                    log.info("[爆款分析] 开始调用火山方舟API分析视频, URL长度: {}", videoUrl.length());
                    MediaToDocService.WorkflowResult result = mediaToDoc.documentAndScriptFromVideoUrl(
                            videoUrl, "markdown", req.getStyle(), req.getDuration(), req.getModel());
                    String videoAnalysis = result.document();
                    
                    String analysisPrompt = """
                            你是一位专业的短视频爆款分析专家。请基于以下视频分析结果和补充信息，进行爆款分析。

                            视频分析结果（来自视觉分析）：
                            %s

                            补充信息（来自平台提取）：
                            %s

                            请按照以下结构进行分析：
                            1. **钩子分析**：视频的开头是如何吸引观众的？画面、文案、节奏有什么特别之处？
                            2. **内容结构**：视频的内容组织结构有什么可借鉴的地方？
                            3. **节奏把控**：视频的节奏是如何安排的？快慢如何搭配？
                            4. **视觉表现**：画面、色彩、字幕、构图有什么可借鉴的？
                            5. **情绪价值**：视频传递了哪些情绪价值？是如何调动观众情绪的？
                            6. **金句/记忆点**：视频中有哪些让人印象深刻的金句或记忆点？
                            7. **可仿写要点**：提炼出3-5个可以直接仿写的核心要点

                            注意：
                            - 要结合视觉分析和文案信息一起分析
                            - 分析要具体，不要泛泛而谈
                            - 可仿写要点要具有可操作性
                            """.formatted(videoAnalysis, videoDesc);

                    String content = deepSeek.chat(analysisPrompt, "请分析这个爆款视频的原因", req.getModel());
                    return ResponseEntity.ok(new AgentResponse(content, deepSeek.resolveModel(req.getModel()) + " + 真实视频分析"));
                } catch (Exception e) {
                    log.error("[爆款分析] 火山方舟API调用失败: {}", e.getMessage(), e);
                    // 视频分析失败，降级为文案分析
                }
            } else {
                log.info("[爆款分析] 没有拿到视频CDN，降级为文案分析");
            }

            // 如果没有视频链接或视频分析失败，使用文案分析作为降级方案
            if (isBlank(videoContent) && !isBlank(videoDesc)) {
                videoContent = videoDesc;
            }
            if (isBlank(videoContent)) {
                videoContent = req.getContent();
            }
            if (isBlank(videoContent)) {
                videoContent = "视频链接: " + req.getVideoUrl() + "\n\n(未能获取到视频详细内容，请补充更多信息)";
            }

            String analysisPrompt = """
                    你是一位专业的短视频爆款分析专家。请对提供的素材进行爆款分析。

                    注意：
                    - 你可能拿到的是完整的视频文案，也可能只是标题和一些基础信息，请根据你获取到的信息灵活分析
                    - 如果素材信息较少，请重点分析：标题吸引力、话题选择等
                    - 请务必给出具体、可操作的分析，不要泛泛而谈

                    请按照以下结构进行分析：

                    1. **钩子分析**
                    - 标题/开头是如何吸引观众的？
                    - 用了什么技巧（提问、痛点、悬念、数字等）？

                    2. **内容结构**
                    - 整体内容的组织结构有什么可借鉴的？
                    - 是如何层层递进的？

                    3. **情绪价值**
                    - 传递了哪些情绪价值？
                    - 是如何调动观众情绪的？

                    4. **可仿写要点**（最重要！）
                    - 请提炼出3-5个可以直接仿写的核心要点
                    - 要具体，有可操作性

                    以下是分析素材：
                    %s
                    """.formatted(videoContent);

            log.info("[爆款分析] 使用降级文案分析，内容长度: {}", videoContent.length());
            String content = deepSeek.chat(analysisPrompt, "请分析这个爆款视频的原因", req.getModel());
            return ResponseEntity.ok(new AgentResponse(content, deepSeek.resolveModel(req.getModel()) + " + 文案分析"));
        }

        MediaToDocService.WorkflowResult result = hasVideoUrl
                ? mediaToDoc.documentAndScriptFromVideoUrl(
                        req.getVideoUrl(),
                        req.getOutputFormat(),
                        req.getStyle(),
                        req.getDuration(),
                        req.getModel())
                : mediaToDoc.documentAndScriptFromTranscript(
                        req.getContent(),
                        req.getOutputFormat(),
                        req.getStyle(),
                        req.getDuration(),
                        req.getModel());

        String content = """
                ## 转写整理文档

                %s

                ---

                %s
                """.formatted(result.document(), result.script());
        creditsService.deduct(resolveUserId(auth), 15, "video_rip", "视频仿做分析");
        return ResponseEntity.ok(new AgentResponse(content, result.modelLabel() + " + media2doc-workflow"));
    }

    @PostMapping("/media-to-doc")
    public ResponseEntity<AgentResponse> generateMediaDoc(@RequestBody AgentRequest req) {
        if (isBlank(req.getContent())) {
            return ResponseEntity.badRequest().body(new AgentResponse("视频转写文本/字幕不能为空", null));
        }
        MediaToDocService.OutputMode mode = MediaToDocService.OutputMode.from(req.getTargetMode());
        String content = mediaToDoc.transcriptToMode(req.getContent(), mode, req.getOutputFormat(), req.getModel());
        return ResponseEntity.ok(new AgentResponse(
                content,
                deepSeek.resolveModel(req.getModel()) + " + " + mode.label()));
    }

    @PostMapping("/video-storyboard")
    public ResponseEntity<AgentResponse> generateVideoStoryboard(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        boolean hasTopic = !isBlank(req.getTopic());
        boolean hasScript = !isBlank(req.getScript());
        if (!hasTopic && !hasScript) {
            return ResponseEntity.badRequest()
                    .body(new AgentResponse("主题和脚本至少要提供一项", null));
        }
        String system = """
                你是一位短视频分镜师，擅长把口播脚本拆解成可执行的拍摄分镜。

                【分镜要求】
                1. 镜头数 6-12 个，总时长 45-90 秒
                2. 单镜头时长 3-12 秒
                3. 景别要有节奏变化：大全/全/中/近/特写
                4. 第一个镜头必须是钩子，最后一个镜头必须有 CTA
                5. 至少 2 个镜头使用对比构图（分屏/前后对比/反转）

                【输出格式】
                严格输出 JSON，不要加任何注释或代码块符号，格式如下：
                {
                  "segments": [
                    {
                      "index": 1,
                      "duration": 8,
                      "shot_type": "近景",
                      "scene": "画面描述：主体+动作+道具+构图，要具体，便于拍摄或 AI 生成",
                      "voiceover": "对应的口播原文，不要篡改",
                      "subtitle": "简化版口播，≤14字",
                      "notes": "转场/特效/表演备注，没有则留空字符串"
                    }
                  ]
                }
                """;
        StringBuilder user = new StringBuilder();
        if (hasTopic) user.append("视频主题: ").append(req.getTopic()).append("\n");
        if (!isBlank(req.getStyle())) user.append("风格: ").append(req.getStyle()).append("\n");
        if (!isBlank(req.getDuration())) user.append("目标时长: ").append(req.getDuration()).append("\n");
        if (hasScript) user.append("\n[已有口播脚本]\n").append(req.getScript());
        String content = deepSeek.chat(system, user.toString(), req.getModel());
        // 清洗 JSON：去掉可能的代码块标记
        String cleaned = content.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replaceAll("```", "").trim();
        creditsService.deduct(resolveUserId(auth), 12, "drama_script", req.getTopic());
        return ResponseEntity.ok(new AgentResponse(cleaned, deepSeek.resolveModel(req.getModel())));
    }

    @PostMapping("/video-title")
    public ResponseEntity<AgentResponse> generateVideoTitle(@RequestBody AgentRequest req) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("视频主题不能为空", null));
        }
        String system = """
                你是抖音/视频号/小红书视频的发布文案专家, 专做保险科普方向。请根据视频主题, 生成"标题候选 + 发布文案"。

                【输出结构】
                严格按以下两段输出:

                ### 视频标题候选(5 个)

                1. xxx
                2. xxx
                3. xxx
                4. xxx
                5. xxx

                ### 发布文案候选(3 段)

                **方案一**:
                <50-80 字的发布文案, 末尾带 3-5 个话题标签 #xxx>

                **方案二**:
                <同上, 但风格不同>

                **方案三**:
                <同上, 但风格不同>

                【标题规则】
                - 每个 ≤ 25 字
                - 必须有钩子: 数字 / 痛点 / 反问 / 利益点 二选一
                - 加 1-2 个 emoji, 不堆砌
                - 5 个标题在风格上要有差异: 干货/吐槽/共鸣/恐惧/反转

                【发布文案规则】
                - 50-80 字, 比标题更有信息密度
                - 结尾必须有 3-5 个话题标签 #xxx
                - 三段方案要分别对应: 干货导向 / 情绪导向 / 互动导向
                """;
        StringBuilder user = new StringBuilder("视频主题: ").append(req.getTopic());
        if (!isBlank(req.getStyle())) user.append("\n风格: ").append(req.getStyle());
        String content = deepSeek.chat(system, user.toString(), req.getModel());
        return ResponseEntity.ok(new AgentResponse(content, deepSeek.resolveModel(req.getModel())));
    }

    @PostMapping("/video-cover")
    public ResponseEntity<AgentResponse> generateVideoCover(@RequestBody AgentRequest req) {
        if (isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(new AgentResponse("视频主题不能为空", null));
        }
        String system = """
                你是一位短视频封面设计师, 擅长抖音/视频号风格的竖版封面。请根据视频主题, 生成一张"竖版 9:16 大字封面"的图片提示词。

                【封面设计原则】
                - 比例: 9:16 竖版(1080x1920)
                - 必须有醒目的"钩子文字"区域, 字号占画面 1/3, 在画面上半部
                - 钩子文字要短: 4-10 字, 带强冲击力(数字/反问/痛点/承诺)
                - 视觉风格按情绪自动匹配:
                  * 揭秘/避坑 → 深色背景 + 红黄高亮文字 + 警示元素
                  * 干货/教学 → 浅色背景 + 大字 + 清晰图标
                  * 共鸣/温暖 → 暖色调 + 真实场景照片感 + 柔和光线
                  * 反转/吐槽 → 对比构图(左vs右, 前vs后)
                - 25-40 岁中国普通人形象(如有)
                - 严禁出现真实品牌 logo、二维码、水印
                - 不要出现 AI 训练痕迹(夸张笑容、多手指等)

                【输出格式】严格按以下格式, 不要使用代码块, 不要其他说明:

                [钩子文字]
                <封面上展示的中文短文字, 4-10 字, 这是图上显示的字>

                [中文描述]
                <2-3 句中文画面说明, 给用户预览用>

                [IMAGE_PROMPT]
                <一段完整的英文 prompt, 用于图片生成 API。必须明确指出: vertical 9:16 portrait composition; large bold Chinese title text "{钩子文字}" prominent in upper portion; 主体描述; 风格关键词; 光线; 色调; no watermark, no QR code, no real brand logo>
                """;
        String user = "视频主题: " + req.getTopic()
                + (isBlank(req.getStyle()) ? "" : "\n风格: " + req.getStyle());
        String content = deepSeek.chat(system, user, req.getModel());

        String prompt = extractImagePrompt(content);
        // 封面按竖版比例生成，ImageGenerationService 会转换为 9:16。
        boolean useSeedream = "seedream".equalsIgnoreCase(req.getImageProvider());
        String imageUrl = useSeedream
                ? imageGeneration.generateSeedream(prompt)
                : imageGeneration.generate(prompt, "1440x2560");

        AgentResponse resp = new AgentResponse(content, deepSeek.resolveModel(req.getModel()));
        resp.setImageUrl(imageUrl);
        resp.setModel(deepSeek.resolveModel(req.getModel()) + " + "
                + (useSeedream ? imageGeneration.seedreamModelLabel() : imageGeneration.modelLabel()));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/video-generate")
    public ResponseEntity<AgentResponse> generateVideo(@RequestBody AgentRequest req) {
        AgentResponse resp = libTv.generateVideo(req);
        return ResponseEntity.ok(resp);
    }

    /**
     * AtlasCloud Seedance 口播视频生成。
     * 流程：DeepSeek 拆分脚本 → 逐段调用 Seedance API → 返回所有片段 URL。
     */
    @PostMapping("/video-generate-seedance")
    public ResponseEntity<?> generateVideoSeedance(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        boolean hasSegments = req.getStoryboardSegments() != null && !req.getStoryboardSegments().isEmpty();
        if (!hasSegments && isBlank(req.getScript()) && isBlank(req.getTopic())) {
            return ResponseEntity.badRequest().body(Map.of("error", "口播脚本不能为空"));
        }
        String script = !isBlank(req.getScript()) ? req.getScript().trim() : (req.getTopic() != null ? req.getTopic().trim() : "");
        try {
            List<SeedanceService.SegmentResult> results;
            java.util.List<java.util.Map<String, Object>> sbSegs = req.getStoryboardSegments();
            String ratio      = req.getVideoRatio();
            String resolution = req.getVideoResolution();
            if (sbSegs != null && !sbSegs.isEmpty()) {
                // 前端已编辑好的分镜段，直接使用，跳过 DeepSeek 拆分
                List<SeedanceService.Segment> segs = new ArrayList<>();
                for (java.util.Map<String, Object> m : sbSegs) {
                    String voiceover = String.valueOf(m.getOrDefault("voiceover", ""));
                    int dur = ((Number) m.getOrDefault("duration", 8)).intValue();
                    String prompt = String.valueOf(m.getOrDefault("prompt", "A professional insurance advisor talking to camera, close-up, natural expression"));
                    segs.add(new SeedanceService.Segment(voiceover, prompt, Math.max(3, Math.min(10, dur))));
                }
                results = seedance.generateSegmentsDirect(segs, req.getCharacterImageUrl(), req.getBackgroundImageUrl(), ratio, resolution);
            } else {
                results = seedance.generateSegments(
                        script,
                        req.getCharacterImageUrl(),
                        req.getBackgroundImageUrl(),
                        req.getStyle(),
                        ratio,
                        resolution);
            }
            List<Map<String, Object>> segments = new ArrayList<>();
            for (SeedanceService.SegmentResult r : results) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("index", r.index());
                m.put("script", r.script());
                m.put("durationEstimate", r.durationEstimate());
                m.put("videoUrl", r.videoUrl());
                segments.add(m);
            }
            creditsService.deduct(resolveUserId(auth), 80, "video_render", "口播视频成片");
            return ResponseEntity.ok(Map.of("segments", segments, "total", results.size()));
        } catch (Exception e) {
            log.error("[Seedance] 视频生成失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/video-merge")
    public ResponseEntity<AgentResponse> mergeVideo(@RequestBody AgentRequest req) {
        return ResponseEntity.ok(libTv.mergeVideos(req));
    }

    @PostMapping("/libtv/session-videos")
    public ResponseEntity<AgentResponse> listLibTvSessionVideos(@RequestBody AgentRequest req) {
        return ResponseEntity.ok(libTv.listSessionVideos(req));
    }

    @PostMapping("/libtv/new-project")
    public ResponseEntity<AgentResponse> createLibTvProject() {
        return ResponseEntity.ok(libTv.changeProject());
    }

    private static String buildDefaultImageSystemPrompt() {
        return """
                你是一位小红书风格的视觉设计师。请根据以下内容生成一张封面图或配图的详细图片生成提示词。

                【图片风格规则】
                根据情绪基调自动匹配风格:
                - 揭秘/内幕 → 深色背景 + 红色高亮文字 + 探照灯光效, 氛围感强烈
                - 避坑/后悔 → 暖黄/橙色系, 卡通人物踩坑/捂脸表情, 亲切感
                - 反讽/打脸 → 对比分割构图(左边踩坑 vs 右边正确), 扁平插画风
                - 安心/种草 → 浅蓝/薄荷绿背景, 简洁卡片式排版, 有信任感
                - 觉醒/反思 → 女性独立视角, 人物望向远方, 光线柔和温暖

                【构图要求】
                - 尺寸比例: 3:4 (小红书封面标准比例)
                - 必须有标题文字区域(留白或文字叠加)
                - 人物(如有)为 25-35 岁中国女性, 亲切自然, 非广告感
                - 场景道具根据险种选择: 保险合同/手机APP界面/医院场景/家庭场景

                【输出格式】
                严格按以下格式输出, 不要使用代码块, 不要添加额外说明:

                [中文描述]
                <用于给用户看的图片说明, 中文, 2-4 句>

                [IMAGE_PROMPT]
                <用于图片生成 API 的英文提示词。必须是一段完整英文 prompt, 包含主体、场景、风格、构图、光线、色彩、画幅, 避免水印、二维码、真实品牌 logo。>
                """;
    }

    private static String buildTemplatedImageSystemPrompt(ImageTemplateService.Template template) {
        return """
                你是一位小红书视觉设计师。用户选择了一个固定的视觉风格模板, 请基于该模板的设计语言, 结合用户的主题, 生成一段图片生成 prompt。

                【必须严格遵循的视觉模板】
                %s

                【你的任务】
                1. 把用户给的主题(画面主题/内容/风格说明), 翻译成符合上述视觉模板的图片场景。
                2. 主题内容必须填进画面里(如关键词、人物、场景、文字), 不能空有风格、没有内容。
                3. 模板里规定的风格、配色、版式、字体感、画幅比例必须保留。
                4. 严禁出现水印、二维码、真实品牌 logo、AI 训练痕迹(多手指等)。

                【输出格式】严格按以下格式, 不要使用代码块, 不要添加额外说明:

                [中文描述]
                <2-3 句话给用户预览, 说清楚画面里有什么>

                [IMAGE_PROMPT]
                <一段完整英文 prompt, 同时编码模板风格特征 + 用户主题内容, 包含: composition, layout, color palette, typography style, key text content (in Chinese, transliterated as is in the prompt), illustration style. 必须包含 vertical 9:16 portrait composition。>
                """.formatted(template.getDescription());
    }

    // ─── 爆款拆解 ───────────────────────────────────────────────────────────

    @PostMapping("/viral-xhs")
    public ResponseEntity<AgentResponse> analyzeViralXhs(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isBlank(req.getVideoUrl())) {
            return ResponseEntity.badRequest().body(new AgentResponse("请粘贴小红书笔记链接", null));
        }
        XhsNote note;
        try {
            note = xhsExtract.extract(req.getVideoUrl());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AgentResponse("笔记提取失败：" + e.getMessage(), null));
        }

        // 如果是视频笔记且有直链，尝试用 Ark API 转写视频内容
        String transcript = null;
        String warning = null;
        if (!isBlank(note.getVideoUrl())) {
            try {
                transcript = mediaToDoc.transcribeVideoUrl(note.getVideoUrl());
            } catch (Exception e) {
                warning = "⚠️ 视频转写失败，已自动降级为文案分析模式。";
            }
        }

        String system = transcript != null
                ? """
                  你是一位专业的小红书爆款内容分析师，专注于保险行业内容创作辅导。
                  你的任务是对一篇真实的小红书笔记（视频类型）做深度爆款结构拆解。
                  已提供视频转写内容，请结合转写原文和笔记数据做具体、可操作的分析，每条结论必须引用原文。
                  """
                : """
                  你是一位专业的小红书爆款内容分析师，专注于保险行业内容创作辅导。
                  你的任务是对一篇真实的小红书笔记做深度爆款结构拆解，帮助保险经纪人学习和复用爆款套路。
                  分析要具体、可操作，每条结论必须结合笔记原文给出依据，不要泛泛而谈。
                  """;

        String content = deepSeek.chat(system, buildViralXhsPrompt(note, transcript), req.getModel());
        
        // 如果有警告信息，添加到结果开头
        if (warning != null) {
            content = warning + "\n\n---\n\n" + content;
        }
        
        String modelLabel = deepSeek.resolveModel(req.getModel()) + (transcript != null ? " + 视频转写" : " + 文案分析");
        creditsService.deduct(resolveUserId(auth), 6, "viral_xhs", "拆解小红书爆款");
        return ResponseEntity.ok(new AgentResponse(content, modelLabel));
    }

    private static String buildViralXhsPrompt(XhsNote note, String transcript) {
        String tags = note.getTags() == null || note.getTags().isEmpty()
                ? "无"
                : String.join(" / ", note.getTags());
        String stats = String.format("点赞 %s | 收藏 %s | 评论 %s | 分享 %s",
                blankFallback(note.getLikedCount(), "未知"),
                blankFallback(note.getCollectedCount(), "未知"),
                blankFallback(note.getCommentCount(), "未知"),
                blankFallback(note.getShareCount(), "未知"));

        String contentSection = transcript != null
                ? "【视频转写内容】\n" + transcript
                : "【正文内容】\n" + blankFallback(note.getContent(), "（正文为空，视频类型笔记）");

        String section3 = transcript != null
                ? """
                  ## 三、视频内容结构拆解
                  基于视频转写内容逐段分析：
                  - **开头3秒钩子**（引用原文）→ 作用与技巧
                  - **核心干货段**（引用原文）→ 信息组织方式
                  - **互动/评论引导设计**
                  - **收尾 CTA**
                  """
                : """
                  ## 三、正文结构拆解
                  按笔记实际段落逐段分析，说明每段的功能和技巧：
                  - 开场钩子（引用原文）→ 分析作用
                  - 核心干货段（引用原文）→ 信息组织方式
                  - 互动/评论引导设计
                  - 收尾 CTA
                  """;

        return ("""
                请对以下小红书笔记做全面的爆款结构拆解：

                【笔记基本信息】
                标题：%s
                类型：%s
                作者：%s
                发布时间：%s
                互动数据：%s
                话题标签：%s

                %s

                ---

                请按以下结构输出完整分析报告（用 Markdown 格式）：

                ## 一、爆款潜力评分
                用表格打分（1-10分）并各用一句话说明理由：
                标题吸引力 / 内容干货度 / 情绪共鸣感 / 互动引导力 / 综合评分

                ## 二、标题拆解
                - **钩子类型**：（数字型/反问型/痛点型/反转型/人设型，结合原标题说明）
                - **核心情绪词**：列出并分析作用
                - **人设设定**：用了什么人设，为什么有效
                - **为什么会点进来**：一句话总结核心吸引力

                """ + section3 + """

                ## 四、爆款公式提炼
                总结这篇笔记的可复制公式，格式：
                **[人设前置] + [情绪钩子] + [核心场景] + [干货结构] + [互动收尾]**
                并用一句话说明最核心的成功要素。

                ## 五、保险创作复用建议
                基于这篇笔记的爆款结构，给出 3 个可直接套用的保险选题，每个包含：
                - 标题（套用同款钩子公式）
                - 目标人群
                - 核心角度（为什么能爆）
                """).formatted(
                blankFallback(note.getTitle(), "（无标题）"),
                blankFallback(note.getType(), "未知"),
                blankFallback(note.getAuthorName(), "未知"),
                blankFallback(note.getPublishTime(), "未知"),
                stats,
                tags,
                contentSection
        );
    }

    @PostMapping("/viral-douyin")
    public ResponseEntity<AgentResponse> analyzeViralDouyin(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isBlank(req.getVideoUrl())) {
            return ResponseEntity.badRequest().body(new AgentResponse("请粘贴抖音作品链接", null));
        }
        DouyinNote note;
        try {
            note = douyinExtract.extract(req.getVideoUrl());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AgentResponse("作品提取失败：" + e.getMessage(), null));
        }

        // 转写策略：优先用 CDN 直链，其次直传 workUrl 给 Ark（字节系可能支持），最后退回纯文案分析
        String transcript = null;
        String warning = null;
        String urlToTranscribe = !isBlank(note.getVideoDownloadUrl())
                ? note.getVideoDownloadUrl()
                : note.getWorkUrl();
        if (!isBlank(urlToTranscribe)) {
            try {
                transcript = mediaToDoc.transcribeVideoUrl(urlToTranscribe);
            } catch (Exception e) {
                log.warn("[viral-douyin] 视频转写失败: {}", e.getMessage());
                warning = "⚠️ 视频内容转写失败，已自动降级为文案分析模式。";
            }
        } else {
            warning = "ℹ️ 未获取到作品链接，将仅使用文案分析模式。";
        }

        String system = transcript != null
                ? """
                  你是一位专业的抖音爆款内容分析师，专注于保险行业内容创作辅导。
                  你的任务是对一条真实的抖音作品做深度爆款结构拆解，帮助保险经纪人学习和复用爆款套路。
                  分析要具体、可操作，每条结论必须结合视频原文和转写内容给出依据，不要泛泛而谈。
                  """
                : """
                  你是一位专业的抖音爆款内容分析师，专注于保险行业内容创作辅导。
                  你的任务是对一条真实的抖音作品做深度爆款结构拆解，帮助保险经纪人学习和复用爆款套路。
                  分析要具体、可操作，每条结论必须结合视频标题/文案原文给出依据，不要泛泛而谈。
                  """;
        
        String content = deepSeek.chat(system, buildViralDouyinPrompt(note, transcript), req.getModel());
        
        // 如果有警告信息，添加到结果开头
        if (warning != null) {
            content = warning + "\n\n---\n\n" + content;
        }
        
        String modelLabel = deepSeek.resolveModel(req.getModel()) + (transcript != null ? " + 视频转写" : " + 文案分析");
        creditsService.deduct(resolveUserId(auth), 15, "viral_douyin", "拆解抖音爆款");
        return ResponseEntity.ok(new AgentResponse(content, modelLabel));
    }

    private static String buildViralDouyinPrompt(DouyinNote note, String transcript) {
        String topics = note.getTopics() == null || note.getTopics().isEmpty()
                ? "无"
                : String.join(" / ", note.getTopics().stream().map(t -> "#" + t).toList());
        String stats = String.format("点赞 %s | 评论 %s | 收藏 %s | 分享 %s | 粉丝 %s",
                blankFallback(note.getLikeCount(), "未知"),
                blankFallback(note.getCommentCount(), "未知"),
                blankFallback(note.getCollectCount(), "未知"),
                blankFallback(note.getShareCount(), "未知"),
                blankFallback(note.getFollowerCount(), "未知"));

        String transcriptSection = transcript != null
                ? "\n【视频转写内容】\n" + transcript + "\n"
                : "";

        String section3 = transcript != null
                ? """
                  ## 三、内容结构拆解
                  基于视频转写内容逐段分析：
                  - **开头3秒钩子**（引用原文）→ 作用与技巧
                  - **中间干货段** → 信息组织方式与节奏设计
                  - **结尾互动引导** → CTA 设计是否有效
                  """
                : """
                  ## 三、文案结构拆解
                  仅基于文案文字进行分析（无视频正片），不要虚构视频内容：
                  - **开场钩子**：文案开头如何抓注意力（结合原文逐句分析）
                  - **核心信息点**：文案传递了哪些干货或观点，如何组织
                  - **互动/评论引导**：文案中是否有引导评论、收藏、转发的设计
                  """;

        return ("""
                请对以下抖音作品做全面的爆款结构拆解：

                【作品基本信息】
                标题/文案：%s
                类型：%s
                作者：%s（%s）
                发布时间：%s
                互动数据：%s
                话题标签：%s
                %s
                ---

                请按以下结构输出完整分析报告（用 Markdown 格式）：

                ## 一、爆款潜力评分
                用表格打分（1-10分）并各用一句话说明理由：
                标题吸引力 / 内容干货度 / 情绪共鸣感 / 互动引导力 / 综合评分

                ## 二、标题/文案拆解
                - **钩子类型**：（数字型/反问型/痛点型/反转型/人设型，结合原文说明）
                - **核心情绪词**：列出并分析作用
                - **人设设定**：用了什么人设，为什么有效
                - **抖音流量逻辑**：分析该文案如何契合抖音推荐算法（话题标签策略/互动率设计/完播钩子）

                """ + section3 + """

                ## 四、爆款公式提炼
                总结可复制公式：
                **[人设] + [情绪钩子] + [核心场景] + [抖音节奏设计] + [互动收尾]**
                并说明最核心的成功要素。

                ## 五、保险创作复用建议
                给出 3 个可直接套用到抖音的保险选题，每个包含：
                - 视频标题（套用同款钩子公式）
                - 视频节奏方案（开头3秒 / 中间干货 / 结尾CTA）
                - 为什么适合抖音
                """).formatted(
                blankFallback(note.getTitle(), "（无标题）"),
                blankFallback(note.getWorkType(), "未知"),
                blankFallback(note.getNickname(), "未知"),
                blankFallback(note.getUserDesc(), ""),
                blankFallback(note.getPublishTime(), "未知"),
                stats,
                topics,
                transcriptSection
        );
    }

    // ─── 知识库 RAG 问答 ────────────────────────────────────────────────────

    @PostMapping("/kb-faq")
    public ResponseEntity<AgentResponse> answerFaq(@RequestBody AgentRequest req) {
        if (isBlank(req.getQuestion())) {
            return ResponseEntity.badRequest().body(new AgentResponse("问题不能为空", null));
        }
        return ResponseEntity.ok(new AgentResponse(
                knowledgeQa.answer(req.getQuestion(), "faq_qa"),
                "deepseek-chat + FAQ知识库RAG"));
    }

    @PostMapping("/kb-claims")
    public ResponseEntity<AgentResponse> answerClaims(@RequestBody AgentRequest req) {
        if (isBlank(req.getQuestion())) {
            return ResponseEntity.badRequest().body(new AgentResponse("问题不能为空", null));
        }
        return ResponseEntity.ok(new AgentResponse(
                knowledgeQa.answer(req.getQuestion(), "claim_cases"),
                "deepseek-chat + 理赔案例RAG"));
    }

    @PostMapping("/kb-products")
    public ResponseEntity<AgentResponse> answerProducts(@RequestBody AgentRequest req) {
        if (isBlank(req.getQuestion())) {
            return ResponseEntity.badRequest().body(new AgentResponse("问题不能为空", null));
        }
        return ResponseEntity.ok(new AgentResponse(
                knowledgeQa.answer(req.getQuestion(), "insurance_products"),
                "deepseek-chat + 险种知识库RAG"));
    }

    @PostMapping("/kb-tips")
    public ResponseEntity<AgentResponse> answerTips(@RequestBody AgentRequest req) {
        if (isBlank(req.getQuestion())) {
            return ResponseEntity.badRequest().body(new AgentResponse("问题不能为空", null));
        }
        return ResponseEntity.ok(new AgentResponse(
                knowledgeQa.answer(req.getQuestion(), "insurance_tips"),
                "deepseek-chat + 投保提示RAG"));
    }

    @PostMapping("/kb-coverage")
    public ResponseEntity<AgentResponse> answerCoverage(@RequestBody AgentRequest req) {
        if (isBlank(req.getQuestion())) {
            return ResponseEntity.badRequest().body(new AgentResponse("问题不能为空", null));
        }
        return ResponseEntity.ok(new AgentResponse(
                knowledgeQa.answer(req.getQuestion(), "coverage_details"),
                "deepseek-chat + 保障责任RAG"));
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankFallback(String s, String fallback) {
        return isBlank(s) ? fallback : s;
    }

    private static String extractImagePrompt(String content) {
        if (content == null) return "";
        String marker = "[IMAGE_PROMPT]";
        int markerIdx = content.indexOf(marker);
        if (markerIdx >= 0) {
            return cleanPrompt(content.substring(markerIdx + marker.length()));
        }

        String[] legacyMarkers = {"英文 Prompt 版", "英文 Prompt", "English Prompt"};
        for (String legacyMarker : legacyMarkers) {
            int idx = content.indexOf(legacyMarker);
            if (idx >= 0) return cleanPrompt(content.substring(idx + legacyMarker.length()));
        }

        String[] lines = content.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (!line.isEmpty()) return cleanPrompt(line);
        }
        return content;
    }

    private static String cleanPrompt(String prompt) {
        if (prompt == null) return "";
        String out = prompt.trim();
        out = out.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "").trim();
        out = out.replaceAll("^[：:：\\-\\s]+", "").trim();
        out = out.replaceAll("^(\\d+[.)、]\\s*)", "").trim();
        if ((out.startsWith("\"") && out.endsWith("\"")) || (out.startsWith("'") && out.endsWith("'"))) {
            out = out.substring(1, out.length() - 1).trim();
        }
        return out;
    }

    private static final ExecutorService IMAGE_POOL = Executors.newFixedThreadPool(9);

    @PostMapping("/xhs-batch-images")
    public ResponseEntity<List<Map<String, Object>>> generateXhsBatchImages(@RequestBody AgentRequest req,
            @RequestHeader(value = "Authorization", required = false) String auth) {
        if (isBlank(req.getContent())) {
            return ResponseEntity.badRequest().body(List.of(Map.of("error", "文章内容不能为空")));
        }

        int count = (req.getImageCount() != null && req.getImageCount() >= 1 && req.getImageCount() <= 9)
                ? req.getImageCount() : 3;
        String ratio = isBlank(req.getImageRatio()) ? "3:4" : req.getImageRatio().trim();
        boolean useSeedream = "seedream".equalsIgnoreCase(req.getImageProvider());

        // 让 DeepSeek 一次性生成 count 个提示词
        String systemPrompt = """
                你是一位小红书视觉配图专家，擅长把文章内容转化为高质量图片提示词。

                任务：根据用户提供的文章内容，生成 %d 个图片提示词，每张图对应文章中一个核心场景或要点。

                要求：
                1. 按文章逻辑顺序分配场景，不要重复，每张图有独立的视觉主题
                2. 图片风格：小红书主流配图风（清新、有质感、适合插图或实拍风格）
                3. 比例：%s（必须在 prompt 中注明）
                4. 人物（如有）为 25-35 岁中国女性，自然亲切
                5. 不要出现水印、品牌 logo、二维码

                输出格式（严格按此，共 %d 组，每组格式一致）：

                [IMAGE_1]
                <2 句中文，描述这张图表达的内容和画面感>
                [PROMPT_1]
                <一段完整英文图片生成 prompt，包含主体、场景、风格、光线、色彩，结尾注明比例如 "vertical 3:4 portrait" 或 "square 1:1">

                [IMAGE_2]
                ...
                [PROMPT_2]
                ...
                """.formatted(count, ratio, count);

        String userMsg = "文章内容如下，请生成 " + count + " 张配图的提示词：\n\n" + req.getContent().trim();
        String llmOut;
        try {
            llmOut = deepSeek.chat(systemPrompt, userMsg, req.getModel());
        } catch (Exception e) {
            log.error("[XhsBatchImages] DeepSeek 生成提示词失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(List.of(Map.of("error", "生成提示词失败: " + e.getMessage())));
        }

        // 解析提示词
        List<String> descs = new ArrayList<>();
        List<String> prompts = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String descMarker = "[IMAGE_" + i + "]";
            String promptMarker = "[PROMPT_" + i + "]";
            String nextDescMarker = "[IMAGE_" + (i + 1) + "]";

            int descStart = llmOut.indexOf(descMarker);
            int promptStart = llmOut.indexOf(promptMarker);
            int nextBlock = llmOut.indexOf(nextDescMarker);

            String desc = "";
            String prompt = "";
            if (descStart >= 0 && promptStart > descStart) {
                desc = llmOut.substring(descStart + descMarker.length(), promptStart).trim();
            }
            if (promptStart >= 0) {
                int end = nextBlock > promptStart ? nextBlock : llmOut.length();
                prompt = cleanPrompt(llmOut.substring(promptStart + promptMarker.length(), end));
            }
            descs.add(desc);
            prompts.add(prompt);
        }

        // 并行生图
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            final String prompt = prompts.get(i);
            final String desc = descs.get(i);
            if (isBlank(prompt)) {
                futures.add(CompletableFuture.completedFuture(
                        Map.of("index", idx + 1, "description", desc, "error", "提示词为空")));
                continue;
            }
            CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    String url = useSeedream
                            ? imageGeneration.generateSeedream(prompt)
                            : imageGeneration.generate(prompt);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("index", idx + 1);
                    m.put("description", desc);
                    m.put("imageUrl", url);
                    return m;
                } catch (Exception e) {
                    log.warn("[XhsBatchImages] 第 {} 张生图失败: {}", idx + 1, e.getMessage());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("index", idx + 1);
                    m.put("description", desc);
                    m.put("error", e.getMessage());
                    return m;
                }
            }, IMAGE_POOL);
            futures.add(future);
        }

        List<Map<String, Object>> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();
        creditsService.deduct(resolveUserId(auth), 5, "xhs_images", "小红书配图");
        return ResponseEntity.ok(results);
    }

    private long resolveUserId(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) throw new IllegalArgumentException("未登录");
        return authService.userIdByToken(auth.substring(7));
    }

    private static List<Map<String, String>> toWarningMaps(List<XhsComplianceService.Violation> violations) {
        if (violations == null) return List.of();
        List<Map<String, String>> result = new ArrayList<>();
        for (XhsComplianceService.Violation v : violations) {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("phrase", v.phrase());
            map.put("category", v.category());
            map.put("reason", v.reason());
            map.put("suggestion", v.suggestion());
            result.add(map);
        }
        return result;
    }
}
