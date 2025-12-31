"""
schemas.py
Pydantic 数据模型：用户与三维模型的基础请求/响应结构。
"""

import datetime as dt
from typing import Optional, List
from pydantic import BaseModel, Field


# -------------------- User --------------------

class UserCreate(BaseModel):
    """创建用户时使用的请求体。
    注意：password_hash 应为业务层加密后的哈希字符串。
    """

    username: str = Field(..., min_length=3, max_length=64)
    password_hash: str = Field(..., min_length=6, max_length=255)
    is_vip: bool = Field(default=False)
    vip_expire_time: Optional[dt.datetime] = None


class UserRead(BaseModel):
    """返回给前端的用户信息，不包含 password_hash。"""

    id: int
    username: str
    is_vip: bool
    vip_expire_time: Optional[dt.datetime] = None
    status: int
    created_at: dt.datetime
    updated_at: dt.datetime

    class Config:
        orm_mode = True


# -------------------- Model (GLB) --------------------

class ModelCreate(BaseModel):
    """创建三维模型记录时使用的请求体。"""

    user_id: int
    name: str = Field(..., min_length=1, max_length=128)
    file_path: str = Field(..., min_length=1, max_length=512)
    preview_image: Optional[str] = Field(default=None, max_length=512)
    visibility: int = Field(default=0, ge=0, le=1, description="0=私有, 1=公开")


class ModelRead(BaseModel):
    """返回三维模型信息。"""

    id: int
    user_id: int
    name: str
    file_path: str
    preview_image: Optional[str] = None
    visibility: int
    created_at: dt.datetime
    updated_at: dt.datetime

    class Config:
        orm_mode = True


# -------------------- GLB Files --------------------

class GlbFileCreate(BaseModel):
    """创建 GLB 文件记录时使用的内部结构。"""

    user_id: int
    original_name: str = Field(..., min_length=1, max_length=256)
    hashed_name: str = Field(..., min_length=64, max_length=64)
    file_path: str = Field(..., min_length=1, max_length=512)
    file_size: int = Field(..., ge=0)
    created_at: dt.datetime
    is_public: bool = Field(default=False)


class GlbFileRead(BaseModel):
    """返回 GLB 文件元数据。"""

    id: int
    user_id: int
    original_name: str
    hashed_name: str
    file_path: str
    file_size: int
    created_at: dt.datetime
    is_public: bool

    class Config:
        orm_mode = True
