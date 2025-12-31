"""
models.py
ORM 模型定义：User（用户+VIP 信息）、Model（三维模型/glb 信息）。
"""

import datetime as dt
from sqlalchemy import (
    Column,
    Integer,
    String,
    Boolean,
    DateTime,
    ForeignKey,
    Index,
    text,
    func,
)
from sqlalchemy.orm import relationship

from .db import Base


class User(Base):
    """用户表（包含 VIP 信息与账号状态）。

    - username：登录名，唯一
    - password_hash：存储加密后的密码哈希
    - is_vip/vip_expire_time：VIP 状态与到期时间
    - status：账号状态，1=正常，0=禁用
    """

    __tablename__ = "users"
    __table_args__ = (
        Index("ux_users_username", "username", unique=True),
        Index("ix_users_is_vip", "is_vip"),
        Index("ix_users_status", "status"),
        {"mysql_engine": "InnoDB", "mysql_charset": "utf8mb4"},
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    username = Column(String(64), nullable=False)
    # password_hash：存储密码的哈希值，明文密码应在业务层先加密
    password_hash = Column(String(255), nullable=False)
    is_vip = Column(Boolean, nullable=False, server_default=text("0"))
    vip_expire_time = Column(DateTime, nullable=True)
    status = Column(Integer, nullable=False, server_default=text("1"))
    created_at = Column(DateTime, nullable=False, server_default=text("CURRENT_TIMESTAMP"))
    # updated_at：默认当前时间，更新记录时自动刷新
    updated_at = Column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
    )

    # 关联：用户拥有的模型列表
    models = relationship(
        "Model",
        back_populates="user",
        cascade="all, delete-orphan",
    )
    glb_files = relationship(
        "GlbFile",
        back_populates="user",
        cascade="all, delete-orphan",
    )


class Model(Base):
    """三维模型（glb）表。

    - file_path：glb 文件路径或 URL
    - preview_image：封面图片，可选
    - visibility：0=私有，1=公开
    """

    __tablename__ = "models"
    __table_args__ = (
        Index("ix_models_user_id", "user_id"),
        {"mysql_engine": "InnoDB", "mysql_charset": "utf8mb4"},
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    name = Column(String(128), nullable=False)
    file_path = Column(String(512), nullable=False)
    preview_image = Column(String(512), nullable=True)
    visibility = Column(Integer, nullable=False, server_default=text("0"))
    created_at = Column(DateTime, nullable=False, server_default=text("CURRENT_TIMESTAMP"))
    updated_at = Column(
        DateTime,
        nullable=False,
        server_default=text("CURRENT_TIMESTAMP"),
        server_onupdate=text("CURRENT_TIMESTAMP"),
    )

    # 关联：归属用户
    user = relationship("User", back_populates="models")


class GlbFile(Base):
    """GLB 文件元数据表。"""

    __tablename__ = "glb_files"
    __table_args__ = (
        Index("ux_glb_files_hashed_name", "hashed_name", unique=True),
        Index("ix_glb_files_user_id", "user_id"),
        {"mysql_engine": "InnoDB", "mysql_charset": "utf8mb4"},
    )

    id = Column(Integer, primary_key=True, autoincrement=True)
    hashed_name = Column(String(64), nullable=False)
    original_name = Column(String(256), nullable=False)
    file_path = Column(String(512), nullable=False)
    user_id = Column(Integer, ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    file_size = Column(Integer, nullable=False)
    created_at = Column(DateTime, nullable=False, server_default=func.now())
    is_public = Column(Boolean, nullable=False, server_default=text("0"))

    user = relationship("User", back_populates="glb_files")
