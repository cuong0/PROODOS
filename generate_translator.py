import re

with open('translator_updates.txt', 'r') as f:
    lines = f.readlines()

english_updates = []
chinese_updates = []

for i in range(0, len(lines), 2):
    if i+1 < len(lines):
        english_updates.append(lines[i].strip())
        chinese_updates.append(lines[i+1].strip())

with open('app/src/main/java/com/example/ui/Translator.kt', 'r') as f:
    content = f.read()

# Insert english updates
eng_insert = "\n        ".join(english_updates) + "\n"
content = re.sub(r'(private val englishMap = mapOf\()', r'\1\n        ' + eng_insert, content)

# Insert chinese updates
chi_insert = "\n        ".join(chinese_updates) + "\n"
content = re.sub(r'(private val chineseMap = mapOf\()', r'\1\n        ' + chi_insert, content)

with open('app/src/main/java/com/example/ui/Translator.kt', 'w') as f:
    f.write(content)

