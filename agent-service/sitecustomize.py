import asyncio
import sys


# psycopg async connections require a selector event loop on Windows. Python
# imports sitecustomize before Uvicorn or LangGraph creates the application loop.
if sys.platform == "win32":
    asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
