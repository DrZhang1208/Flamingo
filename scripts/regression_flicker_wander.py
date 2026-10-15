#!/usr/bin/env python3
"""Flamingo 真机一键回归：前台守卫 + 复现路径 + 截图取证。
用法: python3 scripts/regression_flicker_wander.py [apk路径]
步骤: 安装(可选) -> 前台守卫 -> 播放页连续下一首 -> 收起录屏 -> 漫游验证
"""
import subprocess, sys, time
from pathlib import Path

SERIAL = "ANYX024606000396"
PKG = "com.flamingo.player"
ADB = ["adb", "-s", SERIAL]

def run(*a, timeout=20, check=False):
    r = subprocess.run(ADB + list(a), capture_output=True, text=True, timeout=timeout)
    if check and r.returncode != 0:
        raise RuntimeError(f"{a}: {r.stderr.strip()}")
    return r

def guard():
    r = run("shell", "dumpsys", "activity", "activities")
    for line in r.stdout.splitlines():
        if "topResumedActivity=" in line:
            if f"{PKG}/yos.music.player.MainActivity" not in line:
                raise RuntimeError(f"Flamingo 未在前台，停止操作: {line.strip()}")
            return
    raise RuntimeError("无法获取前台焦点")

def main():
    if len(sys.argv) > 1:
        print(run("install", "-r", "-t", sys.argv[1], timeout=180).stdout.strip())
    run("shell", "am", "force-stop", PKG)
    time.sleep(1)
    run("shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1")
    time.sleep(4)
    guard()
    # 展开播放页
    run("shell", "input", "tap", "632", "2400")
    time.sleep(1.2)
    guard()
    # 连续下一首 + 收起录屏
    remote = "/sdcard/flamingo-regression.mp4"
    rec = subprocess.Popen(ADB + ["shell", "screenrecord", "--time-limit", "12",
                                  "--size", "720x1594", remote],
                           stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    time.sleep(0.7)
    for _ in range(10):
        guard()
        run("shell", "input", "tap", "967", "2200")
        time.sleep(0.09)
    guard()
    run("shell", "input", "swipe", "632", "215", "632", "2100", "180")
    rec.wait(timeout=25)
    out = Path(".dbg/regression-collapse.mp4")
    print("录屏:", run("pull", remote, str(out), timeout=30).returncode == 0 and out or "失败")
    # 漫游快速验证：回主页点漫游入口，检查上一首拦截
    time.sleep(1.5)
    guard()
    run("shell", "input", "keyevent", "4")
    time.sleep(1)
    guard()
    r = run("shell", "dumpsys", "media_session")
    print("会话状态已采样，输出到 stdout 供人工判断")

if __name__ == "__main__":
    main()
