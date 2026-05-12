package com.insurance.agent.service;

import com.insurance.agent.dto.ProfileDto;
import com.insurance.agent.model.HotTopic;
import com.insurance.agent.repository.HotTopicRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 定时推送服务：每天 9:00 根据用户画像从热点库中筛选匹配的选题推送给用户。
 * 推送逻辑：从 hot_topics 表中取今日批次数据，按用户画像匹配排序，
 * 过滤已推送过的选题，记录到 topic_push_log。
 *
 * 扩展点：实际推送渠道（飞书消息 / App 通知等）可通过实现 PushConsumer 接入。
 */
@Service
public class TopicPushService {

    private static final Logger log = LoggerFactory.getLogger(TopicPushService.class);

    /** 每次推送最多条数 */
    private static final int MAX_PUSH_PER_USER = 5;

    private final HotTopicRepository repository;
    private final UserProfileService userProfileService;

    /** 可选的推送消费者，供实际推送渠道接入 */
    private final List<PushConsumer> consumers;

    public TopicPushService(HotTopicRepository repository,
                            UserProfileService userProfileService,
                            Optional<List<PushConsumer>> consumers) {
        this.repository = repository;
        this.userProfileService = userProfileService;
        this.consumers = consumers.orElseGet(List::of);
    }

    /** 每天 9:00 定时推送今日热点选题 */
    @Scheduled(cron = "0 0 9 * * ?")
    public void pushDailyTopics() {
        log.info("[TopicPushService] 开始每日定时推送...");
        LocalDate today = LocalDate.now();

        List<HotTopic> topics = repository.findByBatchDate(today);
        if (topics.isEmpty()) {
            log.warn("[TopicPushService] 今日热点库为空，跳过推送");
            return;
        }
        log.info("[TopicPushService] 今日热点库共 {} 条", topics.size());

        List<Long> userIds = userProfileService.getAllUserIds();
        if (userIds.isEmpty()) {
            log.warn("[TopicPushService] 无用户数据，跳过推送");
            return;
        }

        int totalPushed = 0;
        for (long userId : userIds) {
            try {
                List<HotTopic> pushed = pushForUser(userId, topics, today);
                totalPushed += pushed.size();
            } catch (Exception e) {
                log.error("[TopicPushService] 用户 {} 推送失败", userId, e);
            }
        }

        log.info("[TopicPushService] 定时推送完成，共推送 {} 条选题给 {} 个用户",
                totalPushed, userIds.size());
    }

    /**
     * 为指定用户执行单次推送。
     * 外部也可直接调用此方法对单个用户触发推送。
     *
     * @return 实际推送的选题列表
     */
    public List<HotTopic> pushForUser(long userId, List<HotTopic> allTopics, LocalDate batchDate) {
        ProfileDto profile = userProfileService.getProfile(userId);
        if (profile == null || profile.getId() == null) {
            log.debug("[TopicPushService] 用户 {} 无画像，跳过", userId);
            return List.of();
        }

        // 1. 查询已推送过的 topic_id
        Set<Long> pushedIds = new HashSet<>(repository.findPushedTopicIds(userId));

        // 2. 过滤未推送的选题，按匹配分排序
        List<ScoredTopic> candidates = new ArrayList<>();
        for (HotTopic t : allTopics) {
            if (pushedIds.contains(t.getId())) continue;
            int matchScore = computeMatchScore(t, profile);
            candidates.add(new ScoredTopic(t, matchScore));
        }

        candidates.sort((a, b) -> Integer.compare(b.totalScore(), a.totalScore()));

        // 3. 取前 N 条推送
        List<HotTopic> toPush = candidates.stream()
                .limit(MAX_PUSH_PER_USER)
                .map(s -> s.topic)
                .collect(Collectors.toList());

        // 4. 记录推送日志
        for (HotTopic t : toPush) {
            repository.savePushLog(userId, t.getId());
        }

        // 5. 通知推送消费者
        for (PushConsumer consumer : consumers) {
            try {
                consumer.onPush(userId, toPush);
            } catch (Exception e) {
                log.warn("[TopicPushService] 推送消费者执行失败: {}", e.getMessage());
            }
        }

        if (!toPush.isEmpty()) {
            log.info("[TopicPushService] 用户 {} 推送 {} 条选题", userId, toPush.size());
        }
        return toPush;
    }

    /** 重载：自动取今日日期 */
    public List<HotTopic> pushForUser(long userId) {
        List<HotTopic> topics = repository.findByBatchDate(LocalDate.now());
        return pushForUser(userId, topics, LocalDate.now());
    }

    // ========== 匹配打分 ==========

    /** 计算热点选题与用户画像的匹配分 */
    private int computeMatchScore(HotTopic topic, ProfileDto profile) {
        int score = 0;

        // 1. 主营险种匹配 +40/项
        List<String> products = profile.getPrimaryProducts();
        List<String> insTypes = topic.getInsuranceTypes();
        if (products != null && insTypes != null) {
            for (String p : products) {
                for (String t : insTypes) {
                    if (t.contains(p) || p.contains(t)) {
                        score += 40;
                        break;
                    }
                }
            }
        }

        // 2. 目标客群匹配 +30/项
        List<String> audiences = profile.getTargetAudiences();
        List<String> demos = topic.getDemographics();
        if (audiences != null && demos != null) {
            for (String a : audiences) {
                for (String d : demos) {
                    if (d.contains(a) || a.contains(d)) {
                        score += 30;
                        break;
                    }
                }
            }
        }

        // 3. 风格偏好匹配 +20
        String style = profile.getStyle();
        if (style != null && !style.isBlank()) {
            String why = topic.getWhyThisTopic();
            if (why != null && why.contains(style)) {
                score += 20;
            }
        }

        return score;
    }

    // ========== 推送消费者接口 ==========

    /** 推送消费者扩展点。实现此接口并注册为 Bean 即可接入实际推送渠道。 */
    @FunctionalInterface
    public interface PushConsumer {
        void onPush(long userId, List<HotTopic> topics);
    }

    // ========== 辅助类 ==========

    private static class ScoredTopic {
        final HotTopic topic;
        final int matchScore;

        ScoredTopic(HotTopic topic, int matchScore) {
            this.topic = topic;
            this.matchScore = matchScore;
        }

        int totalScore() {
            return (topic.getHeatScore() + topic.getAiScore()) * 3 / 10 + matchScore;
        }
    }
}
