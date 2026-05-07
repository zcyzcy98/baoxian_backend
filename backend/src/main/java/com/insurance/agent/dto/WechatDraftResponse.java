package com.insurance.agent.dto;

public class WechatDraftResponse {
    private boolean published;
    private String markdownPath;
    private String commandPreview;
    private String mediaId;
    private String stdout;
    private String stderr;
    private String message;

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public String getMarkdownPath() { return markdownPath; }
    public void setMarkdownPath(String markdownPath) { this.markdownPath = markdownPath; }

    public String getCommandPreview() { return commandPreview; }
    public void setCommandPreview(String commandPreview) { this.commandPreview = commandPreview; }

    public String getMediaId() { return mediaId; }
    public void setMediaId(String mediaId) { this.mediaId = mediaId; }

    public String getStdout() { return stdout; }
    public void setStdout(String stdout) { this.stdout = stdout; }

    public String getStderr() { return stderr; }
    public void setStderr(String stderr) { this.stderr = stderr; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
