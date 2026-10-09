import unittest

from benchmarks.interview_quality_eval import evaluate_rows


class InterviewQualityEvaluationTest(unittest.TestCase):
    def test_metrics_detect_single_question_and_stage_improvements(self):
        rows = [
            {
                "stage": "fundamentals",
                "stage_question_number": 1,
                "expected_skill": "Redis",
                "expected_topic": "缓存穿透",
                "question": "Redis 中什么是缓存穿透？",
                "source_type": "RAG",
                "source_question_id": "question-1",
            },
            {
                "stage": "project",
                "stage_question_number": 4,
                "expected_skill": "Spring Boot",
                "expected_topic": "项目职责",
                "question": "你负责什么？还做了哪些模块？",
                "source_type": "MODEL_PROJECT",
            },
        ]
        metrics = evaluate_rows(rows)
        self.assertEqual(0.5, metrics["single_question_rate"])
        self.assertEqual(0.5, metrics["stage_violation_rate"])
        self.assertEqual(1.0, metrics["source_traceability_rate"])
