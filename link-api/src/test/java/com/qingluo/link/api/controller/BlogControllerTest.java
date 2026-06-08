package com.qingluo.link.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.cache.UserCacheService;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
    "tolink.oss.service-type=local",
    "tolink.oss.file-root-path=${java.io.tmpdir}/tolink-blog-test-oss",
    "tolink.oss.public-base-url=/api/v1/oss-files/public"
})
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class BlogControllerTest {

    private static final Long ADMIN_ID = 99101L;
    private static final Long USER_ID = 99102L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private UserCacheService userCacheService;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM blog_asset");
        jdbcTemplate.update("DELETE FROM blog_post");
        jdbcTemplate.update("DELETE FROM sys_user");

        insertUser(ADMIN_ID, "blogadmin", "ADMIN");
        insertUser(USER_ID, "bloguser", "USER");
        given(userCacheService.getOrLoad(anyLong(), any())).willAnswer(invocation -> {
            Supplier<UserProfileDTO> supplier = invocation.getArgument(1);
            return supplier.get();
        });

        StpUtil.login(ADMIN_ID);
        adminToken = StpUtil.getTokenValue();
        StpUtil.login(USER_ID);
        userToken = StpUtil.getTokenValue();
    }

    @Test
    void Should_CreateUploadPublishAndReadPublicBlog_When_AdminOperates() throws Exception {
        Long postId = createPost("minio-storage-guide");

        MockMultipartFile markdown = new MockMultipartFile(
            "file", "post.md", "text/markdown", "# 标题\n正文".getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/admin/blog/posts/{postId}/content", postId)
                .file(markdown)
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.contentObjectKey").value(org.hamcrest.Matchers.matchesPattern(
                "blog/" + postId + "/content/[a-f0-9]{32}\\.md")))
            .andExpect(jsonPath("$.data.contentMarkdown").value("# 标题\n正文"))
            .andReturn();

        String objectKey = objectMapper.readTree(uploadResult.getResponse().getContentAsString())
            .get("data").get("contentObjectKey").asText();
        assertThat(objectKey).doesNotEndWith("/current.md");

        mockMvc.perform(post("/api/v1/admin/blog/posts/{postId}/publish", postId)
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.publishedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/blog/posts")
                .param("page", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.items[0].slug").value("minio-storage-guide"))
            .andExpect(jsonPath("$.data.items[0].contentMarkdown").doesNotExist());

        mockMvc.perform(get("/api/v1/blog/posts/{slug}", "minio-storage-guide"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.slug").value("minio-storage-guide"))
            .andExpect(jsonPath("$.data.contentMarkdown").value("# 标题\n正文"));
    }

    @Test
    void Should_Return403AndNoSideEffect_When_NormalUserCreatesPost() throws Exception {
        mockMvc.perform(post("/api/v1/admin/blog/posts")
                .header("satoken", userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"普通用户文章","slug":"normal-user-post","summary":"x"}
                    """))
            .andExpect(status().isForbidden());

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM blog_post", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void Should_RejectNonMarkdownContent_When_UploadingContent() throws Exception {
        Long postId = createPost("markdown-only");
        MockMultipartFile file = new MockMultipartFile(
            "file", "post.txt", "text/plain", "# 标题".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/admin/blog/posts/{postId}/content", postId)
                .file(file)
                .header("satoken", adminToken))
            .andExpect(status().isBadRequest());

        String key = jdbcTemplate.queryForObject(
            "SELECT content_object_key FROM blog_post WHERE id = ?", String.class, postId);
        assertThat(key).isNull();
    }

    @Test
    void Should_UploadAndSoftDeleteCoverAsset_When_AdminOperates() throws Exception {
        Long postId = createPost("cover-image");
        MockMultipartFile image = new MockMultipartFile(
            "file", "cover.webp", "image/webp", "image-content".getBytes(StandardCharsets.UTF_8));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/admin/blog/posts/{postId}/assets", postId)
                .file(image)
                .param("assetType", "COVER")
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.assetType").value("COVER"))
            .andExpect(jsonPath("$.data.objectKey").value(org.hamcrest.Matchers.matchesPattern(
                "blog/" + postId + "/cover/[a-f0-9]{32}\\.webp")))
            .andExpect(jsonPath("$.data.publicUrl").isNotEmpty())
            .andReturn();

        JsonNode data = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data");
        Long assetId = data.get("id").asLong();
        Long coverAssetId = jdbcTemplate.queryForObject(
            "SELECT cover_asset_id FROM blog_post WHERE id = ?", Long.class, postId);
        assertThat(coverAssetId).isEqualTo(assetId);

        mockMvc.perform(delete("/api/v1/admin/blog/posts/{postId}/assets/{assetId}", postId, assetId)
                .header("satoken", adminToken))
            .andExpect(status().isOk());

        Boolean deleted = jdbcTemplate.queryForObject(
            "SELECT is_deleted FROM blog_asset WHERE id = ?", Boolean.class, assetId);
        assertThat(deleted).isTrue();
        Long clearedCover = jdbcTemplate.queryForObject(
            "SELECT cover_asset_id FROM blog_post WHERE id = ?", Long.class, postId);
        assertThat(clearedCover).isNull();
    }

    @Test
    void Should_Return404_When_ContentDownloadRouteIsRequested() throws Exception {
        Long postId = createPost("no-download");

        mockMvc.perform(get("/api/v1/admin/blog/posts/{postId}/content/download", postId)
                .header("satoken", adminToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void Should_RejectDuplicateSlugAndAllowReuseAfterSoftDelete() throws Exception {
        mockMvc.perform(post("/api/v1/admin/blog/posts")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"非法slug","slug":"Upper-Case","summary":"x"}
                    """))
            .andExpect(status().isBadRequest());

        Long firstId = createPost("release-note");
        mockMvc.perform(post("/api/v1/admin/blog/posts")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"重复slug","slug":"release-note","summary":"x"}
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/v1/admin/blog/posts/{postId}", firstId)
                .header("satoken", adminToken))
            .andExpect(status().isOk());
        Long secondId = createPost("release-note");

        Boolean firstDeleted = jdbcTemplate.queryForObject(
            "SELECT is_deleted FROM blog_post WHERE id = ?", Boolean.class, firstId);
        Long deletedSeq = jdbcTemplate.queryForObject(
            "SELECT deleted_seq FROM blog_post WHERE id = ?", Long.class, firstId);
        assertThat(firstDeleted).isTrue();
        assertThat(deletedSeq).isEqualTo(firstId);
        assertThat(secondId).isNotEqualTo(firstId);
    }

    @Test
    void Should_RejectPublishWithoutReadableContentAndKeepFirstPublishedAt() throws Exception {
        Long postId = createPost("publish-state");

        mockMvc.perform(post("/api/v1/admin/blog/posts/{postId}/publish", postId)
                .header("satoken", adminToken))
            .andExpect(status().isBadRequest());

        jdbcTemplate.update(
            "UPDATE blog_post SET content_object_key = ? WHERE id = ?",
            "blog/" + postId + "/content/missing.md",
            postId);
        mockMvc.perform(post("/api/v1/admin/blog/posts/{postId}/publish", postId)
                .header("satoken", adminToken))
            .andExpect(status().isBadRequest());

        uploadMarkdown(postId, "# 发布\n正文");
        mockMvc.perform(post("/api/v1/admin/blog/posts/{postId}/publish", postId)
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
        LocalDateTime firstPublishedAt = publishedAt(postId);

        mockMvc.perform(post("/api/v1/admin/blog/posts/{postId}/unpublish", postId)
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
        mockMvc.perform(get("/api/v1/blog/posts/{slug}", "publish-state"))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/admin/blog/posts/{postId}/publish", postId)
                .header("satoken", adminToken))
            .andExpect(status().isOk());
        assertThat(publishedAt(postId)).isEqualTo(firstPublishedAt);
    }

    @Test
    void Should_Return500_When_PublicDetailCannotReadMarkdownObject() throws Exception {
        Long postId = createPost("broken-content");
        jdbcTemplate.update(
            "UPDATE blog_post SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP, content_object_key = ? WHERE id = ?",
            "blog/" + postId + "/content/broken.md",
            postId);

        mockMvc.perform(get("/api/v1/blog/posts/{slug}", "broken-content"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void Should_FilterAssetListAndRejectUnsupportedAssetUploads() throws Exception {
        Long postId = createPost("asset-list");
        Long otherPostId = createPost("asset-other");
        Long contentAssetId = uploadAsset(postId, "CONTENT_IMAGE", "diagram.png", "image/png");
        Long coverAssetId = uploadAsset(postId, "COVER", "cover.webp", "image/webp");
        uploadAsset(otherPostId, "CONTENT_IMAGE", "other.png", "image/png");

        mockMvc.perform(delete("/api/v1/admin/blog/posts/{postId}/assets/{assetId}", postId, coverAssetId)
                .header("satoken", adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/blog/posts/{postId}/assets", postId)
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].id").value(contentAssetId));

        MockMultipartFile svg = new MockMultipartFile("file", "vector.svg", "image/svg+xml", "<svg/>".getBytes());
        mockMvc.perform(multipart("/api/v1/admin/blog/posts/{postId}/assets", postId)
                .file(svg)
                .param("assetType", "CONTENT_IMAGE")
                .header("satoken", adminToken))
            .andExpect(status().isBadRequest());
        MockMultipartFile image = new MockMultipartFile("file", "missing.png", "image/png", "image".getBytes());
        mockMvc.perform(multipart("/api/v1/admin/blog/posts/{postId}/assets", 999999L)
                .file(image)
                .param("assetType", "CONTENT_IMAGE")
                .header("satoken", adminToken))
            .andExpect(status().isNotFound());
    }

    private Long createPost(String slug) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/blog/posts")
                .header("satoken", adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"MinIO 存储说明","slug":"%s","summary":"对象存储说明"}
                    """.formatted(slug)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DRAFT"))
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private void uploadMarkdown(Long postId, String content) throws Exception {
        MockMultipartFile markdown = new MockMultipartFile(
            "file", "post.md", "text/markdown", content.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/admin/blog/posts/{postId}/content", postId)
                .file(markdown)
                .header("satoken", adminToken))
            .andExpect(status().isOk());
    }

    private Long uploadAsset(Long postId, String assetType, String filename, String contentType) throws Exception {
        MockMultipartFile image = new MockMultipartFile(
            "file", filename, contentType, "image-content".getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/v1/admin/blog/posts/{postId}/assets", postId)
                .file(image)
                .param("assetType", assetType)
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    private LocalDateTime publishedAt(Long postId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
            "SELECT published_at FROM blog_post WHERE id = ?", Timestamp.class, postId);
        return timestamp.toLocalDateTime();
    }

    private void insertUser(Long id, String username, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setNickname(username);
        user.setEmail(username + "@test.com");
        user.setRole(role);
        user.setStatus(1);
        sysUserMapper.insert(user);
    }
}
