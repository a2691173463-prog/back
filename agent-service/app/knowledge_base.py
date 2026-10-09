import asyncio
import hashlib
import json
import logging
import re
import time
import unicodedata
import uuid
from typing import Any, Literal

import httpx
from langchain_openai import OpenAIEmbeddings
from psycopg_pool import AsyncConnectionPool

from app.config import settings
from app.schemas import ExtractedQAItem


logger = logging.getLogger(__name__)
KnowledgeScope = Literal["PRIVATE", "GLOBAL", "POSITION"]

_pool: AsyncConnectionPool | None = None
_embeddings: OpenAIEmbeddings | None = None



def _clean_knowledge_text(value: str) -> str:
    """Normalize PDF extraction artifacts without flattening useful structure."""
    normalized = unicodedata.normalize("NFKC", value or "")
    normalized = normalized.replace("\u00a0", " ").replace("\u3000", " ")
    normalized = normalized.replace("\r\n", "\n").replace("\r", "\n")
    normalized = re.sub(r"[ \t]+", " ", normalized)
    normalized = re.sub(r" *\n *", "\n", normalized)
    normalized = re.sub(r"\n{3,}", "\n\n", normalized)
    return normalized.strip()


_SECTION_HEADINGS = {
    "概念", "数据类型", "编译性", "解释性", "赋值时", "方法调用时", "自动装箱的弊端",
    "泛型中的应用", "转换中的应用", "集合中的应用", "对象引用转换的问题", "注意一下几点",
}
_NOISY_QUESTION_MARKERS = (
    "扫码", "公众号", "加群", "训练营", "点击", "关注", "购买", "赞赏", "原文地址",
    "面试题有什么优势", "有错误怎么办", "你们说是不是", "笔记和画图工具",
)
_QUESTION_STARTERS = (
    "什么是", "什么叫", "为什么", "为何", "如何", "怎么", "怎样", "请解释",
    "请介绍", "说说", "说一下", "谈谈", "简述", "介绍一下", "讲一下",
)
_QUESTION_SUFFIXES = ("介绍一下", "说一下", "讲一下", "了解吗", "有了解吗")
_EXPLICIT_QUESTION_PREFIX = re.compile(
    r"^\s*(?:Q\s*\d+|问)\s*[.、)）:：]\s*",
    re.IGNORECASE,
)
_ANSWER_CUTOFF_MARKERS = (
    "最新的图解文章都在公众号首发",
    "求职辅导训练营",
)
_DOCUMENT_END_MARKERS = ("番外篇|小林图解故事", "番外篇｜小林图解故事")
_CODE_PATTERN = re.compile(
    r"(?:^|\n)\s*(?:"
    r"package\s+[\w.]|import\s+[\w.]|"
    r"(?:public|private|protected|static|final|abstract)\s+|"
    r"class\s+\w+|interface\s+\w+|enum\s+\w+|"
    r"@\w+|(?:if|for|while|switch|try|catch)\s*\(|"
    r"[\w<>?,.\[\]]+\s+\w+\s*\([^\n]*\)\s*\{"
    r")",
    re.IGNORECASE,
)


def _split_qa_blocks(content: str) -> list[str]:
    """Split QA-oriented documents by detected question boundaries instead of fixed size."""
    lines = _clean_knowledge_text(content).splitlines()
    blocks: list[str] = []
    current: list[str] = []

    for raw_line in lines:
        line = raw_line.strip()
        if not line:
            if current:
                current.append("")
            continue
        if _question_line(line):
            if current:
                block = _clean_knowledge_text("\n".join(current))
                if block:
                    blocks.append(block)
            current = [line]
        elif current:
            current.append(line)

    if current:
        block = _clean_knowledge_text("\n".join(current))
        if block:
            blocks.append(block)
    return blocks


def _question_line(value: str) -> tuple[str, str] | None:
    original = value.strip()
    has_explicit_prefix = bool(_EXPLICIT_QUESTION_PREFIX.match(original))
    line = re.sub(
        r"^\s*(?:(?:Q|q)?\d+[.、)）:：]?|问[：:]|[-*•])\s*",
        "",
        original,
    ).strip()
    normalized_heading = line.rstrip("。；;：:？?").strip()
    if (
        not 5 <= len(line) <= 160
        or normalized_heading in _SECTION_HEADINGS
        or any(marker in line for marker in _NOISY_QUESTION_MARKERS)
        or "http://" in line.lower()
        or "https://" in line.lower()
    ):
        return None

    if line.endswith(("？", "?")):
        return line, ""
    if has_explicit_prefix:
        return line.rstrip("。；;：:") + "？", ""
    if (
        len(line) <= 48
        and (line.startswith(_QUESTION_STARTERS) or line.endswith(_QUESTION_SUFFIXES))
        and not line.endswith(("。", "！", "!"))
    ):
        return line.rstrip("。；;：:") + "？", ""
    return None


