import re

file_path = "app/src/main/AndroidManifest.xml"
with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# Add permissions
permissions = """    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />"""
if permissions not in content:
    content = content.replace('<application', permissions + '\n    <application')

# Add service
service = """        <service
            android:name="com.example.ui.BackupService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />"""
if service not in content:
    content = content.replace('</application>', service + '\n    </application>')

with open(file_path, "w", encoding="utf-8") as f:
    f.write(content)
print("Manifest patched")
