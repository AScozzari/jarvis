#!/usr/bin/env python3
import shutil
import os

src = r"C:\Users\a.scozzari\Desktop\android-app\gradle\wrapper\gradle-8.7-all\gradle-8.7\lib\plugins\gradle-wrapper-8.7.jar"
dst = r"C:\Users\a.scozzari\Desktop\android-app\gradle\wrapper\gradle-wrapper.jar"

print(f"Source file exists: {os.path.exists(src)}")
print(f"Source: {src}")
print(f"Destination: {dst}")

try:
    shutil.copy2(src, dst)
    print(f"File copied successfully!")
    print(f"Destination file exists: {os.path.exists(dst)}")
    print(f"File size: {os.path.getsize(dst)} bytes")
except Exception as e:
    print(f"Error: {e}")