def _topic_from_question(question: str, default_skill: str) -> str:
    topic = question.rstrip("？?")
    topic = re.sub(
        r"^(?:什么是|什么叫|为什么|如何|怎么|怎样|请解释|请介绍|说说|谈谈|简述)",
        "",
        topic,
    ).strip(" ，,。；;：:")
    return (topic or default_skill)[:120]


_SKILL_KEYWORDS = (
    ("RabbitMQ", ("rabbitmq", "消息队列", "消息中间件", "ack", "死信", "交换机")),
    ("Redis", ("redis", "缓存", "布隆过滤器", "跳表", "持久化")),
    ("MySQL", ("mysql", "innodb", "索引", "事务", "mvcc", "sql")),
    ("Spring Boot", ("spring boot", "springboot", "spring mvc", "springmvc", "bean", "ioc", "aop")),
    ("JVM", ("jvm", "垃圾回收", "gc", "类加载", "虚拟机")),
    (
        "计算机网络",
        (
            "计算机网络", "网络协议", "网络模型", "osi", "tcp/ip", "tcp", "udp",
            "http", "https", "dns", "icmp", "arp", "socket", "三次握手",
            "四次挥手", "拥塞控制", "流量控制", "网络层", "传输层", "应用层",
            "路由", "子网",
        ),
    ),
    (
        "操作系统",
        (
            "操作系统", "进程", "进程间通信", "进程调度", "虚拟内存", "页面置换",
            "分页", "分段", "文件系统", "系统调用", "用户态", "内核态", "死锁",
            "中断",
        ),
    ),
    ("Java 并发", ("线程", "并发", "锁", "cas", "synchronized", "threadlocal")),
    ("Java 集合", ("hashmap", "arraylist", "linkedlist", "concurrenthashmap", "集合")),
    ("微服务", ("微服务", "熔断", "限流", "负载均衡", "注册中心", "网关")),
)


def _skill_from_question(question: str, default_skill: str) -> str:
    lowered = question.lower()
    for skill, keywords in _SKILL_KEYWORDS:
        if any(keyword in lowered for keyword in keywords):
            return skill
    return (default_skill or "通用")[:80]


def _difficulty_from_question(question: str) -> str:
    advanced_markers = ("底层", "源码", "并发", "一致性", "故障", "权衡", "性能", "扩容")
    basic_markers = ("什么是", "是什么", "区别", "作用")
    if any(marker in question.lower() for marker in advanced_markers):
        return "advanced"
    if any(marker in question.lower() for marker in basic_markers):
        return "basic"
    return "medium"


def _contains_code(value: str) -> bool:
    return bool(_CODE_PATTERN.search(value))


def _trim_reference_answer(value: str) -> str:
    cutoff_indexes = [
        value.find(marker) for marker in _ANSWER_CUTOFF_MARKERS if marker in value
    ]
    if cutoff_indexes:
        value = value[: min(cutoff_indexes)]
    return _clean_knowledge_text(value)


def _embedding_text(item: ExtractedQAItem) -> str:
    parts = [item.skill, item.topic, item.question, *item.keywords]
    return " ".join(part.strip() for part in parts if part and part.strip())


async def _embed_documents(texts: list[str]) -> list[list[float]]:
    if not texts:
        return []
    client = _embedding_client()
    batch_size = max(1, int(client.chunk_size or 10))
    max_concurrency = max(1, settings.rag_embedding_max_concurrency)
    semaphore = asyncio.Semaphore(max_concurrency)
    batches = [texts[index : index + batch_size] for index in range(0, len(texts), batch_size)]
    batch_results: list[list[list[float]] | None] = [None] * len(batches)

    async def embed_batch(index: int, batch: list[str]) -> None:
        async with semaphore:
            batch_results[index] = await client.aembed_documents(
                batch,
                chunk_size=batch_size,
            )

    await asyncio.gather(
        *(embed_batch(index, batch) for index, batch in enumerate(batches))
    )
    return [
        vector
        for batch in batch_results
        if batch is not None
        for vector in batch
    ]


def _extract_interview_questions(
    content: str,
    default_skill: str,
) -> list[dict[str, Any]]:
    """Extract conservative question-answer units from interview-oriented documents."""
    lines = [line.strip() for line in _clean_knowledge_text(content).splitlines()]
    extracted: list[dict[str, Any]] = []
    current_question = ""
    answer_lines: list[str] = []

    def flush() -> None:
        nonlocal current_question, answer_lines
        answer = _trim_reference_answer("\n".join(answer_lines))
        if current_question and len(answer) >= 12:
            extracted.append(
                {
                    "question": current_question,
                    "reference_answer": answer,
                    "skill": _skill_from_question(current_question, default_skill),
                    "topic": _topic_from_question(current_question, default_skill or "通用"),
                    "difficulty": _difficulty_from_question(current_question),
                    "keywords": [],
                    "has_code": _contains_code(answer),
                }
            )
        current_question = ""
        answer_lines = []

    for line in lines:
        if not line:
            if current_question and answer_lines:
                answer_lines.append("")
            continue
        if any(marker in line for marker in _DOCUMENT_END_MARKERS):
            flush()
            break
        parsed = _question_line(line)
        if parsed:
            flush()
            current_question, inline_answer = parsed
            if inline_answer:
                answer_lines.append(inline_answer)
        elif current_question:
            answer_lines.append(line)
    flush()

    unique: dict[str, dict[str, Any]] = {}
    for item in extracted:
        fingerprint = hashlib.sha256(item["question"].encode("utf-8")).hexdigest()
        unique.setdefault(fingerprint, item)
    return list(unique.values())


