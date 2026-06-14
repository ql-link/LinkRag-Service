#!/usr/bin/env python3
"""
从 RAGFlow 导出的 LLM 配置生成 toLink-Service 的数据库种子 SQL。

数据来源（双源合并）：
  models/*.json     → 厂商 URL、精确模型能力数组
  llm_factories.json → 完整模型列表（对 models/*.json 做补充）

用法：
  python3 scripts/import_ragflow_configs.py [ragflow-configs-dir]

  ragflow-configs-dir 默认为 /Users/fang/Downloads/ragflow-llm-configs

输出：
  scripts/db/seed_llm_providers.sql

能力映射（RAGFlow model_types → toLink capability）：
  chat / vision / image2text → CHAT / VISION
  embedding → EMBEDDING
  rerank    → RERANK
  ocr       → OCR
  asr / tts / doc_parse / speech2text → 跳过

跳过以下厂商（本地推理框架 / 无公网 API / 专属工具）：
  ollama, vllm, localai, lmstudio, xinference, modelscope, gpustack, builtin,
  mineru, mineru_local, paddleocr, paddleocr_local, fishaudio
"""

import json
import sys
from pathlib import Path

# ─── 路径配置 ────────────────────────────────────────────────────────────────

RAGFLOW_DIR = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("/Users/fang/Downloads/ragflow-llm-configs")
MODELS_DIR = RAGFLOW_DIR / "models"
FACTORIES_FILE = RAGFLOW_DIR / "llm_factories.json"
OUTPUT_SQL = Path(__file__).parent / "db" / "seed_llm_providers.sql"

# ─── 映射规则 ────────────────────────────────────────────────────────────────

# RAGFlow model_type / model_types 元素 → toLink capability（None = 跳过）
CAPABILITY_MAP: dict[str, str | None] = {
    "chat":        "CHAT",
    "vision":      "VISION",
    "image2text":  "VISION",   # llm_factories.json 里的旧字段
    "embedding":   "EMBEDDING",
    "rerank":      "RERANK",
    "ocr":         "OCR",
    "asr":         "ASR",
    "speech2text": "ASR",      # llm_factories.json 里的旧字段
    # 以下跳过
    "tts":         None,       # 纯输出，RAG 场景不需要
    "doc_parse":   None,
}

# 文件名 stem → toLink provider_type 覆盖（未出现的用 stem 本身）
STEM_TO_PROVIDER_TYPE: dict[str, str] = {
    "anthropic": "claude",       # toLink 历史上叫 claude
    "zhipu-ai":  "glm",          # 智谱
    "aliyun":    "aliyun",
    "google":    "gemini",       # Google Gemini
    "302ai":     "302ai",
    "azure-openai": "azure-openai",
}

# models/*.json 厂商 name → llm_factories.json 厂商 name（用于补充模型列表）
# 仅在两边命名不一致时才需要列出
MODELS_TO_FACTORIES_NAME: dict[str, str] = {
    "Aliyun": "Tongyi-Qianwen",
    "Google": "Gemini",
}

# 跳过以下 stem（本地部署框架 / 专属非 LLM 工具）
SKIP_STEMS: set[str] = {
    "ollama", "vllm", "localai", "lmstudio", "xinference",
    "modelscope", "gpustack", "builtin",
    "mineru", "mineru_local", "paddleocr", "paddleocr_local",
    "fishaudio",
}

# ─── 协议 + base_url 种子规则 ─────────────────────────────────────────────────
# 权威来源：.specs/llm-model-capability-protocol/provider_url_seed.md
#
# 存储约定：api_base_url 存「协议基地址」，adapter 按 protocol + capability 拼后缀路径。

# 14 家重点厂商（is_active=TRUE），其余厂商整体 is_active=FALSE。
ACTIVE_PROVIDER_TYPES: set[str] = {
    "openai", "claude", "gemini", "xai", "aliyun", "deepseek", "glm",
    "moonshot", "volcengine", "baidu", "hunyuan", "baichuan", "xunfei", "jina",
}

# 厂商层默认协议（provider_url_seed.md §1）。未列出的默认 openai。
PROVIDER_DEFAULT_PROTOCOL: dict[str, str] = {
    "claude": "anthropic",
    "gemini": "google",
    "jina":   "jina",
    # 其余（含 aliyun）默认 openai
}

