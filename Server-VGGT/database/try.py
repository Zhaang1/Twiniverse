"""
try.py
最小可运行 FastAPI 示例：
- SQLite 本地数据库（vggt_app.db） + SQLAlchemy（Declarative Base）
- 仅包含：用户/模型的基础增查接口
- 开发阶段在启动时自动建表（生产环境请使用迁移工具）

运行前依赖：
    pip install fastapi "uvicorn[standard]" sqlalchemy
无需安装 MySQL / PyMySQL。
"""

from typing import List
from fastapi import FastAPI, Depends, HTTPException, status
from sqlalchemy.orm import Session

from .db import Base, engine, get_db
from .models import User, Model
from .schemas import UserCreate, UserRead, ModelCreate, ModelRead


app = FastAPI(title="VGGT Minimal API", version="0.1.0")


@app.on_event("startup")
def on_startup() -> None:
    """启动时创建数据表（开发场景）。
    生产环境请使用 Alembic 等迁移工具管理表结构变更。
    """
    Base.metadata.create_all(bind=engine)


@app.post("/users/", response_model=UserRead, status_code=status.HTTP_201_CREATED)
def create_user(payload: UserCreate, db: Session = Depends(get_db)) -> UserRead:
    """创建用户
    - 假设 payload.password_hash 已经是加密后的哈希值（业务层负责加密）。
    - username 必须唯一，若重复则返回 409。
    """
    exists = db.query(User).filter(User.username == payload.username).first()
    if exists:
        raise HTTPException(status_code=409, detail="username already exists")

    user = User(
        username=payload.username,
        password_hash=payload.password_hash,
        is_vip=payload.is_vip,
        vip_expire_time=payload.vip_expire_time,
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return user  # Pydantic orm_mode 会自动转换


@app.get("/users/{user_id}", response_model=UserRead)
def get_user(user_id: int, db: Session = Depends(get_db)) -> UserRead:
    """查询用户信息（不包含密码哈希）。"""
    user = db.get(User, user_id)
    if not user:
        raise HTTPException(status_code=404, detail="user not found")
    return user


@app.post("/models/", response_model=ModelRead, status_code=status.HTTP_201_CREATED)
def create_model(payload: ModelCreate, db: Session = Depends(get_db)) -> ModelRead:
    """创建三维模型记录
    - 至少包含 user_id, name, file_path
    - 该接口仅写入模型元数据，不处理实际文件上传
    """
    # 可在此处校验用户是否存在
    user = db.get(User, payload.user_id)
    if not user:
        raise HTTPException(status_code=404, detail="user not found")

    model = Model(
        user_id=payload.user_id,
        name=payload.name,
        file_path=payload.file_path,
        preview_image=payload.preview_image,
        visibility=payload.visibility,
    )
    db.add(model)
    db.commit()
    db.refresh(model)
    return model


@app.get("/users/{user_id}/models", response_model=List[ModelRead])
def list_user_models(user_id: int, db: Session = Depends(get_db)) -> List[ModelRead]:
    """查询该用户的所有三维模型，按创建时间降序。"""
    # 同时确认用户是否存在（可选）
    if not db.get(User, user_id):
        raise HTTPException(status_code=404, detail="user not found")

    models = (
        db.query(Model)
        .filter(Model.user_id == user_id)
        .order_by(Model.created_at.desc())
        .all()
    )
    return models


# 运行：uvicorn try:app --reload --host 0.0.0.0 --port 8000
