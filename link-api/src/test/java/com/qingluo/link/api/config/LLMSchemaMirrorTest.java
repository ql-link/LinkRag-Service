package com.qingluo.link.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Python Alembic 是生产权威；本测试保证两份 Java 初始化脚本是同一逻辑 schema 的镜像。
 * MySQL 的 UNSIGNED/AUTO_INCREMENT/JSON 与 H2 的 IDENTITY/VARCHAR(JSON 文本)仅做方言归一化。
 */
class LLMSchemaMirrorTest {

    private static final List<String> TABLES = List.of(
        "llm_model_config", "llm_capability_default", "dataset_parse_config");
    private static final Set<String> JSON_TEXT_COLUMNS = Set.of(
        "chunking_config", "enhancement_config", "pdf_config", "recall_config");
    private static final Pattern COLUMN_LINE = Pattern.compile("^([a-z][a-z0-9_]*)\\s+(.+?)(?:,)?$");
    private static final Pattern MYSQL_INDEX = Pattern.compile(
        "^(UNIQUE\\s+KEY|INDEX|KEY)\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern H2_INDEX = Pattern.compile(
        "CREATE\\s+(UNIQUE\\s+)?INDEX\\s+IF\\s+NOT\\s+EXISTS\\s+([a-zA-Z0-9_]+)"
            + "\\s+ON\\s+([a-zA-Z0-9_]+)\\s*\\(([^)]*)\\)",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern VARCHAR = Pattern.compile("^VARCHAR(?:\\((\\d+)\\))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFAULT = Pattern.compile(
        "\\bDEFAULT\\s+('(?:[^']*)'|[A-Z_]+|[0-9]+)", Pattern.CASE_INSENSITIVE);

    @Test
    void mysqlAndH2MirrorsHaveEqualLogicalLlmAndDatasetSchema() throws IOException {
        String mysql = Files.readString(existing(
            Path.of("../scripts/db/init.sql"), Path.of("scripts/db/init.sql")));
        String h2 = Files.readString(existing(
            Path.of("src/main/resources/schema.sql"),
            Path.of("link-api/src/main/resources/schema.sql")));

        for (String table : TABLES) {
            assertThat(tableFacts(mysql, table, false))
                .as("logical schema for %s", table)
                .isEqualTo(tableFacts(h2, table, true));
        }
        assertThat(mysql).doesNotContain(
            "CREATE TABLE IF NOT EXISTS llm_user_config",
            "CREATE TABLE IF NOT EXISTS llm_system_preset",
            "embedding_config_source");
        assertThat(h2).doesNotContain(
            "CREATE TABLE IF NOT EXISTS llm_user_config",
            "CREATE TABLE IF NOT EXISTS llm_system_preset",
            "embedding_config_source");
    }

    @Test
    void mirrorContainsAllFiveDatasetBindingsAndRequiredReverseIndexes() throws IOException {
        String mysql = Files.readString(existing(
            Path.of("../scripts/db/init.sql"), Path.of("scripts/db/init.sql")));
        TableFacts facts = tableFacts(mysql, "dataset_parse_config", false);

        assertThat(facts.columns().keySet()).contains(
            "dense_embedding_config_id",
            "sparse_embedding_config_id",
            "enhancement_chat_config_id",
            "enhancement_vision_config_id",
            "rerank_config_id");
        assertThat(facts.indexes()).containsEntry(
            "idx_dataset_parse_dense_config", new IndexFacts(false, List.of("dense_embedding_config_id")))
            .containsEntry(
                "idx_dataset_parse_sparse_config", new IndexFacts(false, List.of("sparse_embedding_config_id")))
            .containsEntry(
                "idx_dataset_parse_enhancement_chat_config",
                new IndexFacts(false, List.of("enhancement_chat_config_id")))
            .containsEntry(
                "idx_dataset_parse_enhancement_vision_config",
                new IndexFacts(false, List.of("enhancement_vision_config_id")))
            .containsEntry(
                "idx_dataset_parse_rerank_config", new IndexFacts(false, List.of("rerank_config_id")));
    }

    private TableFacts tableFacts(String ddl, String table, boolean h2) {
        List<String> lines = tableLines(ddl, table);
        Map<String, ColumnFacts> columns = new LinkedHashMap<>();
        Map<String, IndexFacts> indexes = new LinkedHashMap<>();
        for (String rawLine : lines) {
            String line = rawLine.strip();
            Matcher index = MYSQL_INDEX.matcher(line);
            if (index.find()) {
                indexes.put(index.group(2), new IndexFacts(
                    index.group(1).toUpperCase(Locale.ROOT).startsWith("UNIQUE"),
                    columnNames(index.group(3))));
                continue;
            }
            Matcher column = COLUMN_LINE.matcher(line);
            if (!column.matches()) {
                continue;
            }
            String name = column.group(1);
            String definition = column.group(2);
            columns.put(name, new ColumnFacts(
                logicalType(name, definition),
                !definition.toUpperCase(Locale.ROOT).contains("NOT NULL")
                    && !definition.toUpperCase(Locale.ROOT).contains("PRIMARY KEY"),
                normalizedDefault(definition),
                definition.toUpperCase(Locale.ROOT).contains("PRIMARY KEY"),
                definition.toUpperCase(Locale.ROOT).contains("AUTO_INCREMENT")
                    || definition.toUpperCase(Locale.ROOT).contains("IDENTITY"),
                definition.toUpperCase(Locale.ROOT).contains("ON UPDATE CURRENT_TIMESTAMP")));
        }
        if (h2) {
            Matcher index = H2_INDEX.matcher(ddl);
            while (index.find()) {
                if (table.equalsIgnoreCase(index.group(3))) {
                    indexes.put(index.group(2), new IndexFacts(
                        index.group(1) != null, columnNames(index.group(4))));
                }
            }
        }
        return new TableFacts(columns, indexes);
    }

    private List<String> tableLines(String ddl, String table) {
        String marker = "CREATE TABLE IF NOT EXISTS " + table + " (";
        int start = ddl.indexOf(marker);
        assertThat(start).as("table %s exists", table).isGreaterThanOrEqualTo(0);
        String tail = ddl.substring(start + marker.length());
        int end = -1;
        int offset = 0;
        for (String line : tail.split("\\R", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith(") ENGINE=") || trimmed.equals(");")) {
                end = offset;
                break;
            }
            offset += line.length() + 1;
        }
        assertThat(end).as("table %s closing delimiter", table).isGreaterThanOrEqualTo(0);
        return tail.substring(0, end).lines().toList();
    }

    private String logicalType(String name, String definition) {
        String upper = definition.toUpperCase(Locale.ROOT);
        if (JSON_TEXT_COLUMNS.contains(name)) {
            return "JSON_TEXT";
        }
        if (upper.startsWith("BIGINT")) {
            return "BIGINT";
        }
        if (upper.startsWith("BOOLEAN")) {
            return "BOOLEAN";
        }
        if (upper.startsWith("DATETIME")) {
            return "DATETIME";
        }
        Matcher varchar = VARCHAR.matcher(upper);
        if (varchar.find()) {
            return varchar.group(1) == null ? "VARCHAR" : "VARCHAR(" + varchar.group(1) + ")";
        }
        if (upper.startsWith("JSON")) {
            return "JSON";
        }
        throw new AssertionError("Unsupported schema type for " + name + ": " + definition);
    }

    private String normalizedDefault(String definition) {
        if (definition.toUpperCase(Locale.ROOT).contains("GENERATED BY DEFAULT AS IDENTITY")) {
            return null;
        }
        Matcher matcher = DEFAULT.matcher(definition);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(1).replace("'", "").toLowerCase(Locale.ROOT);
        return "null".equals(value) ? null : value;
    }

    private List<String> columnNames(String value) {
        return Pattern.compile(",").splitAsStream(value)
            .map(column -> column.replace("`", "").strip())
            .toList();
    }

    private Path existing(Path moduleRelative, Path repositoryRelative) {
        return Files.exists(moduleRelative) ? moduleRelative : repositoryRelative;
    }

    private record ColumnFacts(String type, boolean nullable, String defaultValue,
                               boolean primaryKey, boolean generated, boolean updateTimestamp) {
    }

    private record IndexFacts(boolean unique, List<String> columns) {
    }

    private record TableFacts(Map<String, ColumnFacts> columns,
                              Map<String, IndexFacts> indexes) {
    }
}
