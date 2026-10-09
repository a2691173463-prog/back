import unittest
from contextlib import nullcontext
from types import SimpleNamespace
from unittest.mock import patch

from langchain_core.messages import AIMessage, AIMessageChunk

from app import interview_agent
from app.graph import _astream_plain_question


class _StructuredQuestionGraph:
    async def astream(self, *_args, **kwargs):
        assert kwargs["stream_mode"] == ["messages", "custom", "updates"]
        yield "custom", {"type": "token", "content": "请说明 Redis "}
        yield "custom", {"type": "token", "content": "缓存穿透的原因和解决方案？"}
        yield "updates", {
            "interviewer": {
                "messages": [AIMessage(content="请说明 Redis 缓存穿透的原因和解决方案？")]
            }
        }

    async def aget_state(self, _config):
        return SimpleNamespace(
            values={
                "interview_stage": "fundamentals",
                "question_count": 4,
                "current_skill": "Redis",
            }
        )


class _TransitionQuestionGraph:
    async def astream(self, *_args, **kwargs):
        assert kwargs["stream_mode"] == ["messages", "custom", "updates"]
        prefix = "项目经历部分先了解到这里。\n\n第一题："
        yield "custom", {"type": "token", "content": prefix}
        yield "updates", {
            "interviewer": {
                "messages": [AIMessage(content=prefix + "JVM 和 Java 有什么区别？")]
            }
        }

    async def aget_state(self, _config):
        return SimpleNamespace(
            values={
                "interview_stage": "fundamentals",
                "question_count": 4,
                "current_skill": "Java",
            }
        )


class InterviewStreamTests(unittest.IsolatedAsyncioTestCase):
    async def test_plain_question_stream_removes_preface_and_stops_after_one_question(self):
        class PlainModel:
            async def astream(self, _messages):
                yield AIMessageChunk(content="好的，")
                yield AIMessageChunk(content="请说明 Redis 缓存穿透的原因？")
                yield AIMessageChunk(content="你还知道哪些方案？")

        streamed = []
        runtime = SimpleNamespace(stream_writer=streamed.append)
        result = await _astream_plain_question(
            PlainModel(),
            [],
            runtime,
        )

        self.assertEqual("请说明 Redis 缓存穿透的原因？", result)
        self.assertEqual(
            "请说明 Redis 缓存穿透的原因？",
            "".join(event["content"] for event in streamed),
        )

    async def test_streams_validated_question_from_node_update(self):
        graph = _StructuredQuestionGraph()
        with (
            patch.object(interview_agent, "interview_graph", graph),
            patch.object(interview_agent, "trace_interview", return_value=nullcontext()),
        ):
            events = [
                event
                async for event in interview_agent.stream_reply(
                    "thread-1", 1, 2, "standard", "JAVA_BACKEND", "准备好了"
                )
            ]

        tokens = [event["content"] for event in events if event["type"] == "token"]
        self.assertEqual(2, len(tokens))
        self.assertEqual(
            "".join(tokens),
            "请说明 Redis 缓存穿透的原因和解决方案？",
        )
        self.assertEqual(events[-1]["type"], "state")

    async def test_transition_prefix_does_not_hide_question_suffix(self):
        graph = _TransitionQuestionGraph()
        with (
            patch.object(interview_agent, "interview_graph", graph),
            patch.object(interview_agent, "trace_interview", return_value=nullcontext()),
        ):
            events = [
                event
                async for event in interview_agent.stream_reply(
                    "thread-2", 1, 2, "standard", "JAVA_BACKEND", "好的"
                )
            ]

        content = "".join(
            event["content"] for event in events if event["type"] == "token"
        )
        self.assertEqual(
            "项目经历部分先了解到这里。\n\n第一题：JVM 和 Java 有什么区别？",
            content,
        )
        self.assertEqual(1, content.count("项目经历部分先了解到这里"))


if __name__ == "__main__":
    unittest.main()
