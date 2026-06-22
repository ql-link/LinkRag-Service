package com.qingluo.link.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 对话标题生成配置。
 */
@ConfigurationProperties(prefix = "tolink.chat.title-generation")
public class ConversationTitleProperties {

    /** 是否启用模型标题生成；关闭时只使用首问截断标题。 */
    private boolean enabled = true;
    /** 生成标题最大长度，超过会清洗截断。 */
    private int maxLength = 30;
    /** HTTP 调用超时时间。 */
    private int timeoutMs = 3000;
    /** 模型最大输出 token。 */
    private int maxTokens = 32;
    /** 采样温度，标题生成保持低随机性。 */
    private double temperature = 0.2;
    /** 生成标题时最多携带的首轮回答字符数。 */
    private int maxAnswerChars = 800;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxAnswerChars() {
        return maxAnswerChars;
    }

    public void setMaxAnswerChars(int maxAnswerChars) {
        this.maxAnswerChars = maxAnswerChars;
    }
}
