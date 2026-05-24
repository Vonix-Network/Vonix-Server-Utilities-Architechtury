$ErrorActionPreference = "Stop"
$rootDir = "C:\Users\Pargon\VSU"
$v21 = "$rootDir\vonix_server_utils-1.21.1-fabric-neoforgetemplate"
$v20 = "$rootDir\vonix_server_utils-1.20.1-fabric-forge-template"
$v19 = "$rootDir\vonix_server_utils-1.19.2-fabric-forge-template"
$v18 = "$rootDir\vonix_server_utils-1.18.2-fabric-forge-template"
$commonSrc = "common\src\main\java\network\vonix\serverutilities"

function Port-Files {
    param([string]$targetDir, [string]$version)
    Write-Host "Porting to $version..." -ForegroundColor Cyan
    
    $srcDir = "$v21\$commonSrc"
    $dstDir = "$targetDir\$commonSrc"
    
    if (Test-Path $dstDir) {
        Remove-Item -Recurse -Force $dstDir
    }
    
    New-Item -ItemType Directory -Force (Split-Path $dstDir -Parent) | Out-Null
    Copy-Item -Recurse -Force $srcDir $dstDir
    
    $files = Get-ChildItem -Path $dstDir -Recurse -Filter "*.java"
    
    foreach ($file in $files) {
        $content = Get-Content $file.FullName -Raw
        
        # Common to 1.20.1, 1.19.2, 1.18.2
        $content = $content.Replace("player.serverLevel()", "((net.minecraft.server.level.ServerLevel) player.level())")
        $content = $content.Replace("target.serverLevel()", "((net.minecraft.server.level.ServerLevel) target.level())")
        $content = $content.Replace("dest.serverLevel()", "((net.minecraft.server.level.ServerLevel) dest.level())")
        
        if ($version -eq "1.19.2" -or $version -eq "1.18.2") {
            if ($file.Name -eq "KitManager.java") {
                $content = $content.Replace("import net.minecraft.core.registries.BuiltInRegistries;", "import net.minecraft.core.Registry;")
                $content = $content.Replace("ResourceLocation.tryParse(itemId)", "new ResourceLocation(itemId)")
                $content = $content.Replace("BuiltInRegistries.ITEM.get(loc)", "Registry.ITEM.get(loc)")
            }
            if ($file.Name -eq "EventHandler.java") {
                $content = $content.Replace("(dispatcher, registry, selection) ->", "(dispatcher, dedicated) ->")
            }
            if ($file.Name -eq "AdminManager.java" -or $file.Name -eq "UtilityCommands.java") {
                $content = $content.Replace("import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;", "import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;")
                $content = $content.Replace("import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;", "")
                
                $content = $content.Replace("new ClientboundPlayerInfoUpdatePacket(`n                            ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, player)", "new ClientboundPlayerInfoPacket(`n                            ClientboundPlayerInfoPacket.Action.ADD_PLAYER, player)")
                $content = $content.Replace("new ClientboundPlayerInfoRemovePacket(List.of(uuid))", "new ClientboundPlayerInfoPacket(`n                            ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, player)")
                $content = $content.Replace("new ClientboundPlayerInfoUpdatePacket(`n                ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player)", "new ClientboundPlayerInfoPacket(`n                ClientboundPlayerInfoPacket.Action.UPDATE_DISPLAY_NAME, player)")
            }
        }
        
        if ($file.Name -eq "ModCommands.java") {
            $content = $content.Replace("Architectury 1.21.1", "Architectury $version")
        }
        
        Set-Content -Path $file.FullName -Value $content -Encoding UTF8
    }
    
    Write-Host "Done porting to $version." -ForegroundColor Green
}

Port-Files $v20 "1.20.1"
Port-Files $v19 "1.19.2"
Port-Files $v18 "1.18.2"