# 厂商层默认 api_base_url（provider_url_seed.md §1）。
# 仅 14 家重点厂商需要权威覆盖；未列出的沿用 RAGFlow 源 url.default。
PROVIDER_BASE_URL: dict[str, str] = {
    "openai":     "https://api.openai.com/v1",
    "claude":     "https://api.anthropic.com",
    "gemini":     "https://generativelanguage.googleapis.com/v1beta",
    "xai":        "https://api.x.ai/v1",
    "aliyun":     "https://dashscope.aliyuncs.com/compatible-mode/v1",
    "deepseek":   "https://api.deepseek.com/v1",
    "glm":        "https://open.bigmodel.cn/api/paas/v4",
    "moonshot":   "https://api.moonshot.cn/v1",
    "volcengine": "https://ark.cn-beijing.volces.com/api/v3",
    "baidu":      "https://qianfan.baidubce.com/v2",
    "hunyuan":    "https://api.hunyuan.cloud.tencent.com/v1",
    "baichuan":   "https://api.baichuan-ai.com/v1",
    "xunfei":     "https://spark-api-open.xf-yun.com/v1",
    "jina":       "https://api.jina.ai/v1",
}

# 模型能力层 (provider_type, capability) → (protocol, api_base_url) 特例覆盖
# （provider_url_seed.md §2）。未命中的沿用厂商默认 protocol + 厂商 base_url。
MODEL_PROTOCOL_OVERRIDE: dict[tuple[str, str], tuple[str, str]] = {
    ("aliyun", "RERANK"): ("dashscope", "https://dashscope.aliyuncs.com/api/v1"),
    ("aliyun", "ASR"):    ("dashscope", "https://dashscope.aliyuncs.com/api/v1"),
    ("jina", "RERANK"):    ("jina", "https://api.jina.ai/v1"),
    ("jina", "EMBEDDING"): ("jina", "https://api.jina.ai/v1"),
}

# 模型能力层 (provider_type, capability) → is_active=FALSE 的特例
# （provider_url_seed.md §4 已决规则①④）。
MODEL_INACTIVE: set[tuple[str, str]] = {
    ("deepseek", "EMBEDDING"),  # DeepSeek 无 embedding 端点
    ("xunfei", "EMBEDDING"),    # 讯飞星火无 OpenAI 兼容 embedding 入口
}


def provider_default_protocol(provider_type: str) -> str:
    """厂商默认协议。未配置的默认 openai。"""
    return PROVIDER_DEFAULT_PROTOCOL.get(provider_type, "openai")


def provider_base_url(provider_type: str, fallback_url: str) -> str:
    """厂商 base_url：14 家重点厂商用权威值覆盖，其余沿用 RAGFlow 源 url。"""
    return PROVIDER_BASE_URL.get(provider_type, fallback_url)


def provider_is_active(provider_type: str) -> bool:
    """厂商是否启用：仅 14 家重点厂商 TRUE。"""
    return provider_type in ACTIVE_PROVIDER_TYPES


def model_protocol_and_url(provider_type: str, capability: str, base_url: str) -> tuple[str, str]:
    """模型能力的 (protocol, api_base_url)：先查特例，否则沿用厂商默认。"""
    override = MODEL_PROTOCOL_OVERRIDE.get((provider_type, capability))
    if override is not None:
        return override
    return provider_default_protocol(provider_type), base_url


def model_is_active(provider_type: str, capability: str) -> bool:
    """模型能力是否上架：非重点厂商整体下架；个别能力行额外下架。"""
    if provider_type not in ACTIVE_PROVIDER_TYPES:
        return False
    if (provider_type, capability) in MODEL_INACTIVE:
        return False
    return True

# ─── 辅助函数 ────────────────────────────────────────────────────────────────

def escape_sql(s: str) -> str:
    return s.replace("'", "''")


