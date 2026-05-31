package com.qingluo.link.service.impl.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qingluo.link.mapper.DocumentParsedLogMapper;
import com.qingluo.link.model.dto.entity.DocumentParsedLog;
import com.qingluo.link.service.DocumentParseRetryChainService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 沿 retry_of_task_id 向 origin 回溯重试链。深度上限 + 访问集防环 + 链断安全终止。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentParseRetryChainServiceImpl implements DocumentParseRetryChainService {

    private final DocumentParsedLogMapper documentParsedLogMapper;

    /** 回溯深度上限，防御异常长链 / 脏数据，避免无界查询。 */
    @Value("${tolink.parse.retry-chain.max-depth:32}")
    private int maxDepth;

    @Override
    public List<String> traceChain(String taskId) {
        List<String> chain = new ArrayList<>();
        if (!StringUtils.hasText(taskId)) {
            return chain;
        }
        Set<String> visited = new HashSet<>();
        String current = taskId;
        while (StringUtils.hasText(current)) {
            // 防环：已访问过的 task_id 再次出现即终止，不进入死循环。
            if (!visited.add(current)) {
                log.warn("Retry chain cycle detected at taskId={}, stop tracing", current);
                break;
            }
            // 深度上限：已收集 maxDepth 个节点即截断。
            if (chain.size() >= maxDepth) {
                log.warn("Retry chain exceeds max depth {}, truncate at taskId={}", maxDepth, current);
                break;
            }
            DocumentParsedLog logRecord = documentParsedLogMapper.selectOne(
                new LambdaQueryWrapper<DocumentParsedLog>()
                    .eq(DocumentParsedLog::getTaskId, current).last("LIMIT 1"));
            // 链断：指向的上一轮日志不存在 → 不纳入该缺失节点，安全终止。
            if (logRecord == null) {
                break;
            }
            chain.add(current);
            current = logRecord.getRetryOfTaskId();
        }
        return chain;
    }
}
