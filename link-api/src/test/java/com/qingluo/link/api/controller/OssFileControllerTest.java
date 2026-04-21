package com.qingluo.link.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "tolink.oss.service-type=local",
    "tolink.oss.file-root-path=/tmp/tolink-oss-api-test",
    "tolink.oss.public-base-url=/api/v1/oss-files/public"
})
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class OssFileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void Should_Upload_Public_File_Through_Api_Controller_When_BizType_Is_Avatar() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, "png-content".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/oss-files/{bizType}", "avatar").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.startsWith("/api/v1/oss-files/public/avatar/")));
    }

    @Test
    void Should_Preview_Uploaded_Public_File_Through_Api_Controller() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "avatar.png", MediaType.IMAGE_PNG_VALUE, "preview-content".getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/oss-files/{bizType}", "avatar").file(file))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode response = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        String previewUrl = response.get("data").asText();
        assertThat(previewUrl).startsWith("/api/v1/oss-files/public/avatar/");

        mockMvc.perform(get(previewUrl))
            .andExpect(status().isOk())
            .andExpect(content().string("preview-content"));
    }
}
