import os

def replace_in_file(filepath, replacements):
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return
    with open(filepath, 'r') as f:
        content = f.read()
    
    new_content = content
    for old, new in replacements:
        new_content = new_content.replace(old, new)
        
    if new_content != content:
        with open(filepath, 'w') as f:
            f.write(new_content)
        print(f"Updated {filepath}")
    else:
        print(f"No changes for {filepath}")

replace_in_file('metadata.json', [('Ứng dụng bán lẻ POS', 'Ứng dụng Proodos POS')])
replace_in_file('app/build.gradle.kts', [
    ('applicationId = "com.aistudio.banle.qpxzwt"', 'applicationId = "com.aistudio.proodos.qpxzwt"'),
    ('versionCode = 1', 'versionCode = 2')
])
replace_in_file('app/src/main/java/com/example/ui/MainViewModel.kt', [('banle_prefs', 'proodos_prefs')])
replace_in_file('app/src/main/java/com/example/ui/PosApp.kt', [('banle_prefs', 'proodos_prefs')])
replace_in_file('app/src/main/java/com/example/data/AppDatabase.kt', [('banle_database', 'proodos_database')])

