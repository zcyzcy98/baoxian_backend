package com.insurance.agent.service;

import com.insurance.agent.dto.XiangshengResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class XiangshengService {

    private static final Logger log = LoggerFactory.getLogger(XiangshengService.class);

    private final DeepSeekService deepSeek;

    public XiangshengService(DeepSeekService deepSeek) {
        this.deepSeek = deepSeek;
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
