package com.qingluo.link.service.mq.cdc;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Canal flatMessage 行变更事件（Kafka 原始 topic 入站）。
 *
 * <p>仅声明 CDC 桥接需要的字段：表名、操作类型、变更行集。Canal flatMessage 的 data 为多行数组、
 * 字段值统一为 String；DELETE 时 data 为被删行（before image）。其余 flatMessage 字段
 * （old / sql / mysqlType 等）桥接不读，故不声明。</p>
 */
@Data
public class CanalChangeEvent {

    private String database;

    private String table;

    /** 操作类型：INSERT / UPDATE / DELETE。 */
    private String type;

    /** 是否 DDL 变更；DDL 不涉及行数据，桥接直接忽略。 */
    @JSONField(name = "isDdl")
    private boolean ddl;

    /** binlog 事件时间（毫秒），用作补偿消息 occurred_at。 */
    private Long es;

    /** 变更行集：每行是 列名→值（值为 String）。 */
    private List<Map<String, String>> data;
}
