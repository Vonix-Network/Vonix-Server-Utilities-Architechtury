import os
import re

for root, _, files in os.walk(r'C:\Users\Pargon\VSU\vonix_server_utils-1.18.2-fabric-forge-template\common\src\main\java'):
    for f in files:
        if f.endswith('.java'):
            path = os.path.join(root, f)
            with open(path, 'r', encoding='utf-8') as file:
                content = file.read()
            
            # Use non-greedy match to find everything inside .sendSystemMessage(...) until the closing );
            new_content = re.sub(r'\.sendSystemMessage\((.*?)\);', r'.sendMessage(\1, net.minecraft.Util.NIL_UUID);', content, flags=re.DOTALL)
            
            if new_content != content:
                with open(path, 'w', encoding='utf-8') as file:
                    file.write(new_content)
