import asyncio
import json
import unittest
from unittest.mock import patch

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage
from pydantic import ValidationError

from app import llm_client
from app.llm_client import (
    StructuredOutputValidationError,
    ainvoke_structured,
    invoke_structured,
)
from app.schemas import (
    DimensionEvaluation,
    EvaluationResponse,
    ResumeDiagnosisResponse,
    SkillScore,
)


class NativeStructuredRunnable:
    def __init__(self, result):
        self.result = result

    def invoke(self, _messages):
        return self.result

    async def ainvoke(self, _messages):
        return self.result


class NativeStructuredModel:
    def __init__(self, parsed, raw_content="provider-specific raw response"):
        self.result = {
            "raw": AIMessage(content=raw_content),
            "parsed": parsed,
            "parsing_error": None,
        }
        self.structured_calls = []
        self.raw_calls = 0

    def with_structured_output(self, schema, **kwargs):
        self.structured_calls.append((schema, kwargs))
        return NativeStructuredRunnable(self.result)

    def invoke(self, _messages):
        self.raw_calls += 1
        raise AssertionError("Native structured output should avoid a raw retry")


class PlainJsonModel:
    def __init__(self, content):
        self.content = content

    def with_structured_output(self, _schema, **_kwargs):
        raise NotImplementedError

    def invoke(self, _messages):
        return AIMessage(content=self.content)

    async def ainvoke(self, _messages):
        return AIMessage(content=self.content)


class StructuredCapabilityError(RuntimeError):
    status_code = 400


class StructuredRequestErrorModel(PlainJsonModel):
    def with_structured_output(self, _schema, **_kwargs):
        class FailingRunnable:
            def invoke(self, _messages):
                raise StructuredCapabilityError("structured output unsupported")

        return FailingRunnable()


class VisionJsonModel:
    def __init__(self, content):
        self.content = content
        self.raw_calls = 0
        self.structured_calls = []
        self.last_messages = None

    def with_structured_output(self, schema, **kwargs):
        self.structured_calls.append((schema, kwargs))
        model = self

        class VisionStructuredRunnable:
            def invoke(self, messages):
                model.last_messages = messages
                return {
                    "raw": AIMessage(content=model.content),
                    "parsed": None,
                    "parsing_error": None,
                }

            async def ainvoke(self, messages):
                return self.invoke(messages)

        return VisionStructuredRunnable()

    def invoke(self, _messages):
        self.raw_calls += 1
        return AIMessage(content=self.content)

    async def ainvoke(self, messages):
        return self.invoke(messages)


