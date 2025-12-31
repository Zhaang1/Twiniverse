"""
login_test.py
简单的用户登录测试脚本：
- 从命令行参数或交互输入获取 username、password
- 使用当前项目的 SQLite 数据库（database/db.py -> SessionLocal）查询 users 表
- 测试阶段：假设 users.password_hash 字段保存的是明文密码

用法示例：
    python database/login_test.py alice 123456
若省略参数则交互输入。
"""

import sys
from typing import Optional
from pathlib import Path

# 确保可作为脚本直接运行（将项目根目录加入 sys.path）
ROOT_DIR = Path(__file__).resolve().parents[1]
if str(ROOT_DIR) not in sys.path:
    sys.path.insert(0, str(ROOT_DIR))

from database.db import SessionLocal
from database.models import User


def get_credentials_from_argv_or_input() -> tuple[str, str]:
    """优先从命令行读取用户名和密码，否则使用交互输入。"""
    if len(sys.argv) >= 3:
        return sys.argv[1], sys.argv[2]
    username = input("请输入用户名: ").strip()
    password = input("请输入密码: ").strip()
    return username, password


def login(username: str, password: str) -> None:
    """执行登录验证并打印中文结果。"""
    db = SessionLocal()
    try:
        user: Optional[User] = db.query(User).filter(User.username == username).first()
        if not user:
            print("用户不存在")
            return

        # 测试阶段：password_hash 字段保存的是明文密码
        if user.password_hash != password:
            print("密码错误")
            return

        print("登录成功")
        if bool(user.is_vip):
            print("该用户为 VIP 会员")
        else:
            print("该用户为普通用户")
    finally:
        db.close()


if __name__ == "__main__":
    uname, pwd = get_credentials_from_argv_or_input()
    login(uname, pwd)
