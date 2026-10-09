import unittest

from app import knowledge_base


class KnowledgeBaseEmbeddingTest(unittest.TestCase):
    def tearDown(self) -> None:
        knowledge_base._embeddings = None

    def test_dashscope_embedding_client_sends_small_raw_text_batches(self) -> None:
        client = knowledge_base._embedding_client()

        self.assertFalse(client.check_embedding_ctx_length)
        self.assertEqual(10, client.chunk_size)

    def test_scope_and_position_values_are_normalized(self) -> None:
        self.assertEqual("PRIVATE", knowledge_base._normalize_scope("private"))
        self.assertEqual("AI_AGENT", knowledge_base._normalize_position("ai_agent"))
        with self.assertRaises(ValueError):
            knowledge_base._normalize_scope("unknown")

    def test_access_filter_combines_public_position_and_private_sources(self) -> None:
        clause, params = knowledge_base._access_clause("Redis")

        self.assertIn("d.scope = 'GLOBAL'", clause)
        self.assertIn("d.scope = 'POSITION'", clause)
        self.assertIn("d.scope = 'PRIVATE'", clause)
        self.assertEqual(["Redis"], params)

    def test_question_retrieval_prefers_exact_skill_before_java_family(self) -> None:
        levels = knowledge_base._question_retrieval_levels(
            "d.status = 'ACTIVE'",
            [],
            "Java",
            "basic",
        )

        self.assertEqual(
            [
                "exact-skill+difficulty",
                "exact-skill",
                "skill-family+difficulty",
                "skill-family",
                "difficulty",
                "broad",
            ],
            [level_name for _, _, level_name in levels],
        )
        self.assertEqual(["Java", "basic"], levels[0][1])
        self.assertEqual("JVM", levels[2][1][-2])

    def test_topic_overlap_handles_normalized_containment(self) -> None:
        self.assertTrue(
            knowledge_base._topics_overlap("JVM 类加载过程", "类加载过程")
        )
        self.assertFalse(
            knowledge_base._topics_overlap("类加载过程", "垃圾回收器 CMS 和 G1")
        )


if __name__ == "__main__":
    unittest.main()