def _select_relevant_candidates(
    candidates: list[dict[str, Any]], limit: int, threshold: float
) -> list[dict[str, Any]]:
    return [item for item in candidates if item["score"] >= threshold][:limit]


def _vector_literal(values: list[float]) -> str:
    return "[" + ",".join(str(value) for value in values) + "]"


def _normalize_scope(scope: str) -> KnowledgeScope:
    normalized = (scope or "PRIVATE").strip().upper()
    if normalized not in {"PRIVATE", "GLOBAL", "POSITION"}:
        raise ValueError("scope must be PRIVATE, GLOBAL, or POSITION")
    return normalized  # type: ignore[return-value]


def _normalize_position(position_code: str | None) -> str:
    return (position_code or "GENERAL").strip().upper()[:40] or "GENERAL"


_SKILL_FAMILIES = {
    "java": ("Java", "Java 集合", "Java 并发", "JVM"),
    "java集合": ("Java 集合", "Java"),
    "java并发": ("Java 并发", "Java", "JVM"),
    "并发编程": ("Java 并发", "Java"),
    "jvm": ("JVM", "Java"),
    "springboot": ("Spring Boot",),
    "springmvc": ("Spring Boot",),
    "mysql": ("MySQL", "数据库"),
    "数据库": ("MySQL", "数据库"),
    "redis": ("Redis",),
    "rabbitmq": ("RabbitMQ", "消息队列"),
    "kafka": ("Kafka", "消息队列"),
    "消息队列": ("RabbitMQ", "Kafka", "消息队列"),
    "微服务": ("微服务",),
}


def _skill_family(skill: str | None) -> tuple[str, ...]:
    normalized = re.sub(r"[^0-9a-z\u4e00-\u9fff]+", "", (skill or "").lower())
    if not normalized:
        return ()
    return _SKILL_FAMILIES.get(normalized, ((skill or "").strip(),))


def _embedding_client() -> OpenAIEmbeddings:
    global _embeddings
    if _embeddings is None:
        _embeddings = OpenAIEmbeddings(
            model=settings.rag_embedding_model,
            dimensions=settings.rag_embedding_dimensions,
            api_key=settings.model_api_key,
            base_url=settings.model_base_url,
            check_embedding_ctx_length=False,
            chunk_size=10,
        )
    return _embeddings


