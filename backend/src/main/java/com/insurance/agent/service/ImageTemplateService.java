package com.insurance.agent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImageTemplateService {

    public static class Template {
        private final String id;
        private final String name;
        private final String thumbnail;
        private final String description;

        public Template(String id, String name, String thumbnail, String description) {
            this.id = id;
            this.name = name;
            this.thumbnail = thumbnail;
            this.description = description;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getThumbnail() { return thumbnail; }
        public String getDescription() { return description; }
    }

    private final Map<String, Template> templates;

    public ImageTemplateService() {
        Map<String, Template> map = new LinkedHashMap<>();
        map.put("mindmap", new Template(
                "mindmap",
                "思维导图风",
                "/templates/mindmap.png",
                "小红书思维导图风格信息图，保险主题，白色背景，左侧垂直排列3-4个红色加粗中文关键词作为主节点，每个主节点向右延伸多条细线连接子内容文字，重要词汇用黄色荧光笔效果标注，整体字体清晰工整像手写体，排版紧凑信息密度高，顶部有一个冲击感强的大标题，竖版9:16"
        ));
        map.put("cartoon", new Template(
                "cartoon",
                "卡通水彩风",
                "/templates/cartoon.png",
                "小红书科普信息图，卡通插画风格，保险主题，水彩晕染背景（米白+淡黄+淡蓝），手绘Q版人物和场景图标，图文混排，标题用毛笔体大字居中，正文分模块排列，每个模块配对应卡通小图，色彩柔和温暖，整体像一张手工绘制的科普海报，竖版9:16"
        ));
        this.templates = map;
    }

    public List<Template> list() {
        return new ArrayList<>(templates.values());
    }

    public Template find(String id) {
        if (id == null || id.isBlank()) return null;
        return templates.get(id.trim());
    }
}