class StructuredOutputContractTest(unittest.TestCase):
    def test_native_schema_succeeds_where_legacy_text_parser_fails(self):
        raw_text = "评分已完成，但这里不是可直接解析的 JSON。"
        expected = EvaluationResponse(
            score=86,
            evaluation="项目证据充分，系统设计仍可加强。",
            skills=[
                SkillScore(
                    skillName="Java",
                    score=4.2,
                    comment="能够结合项目解释并发控制。",
                )
            ],
        )
        model = NativeStructuredModel(expected, raw_content=raw_text)

        with self.assertRaises(ValidationError):
            EvaluationResponse.model_validate_json(
                llm_client._strip_markdown_json(raw_text)
            )

        actual = invoke_structured(
            model,
            EvaluationResponse,
            [HumanMessage(content="请评分")],
        )

        self.assertEqual(expected, actual)
        self.assertEqual(0, model.raw_calls)
        schema, options = model.structured_calls[0]
        self.assertIs(EvaluationResponse, schema)
        self.assertEqual("function_calling", options["method"])
        self.assertTrue(options["include_raw"])

    def test_plain_json_fallback_preserves_legacy_provider_compatibility(self):
        model = PlainJsonModel(
            """```json
            {
              "score": 78,
              "evaluation": "基础较好。",
              "skills": [
                {"skillName": "Redis", "score": 3.5, "comment": "缓存实践充分。"}
              ]
            }
            ```"""
        )

        result = invoke_structured(
            model,
            EvaluationResponse,
            [HumanMessage(content="请评分")],
        )

        self.assertEqual(78, result.score)
        self.assertEqual("Redis", result.skills[0].skillName)

    def test_provider_capability_error_falls_back_to_plain_json(self):
        model = StructuredRequestErrorModel(
            '{"score": 82, "evaluation": "结构清晰。", "skills": []}'
        )

        result = invoke_structured(
            model,
            EvaluationResponse,
            [HumanMessage(content="请评分")],
        )

        self.assertEqual(82, result.score)

    def test_invalid_fallback_output_is_rejected_by_schema(self):
        model = PlainJsonModel(
            '{"score": 120, "evaluation": "越界分数", "skills": []}'
        )

        with self.assertRaises(StructuredOutputValidationError):
            invoke_structured(
                model,
                EvaluationResponse,
                [HumanMessage(content="请评分")],
            )

    def test_resume_diagnosis_keeps_existing_json_string_contract(self):
        diagnosis = ResumeDiagnosisResponse(
            advantages=["项目经历具体"],
            disadvantages=["缺少量化指标"],
            suggestions=["补充性能提升数据"],
            extracted_text="",
        )
        model = NativeStructuredModel(diagnosis)

        with patch.object(llm_client, "_model", return_value=model):
            raw = asyncio.run(llm_client.resume_diagnosis(
                parsed_text="Java 后端项目",
                image_base64=None,
                image_mime_type="image/png",
            ))

        self.assertEqual(diagnosis.model_dump(), json.loads(raw))

    def test_vision_diagnosis_uses_json_mode_and_parses_once(self):
        model = VisionJsonModel(
            "分析结果如下：\n"
            '{"advantages":["结构清晰"],"disadvantages":[],"suggestions":[],"extracted_text":""}'
        )

        with patch.object(llm_client, "_model", return_value=model):
            raw = asyncio.run(llm_client.resume_diagnosis(
                parsed_text="Java 后端简历",
                image_base64="synthetic-image",
                image_mime_type="image/png",
            ))

        self.assertEqual(["结构清晰"], json.loads(raw)["advantages"])
        self.assertEqual(0, model.raw_calls)
        schema, options = model.structured_calls[0]
        self.assertIs(ResumeDiagnosisResponse, schema)
        self.assertEqual("json_mode", options["method"])
        self.assertTrue(options["include_raw"])
        self.assertIsInstance(model.last_messages[0], SystemMessage)
        self.assertIsInstance(model.last_messages[1], HumanMessage)
        self.assertIn("待分析数据", model.last_messages[0].content)
        self.assertIn("各输出 2 至 6 条", model.last_messages[1].content[0]["text"])
        self.assertIn("<pdf_extracted_text>", model.last_messages[1].content[0]["text"])

    def test_vision_diagnosis_returns_valid_contract_for_non_json_output(self):
        model = VisionJsonModel("当前模型暂时只返回了普通诊断文本。")

        with patch.object(llm_client, "_model", return_value=model):
            raw = asyncio.run(llm_client.resume_diagnosis(
                parsed_text="Java 后端简历",
                image_base64="synthetic-image",
                image_mime_type="image/png",
            ))

        result = ResumeDiagnosisResponse.model_validate_json(raw)
        self.assertEqual(1, model.raw_calls)
        self.assertEqual(1, len(model.structured_calls))
        self.assertTrue(result.disadvantages)
        self.assertIn("重新发起简历诊断", result.suggestions[0])


class AsyncStructuredOutputContractTest(unittest.IsolatedAsyncioTestCase):
    async def test_dimension_schema_supports_async_worker_calls(self):
        expected = DimensionEvaluation(
            score=4.3,
            comment="回答包含明确的事务边界和异常处理证据。",
        )
        model = NativeStructuredModel(expected)

        result = await ainvoke_structured(
            model,
            DimensionEvaluation,
            [HumanMessage(content="评价事务能力")],
        )

        self.assertEqual(expected, result)

    async def test_async_fallback_parses_fenced_json(self):
        model = PlainJsonModel(
            '```json\n{"score": 3.8, "comment": "能够解释缓存一致性。"}\n```'
        )

        result = await ainvoke_structured(
            model,
            DimensionEvaluation,
            [HumanMessage(content="评价缓存能力")],
        )

        self.assertEqual(3.8, result.score)


if __name__ == "__main__":
    unittest.main()
