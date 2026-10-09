"""Verify PostgreSQL schemas, checkpointer setup, and graph compilation."""

import asyncio
import selectors
import sys

from app.interview_agent import shutdown, startup
from app.knowledge_base import retrieval_stats


async def main() -> None:
    await startup()
    stats = await retrieval_stats()
    print(
        "agent startup ok; "
        f"indexed_questions={stats['indexed_questions']}; "
        f"question_queries={stats['question_queries']}; "
        f"selected_question_hits={stats['selected_question_hits']}"
    )
    await shutdown()


if __name__ == "__main__":
    loop_factory = None
    if sys.platform == "win32":
        loop_factory = lambda: asyncio.SelectorEventLoop(selectors.SelectSelector())
    with asyncio.Runner(loop_factory=loop_factory) as runner:
        runner.run(main())
