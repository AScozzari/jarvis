#!/usr/bin/env python3
import zipfile
import os

zip_path = r"C:\Users\a.scozzari\Desktop\android-app\java-setup\jdk17.zip"
extract_path = r"C:\Users\a.scozzari\Desktop\android-app\java-setup"

try:
    print(f"Extracting {zip_path}...")
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        zip_ref.extractall(extract_path)
    print("Extraction complete!")

    # List the extracted contents
    print("\nContents of java-setup:")
    for item in os.listdir(extract_path):
        path = os.path.join(extract_path, item)
        if os.path.isdir(path):
            print(f"  [DIR] {item}")
        else:
            print(f"  [FILE] {item}")

except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()

