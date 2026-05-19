package com.insurance.agent.service;

import com.insurance.agent.dto.XiangshengResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class XiangshengService {

    private static final Logger log = LoggerFactory.getLogger(XiangshengService.class);

    private final DeepSeekService deepSeek;

    public XiangshengService(DeepSeekService deepSeek) {
        this.deepSeek = deepSeek;
    }

    /**
     * AI智能推荐：分析主题，推荐最佳维度组合。
     */
    public Map<String, Object> recommend(String topic, String model) {
        String system = XiangshengPromptTemplates.recommendSystem();
        String user = XiangshengPromptTemplates.recommendUser(topic);

        log.info("[相声-AI推荐] 选题={}", topic);
        String result = deepSeek.chat(system, user, model);

        // 解析JSON结果
        try {
            String json = result.trim();
            // 提取JSON部分（可能被markdown代码块包裹）
            if (json.contains("```")) {
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    json = json.substring(start, end + 1);
                }
            }
            // 简单解析JSON
            Map<String, Object> parsed = parseJson(json);
            parsed.put("raw", result);
            return parsed;
        } catch (Exception e) {
            log.warn("[相声-AI推荐] JSON解析失败，返回原始结果", e);
            return Map.of("raw", result,
                    "reason", "推荐结果解析失败，请手动选择维度");
        }
    }

    /**
     * 简单JSON解析（不引入额外依赖）
     */
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        // 移除花括号
        String content = json.trim();
        if (content.startsWith("{")) content = content.substring(1);
        if (content.endsWith("}")) content = content.substring(0, content.length() - 1);

        // 按逗号分割（简单处理，不处理嵌套引号内的逗号）
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escapeNext = false;

        for (char c : content.toCharArray()) {
            if (escapeNext) {
                current.append(c);
                escapeNext = false;
                continue;
            }
            if (c == '\\') {
                escapeNext = true;
                current.append(c);
                continue;
            }
            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (c == ',' && !inString) {
                addKeyValue(map, current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            addKeyValue(map, current.toString().trim());
        }

        return map;
    }

    private void addKeyValue(Map<String, Object> map, String pair) {
        int colonIdx = pair.indexOf(':');
        if (colonIdx < 0) return;

        String key = pair.substring(0, colonIdx).trim();
        String value = pair.substring(colonIdx + 1).trim();

        // 移除引号
        if (key.startsWith("\"") && key.endsWith("\"")) {
            key = key.substring(1, key.length() - 1);
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }

        map.put(key, value);
    }

    /**
     * 阶段一：台词创作。根据用户选择的多维度生成相声台词。
     */
    public Stage1Result stage1(String topic, String hookType, String structure,
                                String emotionArc, String audience,
                                String topicDirection, String toneStyle, Integer duration, String model) {
        String system = XiangshengPromptTemplates.stage1System(
                hookType, structure, emotionArc, audience, topicDirection, toneStyle, duration);
        String user = XiangshengPromptTemplates.stage1User(topic);

        String dimDesc = String.format("钩子=%s 结构=%s 情绪=%s 受众=%s 方向=%s",
                hookType, structure, emotionArc, audience, topicDirection);
        log.info("[相声-阶段一] 选题={} {}", topic, dimDesc);
        String dialogue = deepSeek.chat(system, user, model);

        return new Stage1Result(dimDesc, dialogue);
    }

    /**
     * 阶段二：Seedance 分镜剧本。将台词转为分镜。
     */
    public String stage2(String dialogue, Integer duration, String model) {
        String system = XiangshengPromptTemplates.stage2System(duration);
        String user = XiangshengPromptTemplates.stage2User(dialogue);

        log.info("[相声-阶段二] 台词长度={} 时长={}秒", dialogue.length(), duration);
        return deepSeek.chat(system, user, model);
    }

    /**
     * 阶段三：按组拆分提示词。将分镜按组拆分。
     */
    public String stage3(String storyboard, String model) {
        String system = XiangshengPromptTemplates.stage3System();
        String user = XiangshengPromptTemplates.stage3User(storyboard);

        log.info("[相声-阶段三] 分镜长度={}", storyboard.length());
        return deepSeek.chat(system, user, model);
    }

    /**
     * 一键全流程：台词 → 分镜 → 分组提示词。
     */
    public XiangshengResponse fullPipeline(String topic, String hookType, String structure,
                                            String emotionArc, String audience,
                                            String topicDirection, String toneStyle, Integer duration, String model) {
        Stage1Result s1 = stage1(topic, hookType, structure, emotionArc, audience, topicDirection, toneStyle, duration, model);
        String storyboard = stage2(s1.dialogue, duration, model);
        String groupPrompts = stage3(storyboard, model);

        return new XiangshengResponse(
                s1.styleName,
                s1.dialogue,
                storyboard,
                groupPrompts,
                deepSeek.resolveModel(model));
    }

    public record Stage1Result(String styleName, String dialogue) {}
}
