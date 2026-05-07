package com.insurance.agent.dto;

import java.util.List;

/**
 * 公众号文章. 通过抓取 mp.weixin.qq.com/s 链接的 SSR HTML 拿到.
 *
 * 跟 XhsNote 的角色对等, 用同一套改写流程 (NoteRewriteService).
 *
 * 不包含阅读量 / 点赞数 / 在看数 / 评论 - 这几项需要登录态 + 接口调用,
 * 直接抓 HTML 拿不到, 用户场景下不需要。
 */
public class WechatArticle {
    /** 微信内部三件套, 唯一定位一篇文章 */
    private String msgBiz;
    private String msgMid;
    private String msgIdx;

    private String url;
    private String type;            // "normal" 普通图文 / "image" 图片型 / "fail" 已失效
    private String title;
    private String digest;          // 摘要 (msg_desc)
    private String content;         // 正文纯文本
    private String contentHtml;     // 正文 HTML (#js_content innerHTML)
    private String publishTime;     // YYYY-MM-DD HH:mm:ss

    /** 公众号信息 */
    private String accountName;     // nickname
    private String accountId;       // wechatId / username
    private String accountAvatar;   // hd_head_img

    /** 媒体资源 */
    private String cover;           // 封面图 (msg_cdn_url)
    private List<String> imageUrls; // 正文里所有图片
    private String sourceUrl;       // 阅读原文链接

    /** 标签 / 话题 (公众号文章里很少, 一般是 #tag# 或 关键词) */
    private List<String> tags;

    public String getMsgBiz() { return msgBiz; }
    public void setMsgBiz(String msgBiz) { this.msgBiz = msgBiz; }

    public String getMsgMid() { return msgMid; }
    public void setMsgMid(String msgMid) { this.msgMid = msgMid; }

    public String getMsgIdx() { return msgIdx; }
    public void setMsgIdx(String msgIdx) { this.msgIdx = msgIdx; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDigest() { return digest; }
    public void setDigest(String digest) { this.digest = digest; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getContentHtml() { return contentHtml; }
    public void setContentHtml(String contentHtml) { this.contentHtml = contentHtml; }

    public String getPublishTime() { return publishTime; }
    public void setPublishTime(String publishTime) { this.publishTime = publishTime; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getAccountAvatar() { return accountAvatar; }
    public void setAccountAvatar(String accountAvatar) { this.accountAvatar = accountAvatar; }

    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }

    public List<String> getImageUrls() { return imageUrls; }
    public void setImageUrls(List<String> imageUrls) { this.imageUrls = imageUrls; }

    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    /** 把 WechatArticle 转成 RewriteRequest 能用的 originalContent. */
    public String toRewritableText() {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) sb.append(title).append("\n\n");
        if (content != null && !content.isBlank()) sb.append(content);
        return sb.toString();
    }
}
