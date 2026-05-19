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
     * 阶段一：台词创作。根据用户选择的风格生成相声台词。
     */
    public Stage1Result stage1(String topic, int styleIndex, String model) {
        if (styleIndex < 1 || styleIndex > 6) styleIndex = 1;
        String styleName = XiangshengPromptTemplates.styleName(styleIndex);

        String system = XiangshengPromptTemplates.stage1System(styleIndex);
        String user = XiangshengPromptTemplates.stage1User(topic);

        log.info("[相声-阶段一] 选题={} 风格={}", topic, styleName);
        String dialogue = deepSeek.chat(system, user, model);

        return new Stage1Result(styleName, dialogue);
    }

    /**
     * 阶段二：Seedance 分镜剧本。将台词转为分镜。
     */
    public String stage2(String dialogue, String model) {
        String system = XiangshengPromptTemplates.stage2System();
        String user = XiangshengPromptTemplates.stage2User(dialogue);

        log.info("[相声-阶段二] 台词长度={}", dialogue.length());
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
    public XiangshengResponse fullPipeline(String topic, int styleIndex, String model) {
        Stage1Result s1 = stage1(topic, styleIndex, model);
        String storyboard = stage2(s1.dialogue, model);
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
