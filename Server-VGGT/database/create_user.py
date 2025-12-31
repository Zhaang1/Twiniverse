"""
create_user.py
从命令行参数创建用户记录：
    python database/create_user.py <username> <password> [is_vip]

说明：
- 测试阶段：password_hash 直接保存明文密码
- is_vip 解析："true/1/yes/y" 视为 True，其它为 False（大小写不敏感）
"""

import sys
from typing import Optional
from pathlib import Path

# 确保可作为脚本直接运行（将项目根目录加入 sys.path）
ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from database.db import SessionLocal, engine, Base
from database.models import User


def parse_bool(value: Optional[str]) -> bool:
    if value is None:
        return False
    return value.strip().lower() in {"true", "1", "yes", "y", "t"}


def main() -> None:
    if len(sys.argv) < 3:
        print("用法: python create_user.py <username> <password> [is_vip]")
        sys.exit(1)

    username = sys.argv[1]
    password = sys.argv[2]
    is_vip = parse_bool(sys.argv[3]) if len(sys.argv) >= 4 else False

    # 开发场景：确保表存在（若已存在则忽略）
    try:
        Base.metadata.create_all(bind=engine)
    except Exception:
        pass

    db = SessionLocal()
    try:
        exists = db.query(User).filter(User.username == username).first()
        if exists:
            print("用户名已存在")
            return

        user = User(
            username=username,
            password_hash=password,  # 测试阶段直接保存明文
            is_vip=is_vip,
        )
        db.add(user)
        db.commit()
        db.refresh(user)
        print(f"id={user.id}, username={user.username}, is_vip={bool(user.is_vip)}")
    finally:
        db.close()


if __name__ == "__main__":
    main()