async def startup() -> None:
    global _pool
    if _pool is not None:
        return
    dimensions = settings.rag_embedding_dimensions
    if dimensions < 1 or dimensions > 2000:
        raise ValueError("RAG_EMBEDDING_DIMENSIONS must be between 1 and 2000")
    _pool = AsyncConnectionPool(
        conninfo=settings.postgres_url,
        min_size=1,
        max_size=max(4, settings.agent_max_concurrency),
        open=False,
    )
    await _pool.open()
    async with _pool.connection() as connection:
        async with connection.transaction():
            await connection.execute("CREATE EXTENSION IF NOT EXISTS vector")
            await connection.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm")
            await connection.execute("CREATE SCHEMA IF NOT EXISTS rag")
            await connection.execute(
                """
                CREATE TABLE IF NOT EXISTS rag.documents (
                    id UUID PRIMARY KEY,
                    owner_user_id BIGINT NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    file_name VARCHAR(255) NOT NULL,
                    category VARCHAR(80) NOT NULL,
                    content_hash CHAR(64) NOT NULL,
                    chunk_count INTEGER NOT NULL DEFAULT 0,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    UNIQUE (owner_user_id, content_hash)
                )
                """
            )
            # Existing installations are migrated in place; their documents remain private.
            await connection.execute(
                "ALTER TABLE rag.documents ADD COLUMN IF NOT EXISTS scope VARCHAR(16) NOT NULL DEFAULT 'PRIVATE'"
            )
            await connection.execute(
                "ALTER TABLE rag.documents ADD COLUMN IF NOT EXISTS position_code VARCHAR(40) NOT NULL DEFAULT 'GENERAL'"
            )
            await connection.execute(
                "ALTER TABLE rag.documents ADD COLUMN IF NOT EXISTS source_type VARCHAR(24) NOT NULL DEFAULT 'USER_UPLOAD'"
            )
            await connection.execute(
                "ALTER TABLE rag.documents ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1"
            )
            await connection.execute(
                "ALTER TABLE rag.documents ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'"
            )
            await connection.execute(
                "ALTER TABLE rag.documents ADD COLUMN IF NOT EXISTS retrieval_count BIGINT NOT NULL DEFAULT 0"
            )
            await connection.execute(
                "ALTER TABLE rag.documents ADD COLUMN IF NOT EXISTS last_retrieved_at TIMESTAMPTZ"
            )
            await connection.execute(
                "ALTER TABLE rag.documents DROP CONSTRAINT IF EXISTS documents_owner_user_id_content_hash_key"
            )
            await connection.execute(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_private_content
                ON rag.documents(owner_user_id, content_hash)
                WHERE scope = 'PRIVATE'
                """
            )
            await connection.execute(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS idx_rag_shared_content
                ON rag.documents(scope, position_code, content_hash)
                WHERE scope IN ('GLOBAL', 'POSITION')
                """
            )
            await connection.execute(
                "CREATE INDEX IF NOT EXISTS idx_rag_documents_scope ON rag.documents(scope, position_code, status)"
            )
            await connection.execute(
                f"""
                CREATE TABLE IF NOT EXISTS rag.interview_questions (
                    id UUID PRIMARY KEY,
                    document_id UUID NOT NULL REFERENCES rag.documents(id) ON DELETE CASCADE,
                    owner_user_id BIGINT NOT NULL,
                    question TEXT NOT NULL,
                    reference_answer TEXT NOT NULL,
                    skill VARCHAR(80) NOT NULL,
                    topic VARCHAR(160) NOT NULL,
                    difficulty VARCHAR(16) NOT NULL DEFAULT 'medium',
                    question_hash CHAR(64) NOT NULL,
                    metadata JSONB NOT NULL DEFAULT '{{}}'::jsonb,
                    embedding VECTOR({dimensions}) NOT NULL,
                    retrieval_count BIGINT NOT NULL DEFAULT 0,
                    last_retrieved_at TIMESTAMPTZ,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    UNIQUE(document_id, question_hash)
                )
                """
            )
            await connection.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_rag_questions_embedding_hnsw
                ON rag.interview_questions USING hnsw (embedding vector_cosine_ops)
                """
            )
            await connection.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_rag_questions_text_trgm
                ON rag.interview_questions USING gin (question gin_trgm_ops)
                """
            )
            await connection.execute(
                """
                CREATE TABLE IF NOT EXISTS rag.question_retrieval_logs (
                    id UUID PRIMARY KEY,
                    query_id UUID NOT NULL,
                    user_id BIGINT NOT NULL,
                    thread_id VARCHAR(100),
                    query TEXT NOT NULL,
                    question_id UUID REFERENCES rag.interview_questions(id) ON DELETE SET NULL,
                    vector_score DOUBLE PRECISION,
                    keyword_score DOUBLE PRECISION,
                    rerank_score DOUBLE PRECISION,
                    final_score DOUBLE PRECISION,
                    rank INTEGER,
                    selected BOOLEAN NOT NULL DEFAULT FALSE,
                    asked_question TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
                """
            )
            await connection.execute(
                "CREATE INDEX IF NOT EXISTS idx_rag_question_logs_thread ON rag.question_retrieval_logs(thread_id, created_at DESC)"
            )
            # Legacy passage-level RAG is retired; QA interview_questions is the only retrieval store.
            await connection.execute("DROP TABLE IF EXISTS rag.retrieval_logs")
            await connection.execute("DROP TABLE IF EXISTS rag.chunks")


async def shutdown() -> None:
    global _pool, _embeddings
    if _pool is not None:
        await _pool.close()
    _pool = None
    _embeddings = None


def _database() -> AsyncConnectionPool:
    if _pool is None:
        raise RuntimeError("Knowledge base is not initialized")
    return _pool


