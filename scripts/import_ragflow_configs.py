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
    lines.append("INSERT INTO llm_system_provider (provider_type, provider_name, api_base_url, is_active, priority)")
    lines.append("VALUES")

    provider_rows: list[str] = []
    for v in vendors:
        pt  = escape_sql(v["provider_type"])
        pn  = escape_sql(v["provider_name"])
        url = escape_sql(v["api_base_url"])
        pri = v["priority"]
        provider_rows.append(f"    ('{pt}', '{pn}', '{url}', TRUE, {pri})")

    lines.append(",\n".join(provider_rows))
    lines.append("ON DUPLICATE KEY UPDATE")
    lines.append("    provider_name  = VALUES(provider_name),")
    lines.append("    api_base_url   = VALUES(api_base_url),")
    lines.append("    is_active      = VALUES(is_active),")
    lines.append("    priority       = VALUES(priority);")
    lines.append("")

    # ── 2. llm_provider_model ───────────────────────────────────────────────
    lines.append("-- ─── 2. 模型能力目录（一模型多能力 = 多行）──────────────────────")
    lines.append("-- 用子查询取 provider_id，避免依赖具体 ID 值")
    lines.append("")

    for v in vendors:
        pt = escape_sql(v["provider_type"])
        model_cap_pairs = v["model_cap_pairs"]
        if not model_cap_pairs:
            continue

        lines.append(f"-- {v['provider_name']} ({pt})")
        lines.append("INSERT IGNORE INTO llm_provider_model (provider_id, model_name, capability)")

        select_rows: list[str] = []
        for model_name, capability in model_cap_pairs:
            mn  = escape_sql(model_name)
            cap = escape_sql(capability)
            select_rows.append(
                f"    SELECT id, '{mn}', '{cap}' FROM llm_system_provider WHERE provider_type = '{pt}'"
            )

        lines.append("\nUNION ALL\n".join(select_rows) + ";")
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
        api_base_url    = data["url"]["default"]
        provider_type   = STEM_TO_PROVIDER_TYPE.get(stem, stem)

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
            "provider_type":   provider_type,
            "provider_name":   vendor_name_raw,
            "api_base_url":    api_base_url,
            "priority":        priority,
            "model_cap_pairs": model_cap_pairs,
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
