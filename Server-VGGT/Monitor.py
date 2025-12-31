import os
import sys
import time
import threading
import psutil
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
import platform
import numpy as np


# --- 1. 环境与字体配置 ---

def configure_chinese_font():
    """配置中文字体，防止绘图乱码"""
    system = platform.system()
    font_path = ""

    # 常见中文字体路径
    if system == "Windows":
        font_path = "C:/Windows/Fonts/simsun.ttc"  # 宋体
    elif system == "Darwin":  # macOS
        font_path = "/System/Library/Fonts/Supplemental/Songti.ttc"
    else:  # Linux
        paths = [
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc"
        ]
        for p in paths:
            if os.path.exists(p):
                font_path = p
                break

    if font_path and os.path.exists(font_path):
        prop = fm.FontProperties(fname=font_path)
        plt.rcParams['font.sans-serif'] = [prop.get_name()]
        plt.rcParams['font.family'] = prop.get_name()
    else:
        plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']  # 备选

    plt.rcParams['axes.unicode_minus'] = False


configure_chinese_font()

# --- 2. GPU 监控库检测与初始化 ---
NVIDIA_AVAILABLE = False
GPU_NAME = "未知/无NVIDIA显卡"
try:
    import pynvml

    pynvml.nvmlInit()
    device_count = pynvml.nvmlDeviceGetCount()
    if device_count > 0:
        NVIDIA_AVAILABLE = True
        # 默认获取第0号显卡信息
        handle = pynvml.nvmlDeviceGetHandleByIndex(0)
        GPU_NAME = pynvml.nvmlDeviceGetName(handle)
        # pynvml.nvmlDeviceGetName 在不同版本返回值可能是 bytes 或 str
        if isinstance(GPU_NAME, bytes):
            GPU_NAME = GPU_NAME.decode("utf-8")
except Exception as e:
    print(f"[*] GPU 监控初始化失败 (非NVIDIA显卡或未安装pynvml): {e}")


# --- 3. 数据采集功能 ---

def get_resources():
    """
    获取当前的系统资源数据
    返回: CPU(%), GPU(%), VMem(GB), Mem(GB)
    """
    # 1. CPU (非阻塞，依赖初始化)
    cpu = psutil.cpu_percent(interval=None)

    # 2. 内存 (换算为 GB)
    mem_info = psutil.virtual_memory()
    # mem_info.used 是字节数
    mem_gb = mem_info.used / (1024 ** 3)

    # 3. GPU & 显存 (仅限 NVIDIA)
    gpu_util = 0.0
    vmem_gb = 0.0

    if NVIDIA_AVAILABLE:
        try:
            handle = pynvml.nvmlDeviceGetHandleByIndex(0)
            # GPU 利用率
            util = pynvml.nvmlDeviceGetUtilizationRates(handle)
            gpu_util = float(util.gpu)

            # 显存使用 (Bytes -> GB)
            mem = pynvml.nvmlDeviceGetMemoryInfo(handle)
            vmem_gb = mem.used / (1024 ** 3)
        except Exception:
            pass

    return cpu, gpu_util, vmem_gb, mem_gb


def collection_task(data_storage, stop_event):
    """
    子线程任务：循环采集数据
    """
    # [关键修复1] CPU 首次读取往往是 0，先空读一次初始化计数器
    psutil.cpu_percent(interval=None)
    time.sleep(0.1)  # 稍作停顿

    start_time = time.time()

    while not stop_event.is_set():
        current_t = time.time() - start_time

        # 获取数据
        c, g, v, m = get_resources()

        # 存入列表
        data_storage['time'].append(current_t)
        data_storage['cpu'].append(c)
        data_storage['gpu'].append(g)
        data_storage['vmem'].append(v)
        data_storage['memory'].append(m)

        # 严格控制 0.1s 间隔 (减去执行时间)
        time.sleep(0.1)


# --- 4. 绘图功能 ---

