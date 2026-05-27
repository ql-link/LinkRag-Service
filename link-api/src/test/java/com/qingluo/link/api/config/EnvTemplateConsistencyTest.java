package com.qingluo.link.api.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 环境变量模板一致性测试。
 * 验证 .env.example 包含 application-dev.yml 中引用的所有环境变量。
 *
 * Validates: Requirements 5.1, 5.6
 */
class EnvTemplateConsistencyTest {

    /**
     * 用于提取 YAML 文件中 ${ENV_VAR} 和 ${ENV_VAR:default} 形式引用的环境变量名。
     * 仅匹配全大写+下划线的变量名（排除 Spring 内置变量如 user.home、java.io.tmpdir）。
     */
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$\\{([A-Z_]+)(?::[^}]*)?\\}");

    @Test
    void envExampleContainsAllReferencedVariables() throws IOException {
        Set<String> yamlVars = extractEnvVarsFromDevYaml();
        String envExampleContent = readEnvExample();

        assertThat(yamlVars).as("应从 application-dev.yml 中提取到环境变量引用").isNotEmpty();

        for (String var : yamlVars) {
            assertThat(envExampleContent)
                    .as("Missing env var in .env.example: " + var)
                    .contains(var);
        }
    }

    /**
     * 从 application-dev.yml 中提取所有通过 ${ENV_VAR} 或 ${ENV_VAR:default} 引用的环境变量名。
     */
    private Set<String> extractEnvVarsFromDevYaml() throws IOException {
        Path yamlPath = Paths.get("src/main/resources/application-dev.yml");
        String content = Files.readString(yamlPath, StandardCharsets.UTF_8);

        Matcher matcher = ENV_VAR_PATTERN.matcher(content);
        Set<String> vars = new HashSet<>();
        while (matcher.find()) {
            vars.add(matcher.group(1));
        }
        return vars;
    }

    /**
     * 读取项目根目录的 .env.example 文件。
     * Maven 测试执行时 user.dir 指向模块目录（link-api），需向上导航到项目根目录。
     */
    private String readEnvExample() throws IOException {
        // Maven 测试执行时 working directory 为模块目录 (link-api)
        Path moduleDir = Paths.get(System.getProperty("user.dir"));
        Path projectRoot = moduleDir.getParent();
        Path envExamplePath = projectRoot.resolve(".env.example");

        assertThat(envExamplePath.toFile())
                .as(".env.example 文件应存在于项目根目录: " + envExamplePath)
                .exists();

        return Files.readString(envExamplePath, StandardCharsets.UTF_8);
    }
}
