import re

with open('app/src/main/java/com/example/ui/Translator.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
seen_keys_in_current_map = set()
in_map = False

for line in lines:
    if 'mapOf(' in line:
        in_map = True
        seen_keys_in_current_map = set()
        new_lines.append(line)
        continue
    
    if in_map and ')' in line and not 'to' in line:
        in_map = False
        new_lines.append(line)
        continue
        
    if in_map:
        match = re.search(r'^\s*"([^"]+)"\s*to\s*', line)
        if match:
            key = match.group(1)
            if key in seen_keys_in_current_map:
                # We skip the duplicate line
                continue
            else:
                seen_keys_in_current_map.add(key)
                new_lines.append(line)
        else:
            new_lines.append(line)
    else:
        new_lines.append(line)

with open('app/src/main/java/com/example/ui/Translator.kt', 'w') as f:
    f.writelines(new_lines)

