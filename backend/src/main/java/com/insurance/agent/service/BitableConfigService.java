package com.insurance.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.agent.dto.BitableConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class BitableConfigService {
    private static final Logger log = LoggerFactory.getLogger(BitableConfigService.class);

    private final Map<String, BitableConfig> configs = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private String configFile;

    public BitableConfigService(@Value("${bitable.config.path:bitable-config.json}") String configPath) {
        this.configFile = configPath;
        loadConfigs();
    }

    public List<BitableConfig> getAllConfigs() {
        return new ArrayList<>(configs.values());
    }

    public BitableConfig getConfig(String id) {
        return configs.get(id);
    }

    public BitableConfig getConfigByName(String name) {
        return configs.values().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    public List<BitableConfig> getConfigsByCategory(String category) {
        return configs.values().stream()
                .filter(c -> category == null || c.getCategory() == null || c.getCategory().equalsIgnoreCase(category))
                .collect(Collectors.toList());
    }

    public List<String> getCategories() {
        return configs.values().stream()
                .map(BitableConfig::getCategory)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public boolean saveConfig(BitableConfig config) {
        if (config.getId() == null || config.getId().isBlank()) {
            config.setId(UUID.randomUUID().toString());
        }
        configs.put(config.getId(), config);
        saveConfigs();
        log.info("保存表格配置: {}", config.getName());
        return true;
    }

    public boolean deleteConfig(String id) {
        if (configs.remove(id) != null) {
            saveConfigs();
            log.info("删除表格配置: {}", id);
            return true;
        }
        return false;
    }

    public boolean toggleActive(String id) {
        BitableConfig config = configs.get(id);
        if (config != null) {
            config.setActive(!config.isActive());
            saveConfigs();
            return true;
        }
        return false;
    }

    private void loadConfigs() {
        File file = new File(configFile);
        if (file.exists()) {
            try {
                List<BitableConfig> list = mapper.readValue(file,
                        new TypeReference<List<BitableConfig>>() {});
                for (BitableConfig config : list) {
                    configs.put(config.getId(), config);
                }
                log.info("加载了 {} 个表格配置", list.size());
            } catch (IOException e) {
                log.warn("读取配置文件失败: {}", e.getMessage());
            }
        }
        if (configs.isEmpty()) {
            log.info("未发现多维表格配置, 保持为空; 系统只使用用户配置的飞书数据源");
        }
    }

    private void saveConfigs() {
        try {
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(configFile), new ArrayList<>(configs.values()));
        } catch (IOException e) {
            log.error("保存配置文件失败: {}", e.getMessage());
        }
    }
}
