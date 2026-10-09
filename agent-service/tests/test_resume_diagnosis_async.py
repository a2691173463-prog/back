import asyncio
import json
import unittest
from unittest.mock import patch

from langchain_core.messages import AIMessage

from app import llm_client


DIAGNOSIS = {
    "advantages": ["项目职责明确"],
    "disadvantages": [],
    "suggestions": [],
    "extracted_text": "",
}


class ResumeDiagnosisAsyncTest(unittest.IsolatedAsyncioTestCase):
    async def test_text_diagnoses_run_concurrently_with_async_model_calls(self):
        class FakeModel:
            def __init__(self):
                self.active = 0
                self.max_active = 0

            def with_structured_output(self, schema, method, include_raw):
                self.method = method
                return self

            async def ainvoke(self, messages):
                self.active += 1
                self.max_active = max(self.max_active, self.active)
                try:
                    await asyncio.sleep(0.03)
                    return {"parsed": DIAGNOSIS}
                finally:
                    self.active -= 1

            def invoke(self, messages):
                raise AssertionError("Synchronous model invocation is forbidden")

        model = FakeModel()
        with patch.object(llm_client, "_model", return_value=model):
            results = await asyncio.gather(*(
                llm_client.resume_diagnosis("Java 项目经历", None, "image/png")
                for _ in range(3)
            ))

        self.assertEqual(3, model.max_active)
        self.assertEqual("function_calling", model.method)
        self.assertTrue(all(json.loads(result) == DIAGNOSIS for result in results))

    async def test_vision_diagnosis_falls_back_to_async_plain_call(self):
        class FakeStructuredModel:
            async def ainvoke(self, messages):
                raise ValueError("structured output unavailable")

        class FakeModel:
            def with_structured_output(self, schema, method, include_raw):
                self.method = method
                return FakeStructuredModel()

            async def ainvoke(self, messages):
                return AIMessage(content=json.dumps(DIAGNOSIS, ensure_ascii=False))

            def invoke(self, messages):
                raise AssertionError("Synchronous model invocation is forbidden")

        model = FakeModel()
        with patch.object(llm_client, "_model", return_value=model):
            result = await llm_client.resume_diagnosis("", "aGVsbG8=", "image/png")

        self.assertEqual("json_mode", model.method)
        self.assertEqual(DIAGNOSIS, json.loads(result))


if __name__ == "__main__":
    unittest.main()
