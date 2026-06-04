#!/usr/bin/env python3
"""Validate project skills under .ai/skills/ (toLink-Service / Java·Maven).

针对每个 ``.ai/skills/<name>/SKILL.md`` 执行四类机器化质量门禁：

1. frontmatter 完整性：``name``（须等于目录名）、``description`` 必填；``when_to_use`` 缺失告警。
2. 死引用扫描：正文里出现的 Maven 模块 / ``docs/`` ``scripts/`` ``.ai/`` ``.claude/``
   路径必须真实存在（含 glob 前缀目录判定）。
3. 技术栈黑名单：命中非本项目栈的关键词（Python/RAG，如 FastAPI/Qdrant/pytest）即报错，
   防止从 Python 端（toLink-Rag）抄来的模板腐化。
4. 孤儿目录：``.ai/skills/<name>/`` 存在却没有 SKILL.md。

不依赖 PyYAML：frontmatter 用轻量行解析（本项目 SKILL.md 均为单行标量键）。

Usage:
    python3 scripts/check_skills.py                 # 扫描全部 skill（pre-commit / CI）
    python3 scripts/check_skills.py --skills-dir .ai/skills

Exit codes:
    0  - 无 error（可能有 warning）
    1  - 存在一个或多个 error
    2  - 运行期失败（路径缺失等）
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SKILLS_DIR = REPO_ROOT / ".ai" / "skills"

# 路径根：只校验这些前缀开头的 token，避免误判普通词。对齐 Service 的 Maven 多模块布局。
PATH_RE = re.compile(
    r"(?:link-api|link-service|link-model|link-mapper|link-core|link-components"
    r"|docs|scripts|\.ai|\.claude)/[A-Za-z0-9_./*\-]+"
)

# 反引号包裹、不含路径分隔符的「裸文档名」（如 `api_contracts.md`）。按 basename 全仓存在性兜底。
BARE_DOC_RE = re.compile(r"`([A-Za-z0-9_.\-]+\.(?:md|feature))`")

# 工作流在 .specs/<feature>/ 下临时生成、不进仓库的产物：不做存在性校验。
GENERATED_ARTIFACTS = frozenset(
    {
        "brief.md",
        "acceptance.feature",
        "technical_design.md",
        "implementation_report.md",
        "feature_info.md",
        "requirement.md",
        "testing_delivery.md",
    }
)

# 明显的占位/示例路径片段：命中则跳过死引用校验。
# "..." 是文档里省略包路径的常见缩写（如 link-components/.../mq/AbstractMQ.java），按缩写跳过。
PLACEHOLDER_SEGMENTS = ("your_", "my_", "xxx", "example", "sample_", "foo", "placeholder", "demo_", "<", "...")

# 裸文档名所在行若含这些「否定 / 运行时生成」语境词，说明它本就不该存在，跳过 basename 兜底。
NEGATION_CUES = (
    "没有", "不要", "不需要", "不生成", "不落", "不得", "不应", "勿", "禁止", "无此", "废弃",
    "produces", "generates", "generate", "creating", "output", "do not", "don't",
)

# 非本项目技术栈关键词（Python/FastAPI/RAG）。命中视为从 toLink-Rag 抄来的模板腐化，报错。
# 只选绝不会出现在正确 Java/Service skill 正文里的词，避免误伤。
STACK_BLACKLIST = [
    "import torch",
    "from fastapi",
    "FastAPI",
    "@router.",
    "@app.get",
    "@app.post",
    "uvicorn",
    "pytest",
    "Pydantic",
    "async def ",
    "Qdrant",
    "Milvus",
    "alembic",
    "sentence-transformers",
    "FlagEmbedding",
]


class Issue:
    def __init__(self, skill: str, level: str, msg: str) -> None:
        self.skill = skill
        self.level = level  # "error" | "warning"
        self.msg = msg


def _parse_frontmatter(text: str) -> dict | None:
    """轻量解析 SKILL.md 顶部 YAML frontmatter（单行标量键），无 frontmatter 返回 None。"""
    if not text.startswith("---"):
        return None
    end = text.find("\n---", 3)
    if end == -1:
        return None
    raw = text[3:end].strip()
    data: dict[str, str] = {}
    for line in raw.splitlines():
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if line[0] in (" ", "\t"):  # 跳过嵌套键，只取顶层
            continue
        if ":" in line:
            k, _, v = line.partition(":")
            data[k.strip()] = v.strip().strip('"').strip("'")
    return data or None


def _is_placeholder(token: str) -> bool:
    low = token.lower()
    return any(seg in low for seg in PLACEHOLDER_SEGMENTS)


def _path_exists(token: str) -> bool:
    """判断引用路径是否存在；含 ``*`` 时退化为校验其字面父目录前缀存在。"""
    token = token.rstrip(".,;:)】」`\"'")
    if "*" in token:
        prefix = token.split("*", 1)[0].rstrip("/")
        if not prefix:
            return True
        return (REPO_ROOT / prefix).exists()
    return (REPO_ROOT / token).exists()


_REPO_BASENAMES: set[str] | None = None
_SKIP_DIRS = {".git", ".venv", "node_modules", "__pycache__", ".specs", "target", ".idea", ".vscode"}


def _repo_basenames() -> set[str]:
    global _REPO_BASENAMES
    if _REPO_BASENAMES is None:
        import os

        names: set[str] = set()
        for root, dirs, files in os.walk(REPO_ROOT):
            dirs[:] = [d for d in dirs if d not in _SKIP_DIRS]
            names.update(files)
        _REPO_BASENAMES = names
    return _REPO_BASENAMES


def _body_after_frontmatter(text: str) -> str:
    if text.startswith("---"):
        end = text.find("\n---", 3)
        if end != -1:
            nl = text.find("\n", end + 1)
            return text[nl + 1 :] if nl != -1 else ""
    return text


def check_skill(skill_dir: Path) -> list[Issue]:
    name = skill_dir.name
    issues: list[Issue] = []
    skill_md = skill_dir / "SKILL.md"

    if not skill_md.is_file():
        issues.append(Issue(name, "error", "缺少 SKILL.md（孤儿目录）"))
        return issues

    text = skill_md.read_text(encoding="utf-8")
    fm = _parse_frontmatter(text)

    if fm is None:
        issues.append(Issue(name, "error", "缺少或无法解析 YAML frontmatter"))
    else:
        fm_name = str(fm.get("name", "")).strip()
        if not fm_name:
            issues.append(Issue(name, "error", "frontmatter 缺少 name"))
        elif fm_name != name:
            issues.append(Issue(name, "error", f"name='{fm_name}' 与目录名 '{name}' 不一致"))
        desc = str(fm.get("description", "")).strip()
        if not desc:
            issues.append(Issue(name, "error", "frontmatter 缺少 description"))
        elif len(desc) < 20:
            issues.append(Issue(name, "warning", "description 过短（<20 字符），触发可能不准"))
        if not str(fm.get("when_to_use", "")).strip():
            issues.append(Issue(name, "warning", "frontmatter 缺少 when_to_use（建议补充触发/转交说明）"))

    # 技术栈黑名单（全文匹配）
    for kw in STACK_BLACKLIST:
        if kw in text:
            issues.append(Issue(name, "error", f"命中非本项目技术栈关键词（疑似 Python/RAG 模板残留）: '{kw.strip()}'"))

    # 死引用扫描（仅正文，去重）
    body = _body_after_frontmatter(text)
    seen: set[str] = set()
    for m in PATH_RE.finditer(body):
        token = m.group(0)
        if token in seen:
            continue
        seen.add(token)
        if _is_placeholder(token):
            continue
        if not _path_exists(token):
            issues.append(Issue(name, "error", f"引用了不存在的路径: {token}"))

    # 裸文档名扫描，按 basename 全仓兜底
    basenames = _repo_basenames()
    seen_bare: set[str] = set()
    for m in BARE_DOC_RE.finditer(body):
        fname = m.group(1)
        if fname in seen_bare:
            continue
        seen_bare.add(fname)
        if fname in GENERATED_ARTIFACTS or _is_placeholder(fname):
            continue
        if fname in basenames:
            continue
        line_start = body.rfind("\n", 0, m.start()) + 1
        line_end = body.find("\n", m.end())
        line = body[line_start : line_end if line_end != -1 else len(body)].lower()
        if any(cue in line for cue in NEGATION_CUES):
            continue
        issues.append(Issue(name, "error", f"引用了不存在的文档名（全仓无此文件）: {fname}"))

    return issues


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Validate project skills under .ai/skills/.")
    parser.add_argument("--skills-dir", default=str(DEFAULT_SKILLS_DIR), help="skills 根目录（默认 .ai/skills）")
    args = parser.parse_args(argv)

    skills_root = Path(args.skills_dir)
    if not skills_root.is_dir():
        print(f"ERROR: skills 目录不存在: {skills_root}", file=sys.stderr)
        return 2

    skill_dirs = sorted(p for p in skills_root.iterdir() if p.is_dir() and not p.name.startswith("."))
    if not skill_dirs:
        print(f"未发现任何 skill: {skills_root}")
        return 0

    all_issues: list[Issue] = []
    for d in skill_dirs:
        all_issues.extend(check_skill(d))

    errors = [i for i in all_issues if i.level == "error"]
    warnings = [i for i in all_issues if i.level == "warning"]

    if all_issues:
        for i in sorted(all_issues, key=lambda x: (x.skill, x.level)):
            mark = "ERROR" if i.level == "error" else "WARN "
            print(f"[{mark}] {i.skill}: {i.msg}")
        print("-" * 60)

    print(f"扫描 {len(skill_dirs)} 个 skill | errors={len(errors)} warnings={len(warnings)}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
