import os
import shutil
import re

ROOT = r"C:\Users\Pargon\VSU"

V1_21 = os.path.join(ROOT, "vonix_server_utils-1.21.1-fabric-neoforgetemplate")
V1_20 = os.path.join(ROOT, "vonix_server_utils-1.20.1-fabric-forge-template")
V1_19 = os.path.join(ROOT, "vonix_server_utils-1.19.2-fabric-forge-template")
V1_18 = os.path.join(ROOT, "vonix_server_utils-1.18.2-fabric-forge-template")

COMMON_SRC = r"common\src\main\java\network\vonix\serverutilities"

def port_files(target_dir, version):
    print(f"Porting to {version}...")
    src_dir = os.path.join(V1_21, COMMON_SRC)
    dst_dir = os.path.join(target_dir, COMMON_SRC)
    
    if os.path.exists(dst_dir):
        shutil.rmtree(dst_dir)
    shutil.copytree(src_dir, dst_dir)
    
    # Process all Java files in dst_dir
    for root, _, files in os.walk(dst_dir):
        for f in files:
            if not f.endswith(".java"):
                continue
            path = os.path.join(root, f)
            with open(path, "r", encoding="utf-8") as file:
                content = file.read()
                
            # Apply changes based on version
            
            # Common to 1.20.1, 1.19.2, 1.18.2
            content = content.replace("player.serverLevel()", "((net.minecraft.server.level.ServerLevel) player.level())")
            content = content.replace("target.serverLevel()", "((net.minecraft.server.level.ServerLevel) target.level())")
            content = content.replace("dest.serverLevel()", "((net.minecraft.server.level.ServerLevel) dest.level())")
            
            if version in ["1.19.2", "1.18.2"]:
                # KitManager.java changes
                if "KitManager.java" in path:
                    content = content.replace("import net.minecraft.core.registries.BuiltInRegistries;", "import net.minecraft.core.Registry;")
                    content = content.replace("ResourceLocation.tryParse(itemId)", "new ResourceLocation(itemId)")
                    content = content.replace("BuiltInRegistries.ITEM.get(loc)", "Registry.ITEM.get(loc)")
                
                # EventHandler.java changes
                if "EventHandler.java" in path:
                    content = content.replace("(dispatcher, registry, selection) ->", "(dispatcher, dedicated) ->")
                
                # AdminManager.java and UtilityCommands.java packet changes
                if "AdminManager.java" in path or "UtilityCommands.java" in path:
                    content = content.replace("import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;", "import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;")
                    content = content.replace("import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;", "")
                    
                    # Update vanish packets
                    content = content.replace("new ClientboundPlayerInfoUpdatePacket(\n                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player)", "new ClientboundPlayerInfoPacket(\n                            ClientboundPlayerInfoPacket.Action.ADD_PLAYER, player)")
                    
                    content = content.replace("new ClientboundPlayerInfoRemovePacket(List.of(uuid))", "new ClientboundPlayerInfoPacket(\n                            ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, player)")
                    
                    # Tab update packet
                    content = content.replace("new ClientboundPlayerInfoUpdatePacket(\n                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player)", "new ClientboundPlayerInfoPacket(\n                ClientboundPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME, player)")
                    
            if "ModCommands.java" in path:
                content = content.replace("Architectury 1.21.1", f"Architectury {version}")
                
            with open(path, "w", encoding="utf-8") as file:
                file.write(content)
                
    print(f"Done porting to {version}.")

port_files(V1_20, "1.20.1")
port_files(V1_19, "1.19.2")
port_files(V1_18, "1.18.2")
