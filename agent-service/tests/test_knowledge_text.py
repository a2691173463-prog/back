import asyncio
import unittest

from app import knowledge_base
from app.config import settings
from app.knowledge_base import (
    _clean_knowledge_text,
    _contains_code,
    _embedding_text,
    _extract_interview_questions,
    _question_line,
    _select_relevant_candidates,
    _skill_family,
    question_similarity,
)
from app.schemas import ExtractedQAItem


class KnowledgeTextCleanupTest(unittest.TestCase):
    def test_normalizes_pdf_compatibility_characters_and_line_endings(self):
        value = "使⽤Java\r\n\u00a0 处理   数据\r\n\r\n\r\n下一段"

        cleaned = _clean_knowledge_text(value)

        self.assertEqual("使用Java\n处理 数据\n\n下一段", cleaned)

    def test_low_final_scores_are_not_rescued_by_vector_score(self):
        candidates = [
            {"score": 0.3584, "vector_score": 0.82},
            {"score": 0.5809, "vector_score": 0.60},
        ]

        selected = _select_relevant_candidates(candidates, limit=5, threshold=0.45)

        self.assertEqual([candidates[1]], selected)

    def test_extracts_question_answer_pairs_and_filters_marketing(self):
        content = """HashMap 为什么需要把容量设置为 2 的幂？
因为取模可以通过位运算完成，并使哈希分布更均匀，减少冲突。
点击了解训练营？
扫码关注公众号获得资料。
"""
        questions = _extract_interview_questions(content, "Java")
        self.assertEqual(1, len(questions))
        self.assertIn("HashMap", questions[0]["question"])
        self.assertIn("位运算", questions[0]["reference_answer"])
        self.assertEqual("Java 集合", questions[0]["skill"])
        self.assertEqual("medium", questions[0]["difficulty"])

    def test_question_boundary_requires_question_mark_or_explicit_prefix(self):
        self.assertIsNone(_question_line("Java基础面试题 95题 2.5w字"))
        self.assertIsNone(_question_line("List<String> configurations ="))
        self.assertIsNone(_question_line("public static void main(String[] args)"))
        self.assertEqual(
            ("JVM、JDK、JRE三者关系？", ""),
            _question_line("JVM、JDK、JRE三者关系？"),
        )
        self.assertEqual(
            ("HashMap 的底层结构？", ""),
            _question_line("Q12：HashMap 的底层结构"),
        )
        self.assertEqual(
            ("介绍一下服务熔断？", ""),
            _question_line("介绍一下服务熔断"),
        )

    def test_trims_promotional_tail_from_reference_answer(self):
        content = """如何实现负载均衡？
可以使用一致性哈希算法，让相同用户映射到相同服务器。
最新的图解文章都在公众号首发，别忘记关注。
番外篇|小林图解故事
大家看到这里是不是很开心？
"""

        questions = _extract_interview_questions(content, "Java")

        self.assertEqual(1, len(questions))
        self.assertIn("一致性哈希", questions[0]["reference_answer"])
        self.assertNotIn("公众号", questions[0]["reference_answer"])

    def test_preserves_code_in_answer_without_adding_it_to_embedding_text(self):
        answer = """HashMap 基于数组和链表实现。
public class Demo {
    public static void main(String[] args) {}
}
"""
        item = ExtractedQAItem(
            question="HashMap 的底层结构是什么？",
            reference_answer=answer,
            has_code=_contains_code(answer),
            skill="Java 集合",
            topic="HashMap 底层结构",
            difficulty="medium",
            keywords=["HashMap", "数组", "链表"],
        )

        embedding_text = _embedding_text(item)

        self.assertTrue(item.has_code)
        self.assertIn("public class Demo", item.reference_answer)
        self.assertIn("HashMap 的底层结构是什么？", embedding_text)
        self.assertNotIn("public class Demo", embedding_text)

    def test_question_similarity_detects_near_duplicates(self):
        self.assertGreaterEqual(
            question_similarity("HashMap 为什么容量是 2 的幂？", "为什么 HashMap 容量要使用 2 的幂？"),
            0.5,
        )

    def test_skill_family_expands_broad_java_and_aliases(self):
        self.assertEqual(("Java", "Java 集合", "Java 并发", "JVM"), _skill_family("Java"))
        self.assertEqual(("Java 并发", "Java"), _skill_family("并发编程"))
        self.assertEqual(("MySQL", "数据库"), _skill_family("MySQL"))

    def test_skill_keywords_classify_network_and_operating_system_questions(self):
        self.assertEqual(
            "计算机网络",
            knowledge_base._skill_from_question("TCP 为什么需要三次握手？", "通用"),
        )
        self.assertEqual(
            "操作系统",
            knowledge_base._skill_from_question("进程与线程有什么区别？", "通用"),
        )
        self.assertEqual(
            "Java 并发",
            knowledge_base._skill_from_question("synchronized 如何保证线程安全？", "通用"),
        )


class KnowledgeEmbeddingBatchTest(unittest.IsolatedAsyncioTestCase):
    async def test_batches_run_concurrently_and_preserve_input_order(self):
        class FakeEmbeddings:
            chunk_size = 2

            def __init__(self):
                self.active = 0
                self.max_active = 0

            async def aembed_documents(self, texts, chunk_size=None):
                self.active += 1
                self.max_active = max(self.max_active, self.active)
                try:
                    await asyncio.sleep(0.02)
                    return [[float(text)] for text in texts]
                finally:
                    self.active -= 1

        fake = FakeEmbeddings()
        original_embeddings = knowledge_base._embeddings
        original_concurrency = settings.rag_embedding_max_concurrency
        knowledge_base._embeddings = fake
        settings.rag_embedding_max_concurrency = 2
        try:
            vectors = await knowledge_base._embed_documents(
                ["0", "1", "2", "3", "4"]
            )
        finally:
            knowledge_base._embeddings = original_embeddings
            settings.rag_embedding_max_concurrency = original_concurrency

        self.assertEqual([[0.0], [1.0], [2.0], [3.0], [4.0]], vectors)
        self.assertEqual(2, fake.max_active)


if __name__ == "__main__":
    unittest.main()
