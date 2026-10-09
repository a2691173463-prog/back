import unittest
from unittest.mock import patch

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app import llm_client


class SinglePassResumeModel:
    def __init__(self, content: str):
        self.content = content
        self.calls = []

    def invoke(self, messages):
        self.calls.append(messages)
        return AIMessage(content=self.content)

    def with_structured_output(self, *_args, **_kwargs):
        raise AssertionError("Single-pass resume optimization must not call an evaluator")


class ResumePromptOptimizationTest(unittest.TestCase):
    def test_project_optimization_uses_one_model_call_and_strict_fact_prompt(self):
        model = SinglePassResumeModel(
            "- 负责订单模块开发，使用 Redis 处理缓存。"
        )

        with patch.object(llm_client, "_model", return_value=model):
            result = llm_client.optimize_resume_section(
                "项目经历",
                "Java 后端开发工程师",
                "负责订单模块开发，使用 Redis 处理缓存。",
            )

        self.assertEqual("- 负责订单模块开发，使用 Redis 处理缓存。", result)
        self.assertEqual(1, len(model.calls))
        self.assertIsInstance(model.calls[0][0], SystemMessage)
        self.assertIsInstance(model.calls[0][1], HumanMessage)
        prompt = model.calls[0][1].content
        self.assertIn("原始内容是唯一事实来源", prompt)
        self.assertIn("不得编造百分比", prompt)
        self.assertIn("不能扩写为“提升响应速度”", prompt)
        self.assertIn("“参与”不能改成“负责”", prompt)
        self.assertIn("Java 后端开发工程师", prompt)
        self.assertIn("优先整理为 3 至 5 条", prompt)

    def test_personal_summary_uses_section_specific_guidance(self):
        model = SinglePassResumeModel("具备 Java 与 Redis 项目实践。")

        with patch.object(llm_client, "_model", return_value=model):
            llm_client.optimize_resume_section(
                "个人优势",
                "后端开发工程师",
                "会 Java 和 Redis。",
            )

        prompt = model.calls[0][1].content
        self.assertIn("2 至 4 句紧凑陈述", prompt)
        self.assertIn("删除空泛的自我评价", prompt)

    def test_fenced_output_is_cleaned_without_a_second_call(self):
        model = SinglePassResumeModel("```text\n负责接口开发与异常处理。\n```")

        with patch.object(llm_client, "_model", return_value=model):
            result = llm_client.optimize_resume_section(
                "实习经历",
                "Java 后端开发工程师",
                "参与接口开发与异常处理。",
            )

        self.assertEqual("负责接口开发与异常处理。", result)
        self.assertEqual(1, len(model.calls))

    def test_empty_model_output_preserves_original_content(self):
        model = SinglePassResumeModel("")
        original = "参与订单模块开发。"

        with patch.object(llm_client, "_model", return_value=model):
            result = llm_client.optimize_resume_section(
                "项目经历",
                "Java 后端开发工程师",
                original,
            )

        self.assertEqual(original, result)
        self.assertEqual(1, len(model.calls))


if __name__ == "__main__":
    unittest.main()