def plot_all_sessions(all_sessions):
    """绘制所有采集段的数据"""
    if not all_sessions:
        return

    # 找出最长的一次采集时间，作为 X 轴基准
    max_duration = 0
    for session in all_sessions:
        if session['time']:
            duration = session['time'][-1]
            if duration > max_duration:
                max_duration = duration

    # 颜色顺序
    colors = ['#00008B', '#FF0000', '#006400', '#FFA500', '#FFC0CB']

    # 子图配置: (数据键名, Y轴标签, Y轴最大值)
    plots_config = [
        ('cpu', 'CPU占用率 (%)', 100),
        ('gpu', 'GPU占用率 (%)', 100),
        ('vmem', '显存占用 (GB)', 16),
        ('memory', '内存占用 (GB)', 48)  # [关键修复3] 单位改为GB
    ]

    # 创建画布
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    # 设置大标题
    fig.suptitle(f'系统资源监控报告 (检测到显卡: {GPU_NAME})', fontsize=16)
    axes = axes.flatten()

    for ax_idx, (key, label, y_limit) in enumerate(plots_config):
        ax = axes[ax_idx]

        for i, session in enumerate(all_sessions):
            color = colors[i % len(colors)]
            if not session['time']: continue

            # 绘制曲线
            ax.plot(session['time'], session[key],
                    label=f"第{i + 1}次",
                    color=color,
                    linewidth=1.5,
                    marker='o', markersize=2, alpha=0.7)

        # 坐标轴设置
        ax.set_ylabel(label, fontsize=11, fontproperties=fm.FontProperties(fname=configure_chinese_font()))
        ax.set_xlabel("时间 (秒)", fontsize=11, fontproperties=fm.FontProperties(fname=configure_chinese_font()))
        ax.set_ylim(0, y_limit)
        ax.set_xlim(0, max_duration)
        ax.grid(True, linestyle='--', alpha=0.3)
        ax.legend(loc='upper right', fontsize='small')

    plt.tight_layout(rect=[0, 0.03, 1, 0.95])  # 调整布局给suptitle留空间
    plt.show()


# --- 5. 主程序 ---

def main():
    print("=" * 40)
    print("      系统资源监控工具 v2.0")
    print(f" [*] 检测显卡: {GPU_NAME}")
    if not NVIDIA_AVAILABLE:
        print(" [!] 警告: 未检测到 NVIDIA 显卡或 pynvml 库，GPU数据将为 0")
    print("=" * 40)

    # 获取采集次数
    while True:
        try:
            n = int(input("请输入采集次数 n (1-5): "))
            if 1 <= n <= 5: break
            print("请输入 1-5 之间的整数。")
        except ValueError:
            pass

    all_sessions_data = []

    for i in range(n):
        print(f"\n>>> 第 {i + 1} / {n} 次采集准备 <<<")
        input("【按回车键开始】...")

        # 数据容器
        current_data = {'time': [], 'cpu': [], 'gpu': [], 'vmem': [], 'memory': []}

        # 启动后台采集线程
        stop_event = threading.Event()
        t = threading.Thread(target=collection_task, args=(current_data, stop_event))
        t.daemon = True  # 设置为守护线程，防止主程序退出卡死
        t.start()

        print(f"正在采集... (按回车键停止)")
        input()  # 阻塞等待用户按回车

        # 停止采集
        stop_event.set()
        t.join(timeout=1.0)  # 等待线程结束

        # 保存数据
        all_sessions_data.append(current_data)

        # [关键修复1] 计算并打印最大变化量
        print(f"--- 第 {i + 1} 次采集结果统计 ---")

        configs = [
            ('cpu', 'CPU占用率', '%'),
            ('gpu', 'GPU占用率', '%'),
            ('vmem', '显存占用', 'GB'),
            ('memory', '内存占用', 'GB')
        ]

        for key, name, unit in configs:
            values = current_data[key]
            if len(values) >= 2:  # 至少要有两个点才能算变化
                # 排除第1个点（尤其是CPU，第1个点虽然我们在线程里做了预处理，但为了保险，计算变化时可以忽略前几个不稳定数据）
                # 这里简单计算全量数据的极差
                val_min = min(values)
                val_max = max(values)
                diff = val_max - val_min
                print(f"{name:<8} | 最大变化: {diff:5.2f} {unit:<2} | (Max: {val_max:5.2f}, Min: {val_min:5.2f})")
            elif values:
                print(f"{name:<8} | 数据点不足，无法计算变化 (Count: {len(values)})")
            else:
                print(f"{name:<8} | 无数据")

    print("\n正在生成图表...")
    plot_all_sessions(all_sessions_data)


if __name__ == "__main__":
    main()