"""
database/db.py
SQLite-backed SQLAlchemy session/engine/base setup for the VGGT app.
使用项目根目录下的 SQLite 本地数据库文件：vggt_app.db
"""

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

# 使用 SQLite 本地数据库，无需额外安装数据库服务器
DATABASE_URL = "sqlite:///./vggt_app.db"

# 创建 Engine（SQLite 需要 check_same_thread=False 以允许多线程场景下的同连接使用）
engine = create_engine(
    DATABASE_URL,
    connect_args={"check_same_thread": False},
    echo=False,
    future=True,
)

# Session 工厂
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine, future=True)

# Declarative Base
Base = declarative_base()


def get_db():
    """FastAPI 依赖：提供一个 SQLAlchemy Session，并在请求结束时关闭。"""
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
