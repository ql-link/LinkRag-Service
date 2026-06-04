package com.qingluo.link.service.impl.llm;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * `config_schema.modelFetch` 内部结构。
 */
@Data
class ModelFetchSpec {

    private Boolean enabled;
    private String method = "GET";
    private String urlTemplate;
    private Auth auth = new Auth();
    private Map<String, String> headers = new HashMap<>();
    private Response response = new Response();

    boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    @Data
    static class Auth {
        private String type = "bearer";
        private String header = "Authorization";
        private String headerName;
        private String prefix = "Bearer ";
    }

    @Data
    static class Response {
        private String itemsPath = "data";
        private String idPath = "id";
        private String displayNamePath;
        private String ownedByPath = "owned_by";
    }
}