def rank_to_priority(rank: int | None) -> int:
    """RAGFlow rank（最高 999）→ toLink priority（最高 100，越大越靠前）。"""
    if rank is None:
        return 50
    return max(1, min(100, rank // 10))


def model_cap_pairs_from_models_json(models_list: list[dict]) -> list[tuple[str, str]]:
    """从 models/*.json 的 models[] 提取 (model_name, capability) 对。"""
    seen: set[tuple[str, str]] = set()
    result: list[tuple[str, str]] = []
    for model in models_list:
        name = model.get("name", "").strip()
        if not name:
            continue
        for mt in model.get("model_types", []):
            cap = CAPABILITY_MAP.get(mt)
            if cap and (name, cap) not in seen:
                seen.add((name, cap))
                result.append((name, cap))
    return result


def model_cap_pairs_from_factories_llm(llm_list: list[dict]) -> list[tuple[str, str]]:
    """
    从 llm_factories.json 的 llm[] 提取 (model_name, capability) 对。
    model_type 是单字符串；IMAGE2TEXT 标签在 tags 里，需要额外处理。
    """
    seen: set[tuple[str, str]] = set()
    result: list[tuple[str, str]] = []
    for entry in llm_list:
        name = entry.get("llm_name", "").strip()
        if not name:
            continue
        model_type = entry.get("model_type", "")
        tags = entry.get("tags", "")

        caps: list[str] = []
        primary_cap = CAPABILITY_MAP.get(model_type)
        if primary_cap:
            caps.append(primary_cap)

        # image2text 在 tags 里但 model_type 是 "chat" 的情况 → 补 VISION
        if model_type == "chat" and "IMAGE2TEXT" in tags and "VISION" not in caps:
            caps.append("VISION")

        for cap in caps:
            if (name, cap) not in seen:
                seen.add((name, cap))
                result.append((name, cap))

    return result


def load_factories_data() -> dict[str, dict]:
    """
    加载 llm_factories.json，返回 {factory_name: {rank, llm_pairs}} 的字典。
    """
    if not FACTORIES_FILE.exists():
        return {}
    data = json.loads(FACTORIES_FILE.read_text(encoding="utf-8"))
    result: dict[str, dict] = {}
    for entry in data.get("factory_llm_infos", []):
        name = entry.get("name", "")
        rank = entry.get("rank")
        llm_list = entry.get("llm", [])
        if name:
            result[name] = {
                "rank": int(rank) if rank is not None else None,
                "url": entry.get("url", ""),
                "llm_pairs": model_cap_pairs_from_factories_llm(llm_list),
            }
    return result


def load_vendor(json_path: Path) -> dict | None:
    """
    读取单个 models/*.json，校验基本结构。
    无 url.default 的（本地工具）返回 None。
    """
    try:
        data = json.loads(json_path.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"  [WARN] 读取失败 {json_path.name}: {e}")
        return None

    url_obj = data.get("url", {})
    if not isinstance(url_obj, dict) or not url_obj.get("default"):
        return None  # 本地工具，跳过

    return data


def merge_model_pairs(
    primary: list[tuple[str, str]],
    supplement: list[tuple[str, str]],
) -> list[tuple[str, str]]:
    """合并两个列表，以 primary 为主，supplement 补充 primary 中没有的条目。"""
    seen = {(m, c) for m, c in primary}
    result = list(primary)
    for m, c in supplement:
        if (m, c) not in seen:
            seen.add((m, c))
            result.append((m, c))
    return result


# ─── SQL 生成 ────────────────────────────────────────────────────────────────

def gen_sql(vendors: list[dict]) -> str:
    lines: list[str] = []

    total_pairs = sum(len(v["model_cap_pairs"]) for v in vendors)

    lines.append("-- =============================================================")
    lines.append("-- toLink-Service：LLM 厂商与模型目录种子数据")
    lines.append("-- 由 scripts/import_ragflow_configs.py 自动生成，请勿手动编辑")
    lines.append("-- 数据来源：RAGFlow conf/ 目录（2026-06-06 导出）")
    lines.append(f"-- 厂商：{len(vendors)} 个，模型能力记录：{total_pairs} 条")
    lines.append("-- =============================================================")
    lines.append("")
    lines.append("USE tolink_rag_db;")
    lines.append("")
    lines.append("START TRANSACTION;")
    lines.append("")

    # ── 1. llm_system_provider ──────────────────────────────────────────────
    lines.append("-- ─── 1. 厂商基本信息 ──────────────────────────────────────────")
    lines.append("INSERT INTO llm_system_provider (provider_type, provider_name, api_base_url, default_protocol, is_active, priority)")
    lines.append("VALUES")

    provider_rows: list[str] = []
    for v in vendors:
        pt     = escape_sql(v["provider_type"])
        pn     = escape_sql(v["provider_name"])
        url    = escape_sql(v["api_base_url"])
        proto  = escape_sql(v["default_protocol"])
        active = "TRUE" if v["is_active"] else "FALSE"
        pri    = v["priority"]
        provider_rows.append(f"    ('{pt}', '{pn}', '{url}', '{proto}', {active}, {pri})")

    lines.append(",\n".join(provider_rows))
    lines.append("ON DUPLICATE KEY UPDATE")
    lines.append("    provider_name    = VALUES(provider_name),")
    lines.append("    api_base_url     = VALUES(api_base_url),")
    lines.append("    default_protocol = VALUES(default_protocol),")
    lines.append("    is_active        = VALUES(is_active),")
    lines.append("    priority         = VALUES(priority);")
    lines.append("")

    # ── 2. llm_provider_model ───────────────────────────────────────────────
    lines.append("-- ─── 2. 模型能力目录（一模型多能力 = 多行）──────────────────────")
    lines.append("-- 用子查询取 provider_id，避免依赖具体 ID 值")
    lines.append("")

    for v in vendors:
        pt = escape_sql(v["provider_type"])
        base_url = v["api_base_url"]
        model_cap_pairs = v["model_cap_pairs"]
        if not model_cap_pairs:
            continue

        lines.append(f"-- {v['provider_name']} ({pt})")
        lines.append("INSERT INTO llm_provider_model (provider_id, model_name, capability, protocol, api_base_url, is_active)")

        select_rows: list[str] = []
        for model_name, capability in model_cap_pairs:
            mn  = escape_sql(model_name)
            cap = escape_sql(capability)
            proto, model_url = model_protocol_and_url(pt, capability, base_url)
            proto_sql = escape_sql(proto)
            url_sql   = escape_sql(model_url)
            active    = "TRUE" if model_is_active(pt, capability) else "FALSE"
            select_rows.append(
                f"    SELECT id, '{mn}', '{cap}', '{proto_sql}', '{url_sql}', {active} "
                f"FROM llm_system_provider WHERE provider_type = '{pt}'"
            )

        lines.append("\nUNION ALL\n".join(select_rows))
        lines.append("ON DUPLICATE KEY UPDATE")
        lines.append("    protocol     = VALUES(protocol),")
        lines.append("    api_base_url = VALUES(api_base_url),")
        lines.append("    is_active    = VALUES(is_active);")
        lines.append("")

    lines.append("COMMIT;")
    lines.append("")

    return "\n".join(lines)


# ─── 主流程 ──────────────────────────────────────────────────────────────────

def main() -> None:
    if not MODELS_DIR.exists():
        print(f"错误：找不到 models/ 目录：{MODELS_DIR}")
        sys.exit(1)

    factories_data = load_factories_data()
    print(f"已加载 llm_factories.json：{len(factories_data)} 个厂商")

    json_files = sorted(MODELS_DIR.glob("*.json"))
    print(f"发现 models/*.json：{len(json_files)} 个文件")
    print()

    vendors: list[dict] = []
    skipped: list[str] = []

    for jf in json_files:
        stem = jf.stem.lower()

        if stem in SKIP_STEMS:
            skipped.append(f"{jf.name} (已配置跳过)")
            continue

        data = load_vendor(jf)
        if data is None:
            skipped.append(f"{jf.name} (无公网 API 地址)")
            continue

        vendor_name_raw = data.get("name", jf.stem)
        provider_type   = STEM_TO_PROVIDER_TYPE.get(stem, stem)
        # base_url：14 家重点厂商用权威种子覆盖，其余沿用 RAGFlow 源 url.default
        api_base_url    = provider_base_url(provider_type, data["url"]["default"])

        # 优先级：从 llm_factories.json 取 rank，处理名称不一致的情况
        factories_name = MODELS_TO_FACTORIES_NAME.get(vendor_name_raw, vendor_name_raw)
        factories_entry = factories_data.get(factories_name, {})
        priority = rank_to_priority(factories_entry.get("rank"))

        # 主模型列表：来自 models/*.json（能力数组更精确）
        primary_pairs = model_cap_pairs_from_models_json(data.get("models", []))

        # 补充列表：来自 llm_factories.json（模型更全）
        supplement_pairs = factories_entry.get("llm_pairs", [])
        model_cap_pairs = merge_model_pairs(primary_pairs, supplement_pairs)

        if not model_cap_pairs:
            skipped.append(f"{jf.name} (无可映射能力)")
            continue

        vendors.append({
            "provider_type":    provider_type,
            "provider_name":    vendor_name_raw,
            "api_base_url":     api_base_url,
            "default_protocol": provider_default_protocol(provider_type),
            "is_active":        provider_is_active(provider_type),
            "priority":         priority,
            "model_cap_pairs":  model_cap_pairs,
        })

        supplement_count = len(model_cap_pairs) - len(primary_pairs)
        supplement_note = f" [+{supplement_count} from factories]" if supplement_count > 0 else ""
        print(
            f"  ✓ {jf.name:30s} → type={provider_type:20s} "
            f"priority={priority:3d}  模型能力={len(model_cap_pairs)}{supplement_note}"
        )

    print()
    print(f"跳过的厂商（{len(skipped)} 个）：")
    for s in skipped:
        print(f"  ✗ {s}")

    total_pairs = sum(len(v["model_cap_pairs"]) for v in vendors)
    print()
    print(f"生成 SQL：{len(vendors)} 个厂商，{total_pairs} 条模型能力记录")

    sql = gen_sql(vendors)
    OUTPUT_SQL.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT_SQL.write_text(sql, encoding="utf-8")
    print(f"已写入：{OUTPUT_SQL}")


if __name__ == "__main__":
    main()
