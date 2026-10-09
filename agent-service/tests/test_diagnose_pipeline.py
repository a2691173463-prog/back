import asyncio
import os
import sys
import time

# Ensure UTF-8 stdout on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.config import settings
from app.llm_client import resume_diagnosis


async def profile_diagnosis():
    print("=" * 60)
    print("开始简历诊断流水线分段性能排查...")
    print(f"当前 MODEL_NAME: {settings.model_name}")
    print(f"当前 VISION_MODEL_NAME: {settings.vision_model_name}")
    print("=" * 60)

    sample_text = (
        "姓名：张三  求职岗位：Java 后端开发工程师\n"
        "专业技能：熟悉 Java 集合、JVM 内存模型、MySQL 事务隔离级别、Redis 缓存与 RabbitMQ 消息队列。\n"
        "项目经历：参与校招 AI 模拟面试系统研发，负责基于 RabbitMQ 的高可用简历解析流水线构建，"
        "引入 Outbox 模式与两级延迟重试队列，吞吐量提升 135%。"
    )

    # 1. 测试纯文本诊断（不带图片）耗时
    print("\n[测试 1] 发起【纯文本】简历诊断...")
    t0 = time.time()
    try:
        res_text = resume_diagnosis(parsed_text=sample_text, image_base64=None, image_mime_type="image/png")
        t1 = time.time()
        print(f"✅ 纯文本诊断成功！耗时: {t1 - t0:.2f} 秒")
        print(f"返回结果预览: {res_text[:150]}...")
    except Exception as e:
        print(f"❌ 纯文本诊断失败: {e}")

    # 2. 测试带简易 1x1 像素占位图片的 Vision 诊断耗时
    # 1x1 transparent PNG Base64
    tiny_image_base64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    print("\n[测试 2] 发起【带图片 (Vision 模型)】简历诊断...")
    t2 = time.time()
    try:
        res_vision = resume_diagnosis(
            parsed_text=sample_text,
            image_base64=tiny_image_base64,
            image_mime_type="image/png",
        )
        t3 = time.time()
        print(f"✅ 带图 Vision 诊断成功！耗时: {t3 - t2:.2f} 秒")
        print(f"返回结果预览: {res_vision[:150]}...")
    except Exception as e:
        print(f"❌ 带图 Vision 诊断失败: {e}")

    print("\n" + "=" * 60)
    print("排查建议结论：")
    print("1. 如果【纯文本】快、而【带图 Vision】慢或卡住：说明瓶颈在大模型处理 Base64 图像传输与 Vision 编码上。")
    print("2. 如果两者都慢：说明大模型 API 供应商网络或模型本身推理响应较慢。")
    print("=" * 60)

if __name__ == "__main__":
    asyncio.run(profile_diagnosis())
