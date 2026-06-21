package com.qingluo.link.service.impl.llm;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.qingluo.link.core.util.ApiKeyEncryptService;
import com.qingluo.link.model.dto.entity.UserLLMConfig;
import com.qingluo.link.service.config.ConversationTitleProperties;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OpenAI-compatible chat completions 标题生成器。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiCompatibleChatTitleGenerator implements ChatTitleGenerator {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String OPENAI_PROTOCOL = "openai";

    private final ApiKeyEncryptService apiKeyEncryptService;
    private final ConversationTitleProperties properties;

    @Override
    public String generate(UserLLMConfig config, String query, String answer) {
        if (config == null || !OPENAI_PROTOCOL.equals(config.getProtocol())) {
            return null;
        }
        if (!StringUtils.hasText(config.getApiBaseUrl()) || !StringUtils.hasText(config.getApiKey())
            || !StringUtils.hasText(config.getModelName()) || !StringUtils.hasText(query)) {
            return null;
        }

        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .writeTimeout(properties.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();

        JSONObject body = new JSONObject();
        body.put("model", config.getModelName());
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", properties.getMaxTokens());

        JSONArray messages = new JSONArray();
        messages.add(message("system", "你是对话标题生成器。根据用户首问和助手回答，生成一个具体、自然的中文短标题，不超过"
            + properties.getMaxLength() + "个字符。优先概括回答中的核心主题、对象或结论，不要机械复述用户问题。"
            + "只输出标题，不要引号、编号、解释或标点装饰。"));
        messages.add(message("user", buildUserPrompt(query, answer)));
        body.put("messages", messages);

        Request request = new Request.Builder()
            .url(chatCompletionsUrl(config.getApiBaseUrl()))
            .header("Authorization", "Bearer " + apiKeyEncryptService.decrypt(config.getApiKey()))
            .header("Content-Type", "application/json")
            .post(RequestBody.create(body.toJSONString(), JSON))
            .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Generate conversation title failed, httpStatus={}, configId={}",
                    response.code(), config.getId());
                return null;
            }
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                return null;
            }
            return extractTitle(responseBody.string());
        } catch (IOException | RuntimeException e) {
            log.warn("Generate conversation title request failed, configId={}", config.getId(), e);
            return null;
        }
    }

    private JSONObject message(String role, String content) {
        JSONObject message = new JSONObject();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private String buildUserPrompt(String query, String answer) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("用户首问：\n").append(query.trim());
        String answerPreview = limitAnswer(answer);
        if (StringUtils.hasText(answerPreview)) {
            prompt.append("\n\n助手回答摘要材料：\n").append(answerPreview);
        }
        prompt.append("\n\n请基于以上内容生成对话标题。");
        return prompt.toString();
    }

    private String limitAnswer(String answer) {
        if (!StringUtils.hasText(answer)) {
            return null;
        }
        String normalized = answer.trim().replaceAll("\\s+", " ");
        int maxChars = Math.max(0, properties.getMaxAnswerChars());
        if (maxChars == 0 || normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars);
    }

    private String chatCompletionsUrl(String apiBaseUrl) {
        String base = apiBaseUrl.trim();
        if (base.endsWith("/chat/completions")) {
            return base;
        }
        if (base.endsWith("/")) {
            return base + "chat/completions";
        }
        return base + "/chat/completions";
    }

    private String extractTitle(String response) {
        JSONObject json = JSONObject.parseObject(response);
        JSONArray choices = json.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        JSONObject choice = choices.getJSONObject(0);
        JSONObject message = choice.getJSONObject("message");
        return message == null ? null : message.getString("content");
    }
}