async def add_document(
    owner_user_id: int,
    title: str,
    file_name: str,
    category: str,
    content: str,
    scope: str = "PRIVATE",
    position_code: str = "GENERAL",
    source_type: str | None = None,
) -> dict[str, Any]:
    await startup()
    normalized = _clean_knowledge_text(content)
    normalized_scope = _normalize_scope(scope)
    normalized_position = _normalize_position(position_code)
    normalized_source = source_type or (
        "USER_UPLOAD" if normalized_scope == "PRIVATE" else "ADMIN_UPLOAD"
    )
    content_hash = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    duplicate_sql = (
        "owner_user_id = %s AND content_hash = %s AND scope = 'PRIVATE'"
        if normalized_scope == "PRIVATE"
        else "scope = %s AND position_code = %s AND content_hash = %s"
    )
    duplicate_params: tuple[Any, ...] = (
        (owner_user_id, content_hash)
        if normalized_scope == "PRIVATE"
        else (normalized_scope, normalized_position, content_hash)
    )
    async with _database().connection() as connection:
        existing = await connection.execute(
            f"""
            SELECT id, title, file_name, category, chunk_count, created_at,
                   scope, position_code, source_type, retrieval_count
            FROM rag.documents WHERE {duplicate_sql}
            """,
            duplicate_params,
        )
        row = await existing.fetchone()
        if row:
            return _document_row(row, duplicate=True)

    raw_qa_items = _extract_interview_questions(normalized, category.strip())
    qa_items = [ExtractedQAItem.model_validate(item) for item in raw_qa_items]
    logger.info(
        "Knowledge document parsed into %s deterministic QA units, file=%s",
        len(qa_items),
        file_name,
    )

    if qa_items:
        document_id = uuid.uuid4()
        embedding_inputs = [_embedding_text(item) for item in qa_items]
        vectors = await _embed_documents(embedding_inputs)

        async with _database().connection() as connection:
            async with connection.transaction():
                await connection.execute(
                    """
                    INSERT INTO rag.documents
                        (id, owner_user_id, title, file_name, category, content_hash,
                         chunk_count, scope, position_code, source_type)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        document_id, owner_user_id, title.strip(), file_name.strip(),
                        category.strip(), content_hash, len(qa_items), normalized_scope,
                        normalized_position, normalized_source,
                    ),
                )
                for index, (item, vector) in enumerate(zip(qa_items, vectors, strict=True)):
                    metadata = {
                        "document_id": str(document_id), "title": title.strip(),
                        "file_name": file_name.strip(), "category": category.strip(),
                        "question_index": index, "scope": normalized_scope,
                        "position_code": normalized_position, "source_type": normalized_source,
                        "question": item.question, "skill": item.skill, "topic": item.topic,
                        "difficulty": item.difficulty, "keywords": item.keywords,
                        "has_code": item.has_code, "extraction_mode": "DETERMINISTIC_QA_UNIT",
                    }
                    question_hash = hashlib.sha256(item.question.encode("utf-8")).hexdigest()
                    await connection.execute(
                        """
                        INSERT INTO rag.interview_questions
                            (id, document_id, owner_user_id, question, reference_answer,
                             skill, topic, difficulty, question_hash, metadata, embedding)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb, %s::vector)
                        """,
                        (
                            uuid.uuid4(), document_id, owner_user_id, item.question,
                            item.reference_answer, item.skill, item.topic, item.difficulty,
                            question_hash, json.dumps(metadata, ensure_ascii=False),
                            _vector_literal(vector),
                        ),
                    )
        return {
            "id": str(document_id), "title": title.strip(), "file_name": file_name.strip(),
            "category": category.strip(), "chunk_count": len(qa_items),
            "scope": normalized_scope, "position_code": normalized_position,
            "source_type": normalized_source, "retrieval_count": 0,
            "question_count": len(qa_items), "duplicate": False,
            "extraction_mode": "DETERMINISTIC_QA_UNIT",
        }

    raise ValueError(
        "No complete QA units were found. Questions must be on separate lines and "
        "end with '?' or '？', or start with an explicit 'Q1:'/'问：' prefix."
    )


def _document_row(row: tuple[Any, ...], duplicate: bool = False) -> dict[str, Any]:
    return {
        "id": str(row[0]), "title": row[1], "file_name": row[2],
        "category": row[3], "chunk_count": row[4],
        "created_at": row[5].isoformat(), "scope": row[6],
        "position_code": row[7], "source_type": row[8],
        "retrieval_count": row[9], "duplicate": duplicate,
    }


async def list_documents(
    owner_user_id: int,
    include_shared: bool = False,
    shared_only: bool = False,
) -> list[dict[str, Any]]:
    await startup()
    if shared_only:
        where = "scope IN ('GLOBAL', 'POSITION')"
        params: tuple[Any, ...] = ()
    elif include_shared:
        where = "owner_user_id = %s OR scope IN ('GLOBAL', 'POSITION')"
        params = (owner_user_id,)
    else:
        where = "owner_user_id = %s AND scope = 'PRIVATE'"
        params = (owner_user_id,)
    async with _database().connection() as connection:
        result = await connection.execute(
            f"""
            SELECT id, title, file_name, category, chunk_count, created_at,
                   scope, position_code, source_type, retrieval_count
            FROM rag.documents WHERE {where} AND status = 'ACTIVE'
            ORDER BY created_at DESC
            """,
            params,
        )
        rows = await result.fetchall()
    return [_document_row(row) for row in rows]


async def delete_document(
    document_id: str,
    requester_user_id: int,
    allow_shared: bool = False,
) -> bool:
    await startup()
    where = "id = %s" if allow_shared else "id = %s AND owner_user_id = %s AND scope = 'PRIVATE'"
    params: tuple[Any, ...] = (
        (document_id,) if allow_shared else (document_id, requester_user_id)
    )
    async with _database().connection() as connection:
        result = await connection.execute(f"DELETE FROM rag.documents WHERE {where}", params)
        return result.rowcount > 0


def _access_clause(category: str | None) -> tuple[str, list[Any]]:
    clause = """
        d.status = 'ACTIVE' AND (
            d.scope = 'GLOBAL'
            OR (d.scope = 'POSITION' AND d.position_code IN (%s, 'GENERAL'))
            OR (d.scope = 'PRIVATE' AND d.owner_user_id = %s)
        )
    """
    params: list[Any] = []
    if category and category.strip():
        clause += " AND LOWER(d.category) = LOWER(%s)"
        params.append(category.strip())
    return clause, params


def _question_ngrams(value: str) -> set[str]:
    normalized = re.sub(r"[\W_]+", "", value.lower())
    return {normalized[index : index + 2] for index in range(max(0, len(normalized) - 1))}


def question_similarity(left: str, right: str) -> float:
    left_grams = _question_ngrams(left)
    right_grams = _question_ngrams(right)
    if not left_grams or not right_grams:
        return 0.0
    return len(left_grams & right_grams) / len(left_grams | right_grams)


def _topics_overlap(left: str, right: str) -> bool:
    normalized_left = re.sub(r"[^0-9a-z\u4e00-\u9fff]+", "", left.lower())
    normalized_right = re.sub(r"[^0-9a-z\u4e00-\u9fff]+", "", right.lower())
    if not normalized_left or not normalized_right:
        return False
    shorter = min(len(normalized_left), len(normalized_right))
    return (
        normalized_left == normalized_right
        or (
            shorter >= 4
            and (
                normalized_left in normalized_right
                or normalized_right in normalized_left
            )
        )
        or question_similarity(normalized_left, normalized_right) >= 0.66
    )


async def _rerank_question_candidates(
    query: str,
    candidates: list[dict[str, Any]],
) -> bool:
    if not settings.rag_rerank_enabled or not candidates:
        return False
    payload = {
        "model": settings.rag_rerank_model,
        "query": query,
        "documents": [
            (
                f"技能：{item['skill']}\n"
                f"主题：{item['topic']}\n"
                f"难度：{item['difficulty']}\n"
                f"问题：{item['question']}"
            )
            for item in candidates
        ],
        "top_n": len(candidates),
        "instruct": "Rank interview questions for assessing the requested technical skill.",
    }
    try:
        async with httpx.AsyncClient(timeout=settings.rag_rerank_timeout_seconds) as client:
            response = await client.post(
                settings.rag_rerank_url,
                headers={"Authorization": f"Bearer {settings.model_api_key}"},
                json=payload,
            )
            response.raise_for_status()
            data = response.json()
        for result in data.get("results") or data.get("output", {}).get("results") or []:
            index = result.get("index")
            if isinstance(index, int) and 0 <= index < len(candidates):
                candidates[index]["rerank_score"] = float(
                    result.get("relevance_score", 0)
                )
        return any("rerank_score" in item for item in candidates)
    except Exception:
        logger.warning("Question rerank failed; using hybrid ranking", exc_info=True)
        return False


def _question_retrieval_levels(
    access_clause: str,
    access_params: list[Any],
    skill: str,
    difficulty: str,
) -> list[tuple[str, list[Any], str]]:
    """Return retrieval filters from strictest to broadest."""
    skill_family = _skill_family(skill)
    valid_difficulty = difficulty in {"basic", "medium", "advanced"}
    levels: list[tuple[str, list[Any], str]] = []
    if skill and valid_difficulty:
        levels.append((
            access_clause + " AND LOWER(q.skill) = LOWER(%s) AND LOWER(q.difficulty) = LOWER(%s)",
            [*access_params, skill, difficulty],
            "exact-skill+difficulty",
        ))
    if skill:
        levels.append((
            access_clause + " AND LOWER(q.skill) = LOWER(%s)",
            [*access_params, skill],
            "exact-skill",
        ))
    if skill_family and valid_difficulty:
        placeholders = ", ".join(["LOWER(%s)"] * len(skill_family))
        levels.append((
            access_clause + f" AND LOWER(q.skill) IN ({placeholders}) AND LOWER(q.difficulty) = LOWER(%s)",
            [*access_params, *skill_family, difficulty],
            "skill-family+difficulty",
        ))
    if skill_family:
        placeholders = ", ".join(["LOWER(%s)"] * len(skill_family))
        levels.append((
            access_clause + f" AND LOWER(q.skill) IN ({placeholders})",
            [*access_params, *skill_family],
            "skill-family",
        ))
    if valid_difficulty:
        levels.append((
            access_clause + " AND LOWER(q.difficulty) = LOWER(%s)",
            [*access_params, difficulty],
            "difficulty",
        ))
    levels.append((access_clause, access_params, "broad"))
    return levels


async def search_interview_questions(
    query: str,
    owner_user_id: int,
    position_code: str = "GENERAL",
    thread_id: str | None = None,
    asked_questions: list[dict[str, Any]] | None = None,
    excluded_topics: list[str] | None = None,
    top_k: int | None = None,
    skill: str | None = None,
    difficulty: str | None = None,
) -> list[dict[str, Any]]:
    await startup()
    normalized_query = _clean_knowledge_text(query)[:700]
    if not normalized_query:
        return []
    started_at = time.perf_counter()
    limit = max(1, min(top_k or settings.interview_question_top_k, 5))
    candidate_limit = max(limit, min(settings.rag_candidate_k, 30))
    normalized_position = _normalize_position(position_code)
    embedding_started_at = time.perf_counter()
    query_vector = await _embedding_client().aembed_query(normalized_query)
    embedding_seconds = time.perf_counter() - embedding_started_at
    access_clause, access_params = _access_clause(None)
    normalized_skill = (skill or "").strip()
    normalized_difficulty = (difficulty or "").strip().lower()
    retrieval_levels = _question_retrieval_levels(
        access_clause,
        access_params,
        normalized_skill,
        normalized_difficulty,
    )
    base_select = """
        SELECT q.id, q.document_id, q.question, q.reference_answer,
               q.skill, q.topic, q.difficulty, q.metadata,
               d.scope, d.position_code, d.title, d.file_name, d.category,
               1 - (q.embedding <=> %s::vector) AS vector_score,
               GREATEST(
                   similarity(LOWER(q.question), LOWER(%s)),
                   word_similarity(LOWER(%s), LOWER(q.question)),
                   similarity(LOWER(q.topic), LOWER(%s))
               ) AS keyword_score
        FROM rag.interview_questions q
        JOIN rag.documents d ON d.id = q.document_id
    """
    base_params: list[Any] = [
        _vector_literal(query_vector), normalized_query, normalized_query,
        normalized_query, normalized_position, owner_user_id,
    ]
    asked = asked_questions or []
    asked_ids = {str(item.get("source_question_id")) for item in asked if item.get("source_question_id")}
    asked_texts = [str(item.get("question") or "") for item in asked]
    excluded_topic_values = [
        str(value) for value in (excluded_topics or []) if str(value).strip()
    ]

    db_started_at = time.perf_counter()
    rerank_seconds = 0.0
    retrieval_level = "broad"
    candidates: list[dict[str, Any]] = []
    selected: list[dict[str, Any]] = []

    async with _database().connection() as connection:
        for where_clause, where_params, level_name in retrieval_levels:
            vector_sql = f"{base_select} WHERE {where_clause} ORDER BY q.embedding <=> %s::vector LIMIT %s"
            keyword_sql = f"{base_select} WHERE {where_clause} ORDER BY keyword_score DESC LIMIT %s"
            vector_rows = await (
                await connection.execute(
                    vector_sql,
                    [*base_params, *where_params, _vector_literal(query_vector), candidate_limit],
                )
            ).fetchall()
            keyword_rows = await (
                await connection.execute(
                    keyword_sql,
                    [*base_params, *where_params, candidate_limit],
                )
            ).fetchall()

            merged: dict[str, dict[str, Any]] = {}
            for source, rows in (("vector", vector_rows), ("keyword", keyword_rows)):
                for rank, row in enumerate(rows, start=1):
                    key = str(row[0])
                    item = merged.setdefault(
                        key,
                        {
                            "id": key, "document_id": str(row[1]), "question": row[2],
                            "reference_answer": row[3], "skill": row[4], "topic": row[5],
                            "difficulty": row[6], "metadata": row[7], "scope": row[8],
                            "position_code": row[9], "title": row[10], "file_name": row[11],
                            "category": row[12], "vector_score": float(row[13]),
                            "keyword_score": float(row[14]),
                        },
                    )
                    item[f"{source}_rank"] = rank

            level_candidates: list[dict[str, Any]] = []
            for item in merged.values():
                if item["id"] in asked_ids:
                    continue
                if any(
                    question_similarity(item["question"], previous)
                    >= settings.interview_question_similarity_threshold
                    for previous in asked_texts if previous
                ):
                    continue
                if any(
                    _topics_overlap(str(item.get("topic") or ""), previous)
                    for previous in excluded_topic_values
                ):
                    continue
                reciprocal_rank = sum(
                    1 / (60 + item[name])
                    for name in ("vector_rank", "keyword_rank") if name in item
                ) * 30
                item["fusion_score"] = min(
                    1.0,
                    0.70 * max(0.0, item["vector_score"])
                    + 0.20 * max(0.0, item["keyword_score"])
                    + 0.10 * reciprocal_rank,
                )
                level_candidates.append(item)

            rerank_started_at = time.perf_counter()
            reranked = await _rerank_question_candidates(normalized_query, level_candidates)
            rerank_seconds += time.perf_counter() - rerank_started_at
            for item in level_candidates:
                item["score"] = (
                    min(
                        1.0,
                        0.80 * item["rerank_score"]
                        + 0.15 * max(0.0, item["vector_score"])
                        + 0.05 * max(0.0, item["keyword_score"]),
                    )
                    if reranked and "rerank_score" in item
                    else item["fusion_score"]
                )
            level_candidates.sort(key=lambda item: item["score"], reverse=True)
            level_selected = _select_relevant_candidates(
                level_candidates, limit, settings.rag_final_score_threshold
            )
            if level_selected:
                retrieval_level = level_name
                candidates = level_candidates
                selected = level_selected
                break

            logger.info(
                "[RAG FALLBACK] level=%s yielded no usable candidates after filtering/threshold; relaxing constraints",
                level_name,
            )

    db_seconds = time.perf_counter() - db_started_at - rerank_seconds
    query_id = uuid.uuid4()
    async with _database().connection() as connection:
        async with connection.transaction():
            for rank, item in enumerate(selected, start=1):
                await connection.execute(
                    """
                    INSERT INTO rag.question_retrieval_logs
                        (id, query_id, user_id, thread_id, query, question_id,
                         vector_score, keyword_score, rerank_score, final_score, rank)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """,
                    (
                        uuid.uuid4(), query_id, owner_user_id, thread_id,
                        normalized_query, item["id"], item["vector_score"],
                        item["keyword_score"], item.get("rerank_score"),
                        item["score"], rank,
                    ),
                )
    logger.info(
        "[RAG PERF] interview-question retrieval level=%s skill=%s difficulty=%s embedding=%.3fs db=%.3fs rerank=%.3fs total=%.3fs candidates=%d selected=%d",
        retrieval_level,
        normalized_skill or "-",
        normalized_difficulty or "-",
        embedding_seconds,
        db_seconds,
        rerank_seconds,
        time.perf_counter() - started_at,
        len(candidates),
        len(selected),
    )
    return [
        {
            "id": item["id"], "question": item["question"],
            "reference_answer": item["reference_answer"], "skill": item["skill"],
            "topic": item["topic"], "difficulty": item["difficulty"],
            "score": round(item["score"], 4), "document_id": item["document_id"],
            "source": {
                "title": item["title"], "file_name": item["file_name"],
                "scope": item["scope"], "position_code": item["position_code"],
                "category": item["category"],
            },
        }
        for item in selected
    ]


async def record_question_selection(
    thread_id: str,
    question_id: str,
    asked_question: str,
) -> None:
    await startup()
    async with _database().connection() as connection:
        async with connection.transaction():
            await connection.execute(
                """
                UPDATE rag.question_retrieval_logs SET selected = TRUE, asked_question = %s
                WHERE id = (
                    SELECT id FROM rag.question_retrieval_logs
                    WHERE thread_id = %s AND question_id = %s
                    ORDER BY created_at DESC LIMIT 1
                )
                """,
                (asked_question, thread_id, question_id),
            )
            await connection.execute(
                """
                UPDATE rag.interview_questions
                SET retrieval_count = retrieval_count + 1, last_retrieved_at = NOW()
                WHERE id = %s
                """,
                (question_id,),
            )


async def retrieval_stats() -> dict[str, Any]:
    await startup()
    async with _database().connection() as connection:
        summary = await connection.execute(
            """
            SELECT
                COUNT(*) FILTER (WHERE scope = 'GLOBAL'),
                COUNT(*) FILTER (WHERE scope = 'POSITION'),
                COUNT(*) FILTER (WHERE scope = 'PRIVATE'),
                COALESCE(SUM(retrieval_count), 0)
            FROM rag.documents WHERE status = 'ACTIVE'
            """
        )
        summary_row = await summary.fetchone()
        popular = await connection.execute(
            """
            SELECT id, title, scope, position_code, retrieval_count
            FROM rag.documents WHERE status = 'ACTIVE'
            ORDER BY retrieval_count DESC, created_at DESC LIMIT 10
            """
        )
        popular_rows = await popular.fetchall()
        question_quality = await connection.execute(
            """
            SELECT
                (SELECT COUNT(*) FROM rag.interview_questions),
                COUNT(DISTINCT query_id),
                COUNT(*) FILTER (WHERE selected = TRUE)
            FROM rag.question_retrieval_logs
            """
        )
        question_quality_row = await question_quality.fetchone()
        popular_questions = await connection.execute(
            """
            SELECT id, question, skill, topic, difficulty, retrieval_count
            FROM rag.interview_questions
            ORDER BY retrieval_count DESC, created_at DESC LIMIT 10
            """
        )
        popular_question_rows = await popular_questions.fetchall()
    return {
        "global_documents": summary_row[0],
        "position_documents": summary_row[1],
        "private_documents": summary_row[2],
        "document_hits": summary_row[3],
        "indexed_questions": question_quality_row[0],
        "question_queries": question_quality_row[1],
        "selected_question_hits": question_quality_row[2],
        "popular_documents": [
            {
                "id": str(row[0]), "title": row[1], "scope": row[2],
                "position_code": row[3], "retrieval_count": row[4],
            }
            for row in popular_rows
        ],
        "popular_questions": [
            {
                "id": str(row[0]), "question": row[1], "skill": row[2],
                "topic": row[3], "difficulty": row[4], "retrieval_count": row[5],
            }
            for row in popular_question_rows
        ],
    }
