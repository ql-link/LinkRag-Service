package com.qingluo.link.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.qingluo.link.api.TestSecurityConfig;
import com.qingluo.link.mapper.SysUserMapper;
import com.qingluo.link.model.dto.entity.SysUser;
import com.qingluo.link.model.dto.response.LogEntryDTO;
import com.qingluo.link.model.dto.response.LogLabelsDTO;
import com.qingluo.link.model.dto.response.PageResult;
import com.qingluo.link.model.dto.response.UserProfileDTO;
import com.qingluo.link.service.AdminLogQueryService;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)
class AdminLogControllerTest {

    private static final Long ADMIN_ID = 99221L;
    private static final Long USER_ID = 99222L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private AdminLogQueryService adminLogQueryService;

    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        Mockito.reset(adminLogQueryService);
        sysUserMapper.deleteById(ADMIN_ID);
        sysUserMapper.deleteById(USER_ID);
        insertUser(ADMIN_ID, "logadmin", "ADMIN");
        insertUser(USER_ID, "loguser", "USER");
        StpUtil.login(ADMIN_ID);
        adminToken = StpUtil.getTokenValue();
        StpUtil.login(USER_ID);
        userToken = StpUtil.getTokenValue();
    }

    @Test
    void Should_ReturnLogs_When_AdminQueriesLogs() throws Exception {
        LogEntryDTO entry = new LogEntryDTO();
        entry.setTime("2026-07-03T10:00:00Z");
        entry.setLevel("ERROR");
        entry.setService("tolink-service");
        entry.setTraceId("trace-1");
        entry.setLoggerName("com.qingluo.Test");
        entry.setMessage("boom");
        given(adminLogQueryService.queryLogs(
            "tolink-service",
            "ERROR",
            "trace-1",
            "boom",
            "2026-07-03T10:00:00Z",
            "2026-07-03T11:00:00Z",
            1,
            50))
            .willReturn(new PageResult<>(List.of(entry), 1, 1, 50));

        mockMvc.perform(get("/api/v1/admin/logs")
                .header("satoken", adminToken)
                .param("service", "tolink-service")
                .param("level", "ERROR")
                .param("trace_id", "trace-1")
                .param("keyword", "boom")
                .param("start_time", "2026-07-03T10:00:00Z")
                .param("end_time", "2026-07-03T11:00:00Z")
                .param("page", "1")
                .param("page_size", "50"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.items[0].trace_id").value("trace-1"))
            .andExpect(jsonPath("$.data.items[0].logger_name").value("com.qingluo.Test"))
            .andExpect(jsonPath("$.data.pageSize").value(50));
    }

    @Test
    void Should_Return403_When_NormalUserQueriesLogs() throws Exception {
        mockMvc.perform(get("/api/v1/admin/logs")
                .header("satoken", userToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void Should_ReturnLabels_When_AdminQueriesLabels() throws Exception {
        given(adminLogQueryService.listLabels()).willReturn(new LogLabelsDTO(
            List.of("tolink-service", "tolink-rag"),
            List.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "ACCESS", "AUDIT")));

        mockMvc.perform(get("/api/v1/admin/logs/labels")
                .header("satoken", adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.services[0]").value("tolink-service"))
            .andExpect(jsonPath("$.data.services[1]").value("tolink-rag"))
            .andExpect(jsonPath("$.data.levels[6]").value("ACCESS"));
    }

    private void insertUser(Long id, String username, String role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setNickname(username);
        user.setEmail(username + "@test.com");
        user.setRole(role);
        user.setStatus(1);
        sysUserMapper.insert(user);
    }
}
