import unittest
from unittest.mock import patch

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app import llm_client


class CapturingModel:
    def __init__(self, content: str):
        self.content = content
        self.calls = []

    def invoke(self, messages):
        self.calls.append(messages)
        return AIMessage(content=self.content)


class PromptContractTest(unittest.TestCase):
    def test_interview_summary_preserves_role_and_evidence_boundaries(self):
        model = CapturingModel("候选人未能说明 Redis 缓存一致性的处理方式。")

        with patch.object(llm_client, "_model", return_value=model):
            result = llm_client.summarize_interview(
                "面试官：Redis 如何保证一致性？\n候选人：不太清楚。",
                None,
                150,
            )

        self.assertEqual("候选人未能说明 Redis 缓存一致性的处理方式。", result)
        self.assertEqual(1, len(model.calls))
        messages = model.calls[0]
        self.assertIsInstance(messages[0], SystemMessage)
        self.assertIsInstance(messages[1], HumanMessage)
        self.assertIn("面试官问过某项技术不代表候选人掌握", messages[0].content)
        self.assertIn("不得把面试官提出的知识点", messages[1].content)
        self.assertIn("<interview_transcript>", messages[1].content)
        self.assertIn("候选人：不太清楚", messages[1].content)

    def test_incremental_summary_treats_existing_memory_and_transcript_as_data(self):
        model = CapturingModel("候选人能解释缓存穿透，但尚未说明一致性方案。")

        with patch.object(llm_client, "_model", return_value=model):
            llm_client.summarize_interview(
                "候选人：缓存穿透可以使用布隆过滤器。",
                "当前正在考察 Redis。",
                150,
            )

        prompt = model.calls[0][1].content
        self.assertIn("<existing_memory>", prompt)
        self.assertIn("<new_transcript>", prompt)
        self.assertIn("只有候选人的实际回答能够证明能力", prompt)


if __name__ == "__main__":
    unittest.main()
