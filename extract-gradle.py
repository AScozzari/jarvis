#!/usr/bin/env python3
import zipfile
import os
import shutil

wrapper_dir = r"C:\Users\a.scozzari\Desktop\android-app\gradle\wrapper"
zip_path = os.path.join(wrapper_dir, "gradle-8.7-all.zip")
temp_dir = os.path.join(wrapper_dir, "temp-extract")
jar_source = os.path.join(temp_dir, "gradle-8.7", "gradle", "wrapper", "gradle-wrapper.jar")
jar_dest = os.path.join(wrapper_dir, "gradle-wrapper.jar")

try:
    # Create temp directory
    if not os.path.exists(temp_dir):
        os.makedirs(temp_dir)

    # Extract zip
    print(f"Extracting {zip_path}...")
    with zipfile.ZipFile(zip_path, 'r') as zip_ref:
        zip_ref.extractall(temp_dir)
    print("Extraction complete!")

    # Copy jar
    print(f"Copying {jar_source} to {jar_dest}...")
    shutil.copy(jar_source, jar_dest)
    print("Copy complete!")

    # Clean up temp directory
    print(f"Cleaning up {temp_dir}...")
    shutil.rmtree(temp_dir)
    print("Cleanup complete!")

    # Remove zip files
    print("Removing zip files...")
    for zip_file in ["gradle-8.7-all.zip", "gradle-8.7-bin.zip"]:
        zip_path_to_remove = os.path.join(wrapper_dir, zip_file)
        if os.path.exists(zip_path_to_remove):
            os.remove(zip_path_to_remove)
            print(f"Removed {zip_file}")

    print("\nAll done! gradle-wrapper.jar is ready.")

except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()

