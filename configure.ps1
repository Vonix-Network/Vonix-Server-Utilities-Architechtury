$ErrorActionPreference = "Stop"
$rootDir = "C:\Users\Pargon\VSU"
$versions = @("1.20.1", "1.19.2", "1.18.2")

foreach ($v in $versions) {
    $dir = "$rootDir\vonix_server_utils-$v-fabric-forge-template"
    Write-Host "Configuring $v in $dir" -ForegroundColor Cyan

    # 1. Update gradle.properties
    $gp = "$dir\gradle.properties"
    if (Test-Path $gp) {
        $content = Get-Content $gp -Raw
        $content = $content -replace "mod_version\s*=\s*.*", "mod_version = 1.7.1"
        $content = $content -replace "maven_group\s*=\s*.*", "maven_group = network.vonix.serverutilities"
        $content = $content -replace "archives_base_name\s*=\s*.*", "archives_base_name = vonix_server_utilities"
        $content = $content -replace "archives_name\s*=\s*.*", "archives_name = vonix_server_utilities"
        $content = $content -replace "org.gradle.java.home=.*`r?`n", ""
        Set-Content -Path $gp -Value $content -Encoding UTF8
    }

    # 2. Update settings.gradle
    $sg = "$dir\settings.gradle"
    if (Test-Path $sg) {
        $content = Get-Content $sg -Raw
        $content = $content -replace "rootProject.name\s*=\s*'.*'", "rootProject.name = 'vonix_server_utilities'"
        Set-Content -Path $sg -Value $content -Encoding UTF8
    }

    # 3. Update fabric.mod.json
    $fmj = "$dir\fabric\src\main\resources\fabric.mod.json"
    if (Test-Path $fmj) {
        $content = Get-Content $fmj -Raw
        $content = $content -replace "`"id`":\s*`".*`"", "`"id`": `"vonix_server_utilities`""
        $content = $content -replace "`"name`":\s*`".*`"", "`"name`": `"Vonix Server Utilities`""
        $content = $content -replace "`"description`":\s*`".*`"", "`"description`": `"Server-side utility mod.`""
        $content = $content -replace "`"network\.vonix\.utils\.fabric\.ExampleModFabric`"", "`"network.vonix.serverutilities.fabric.VonixServerUtilitiesFabric`""
        $content = $content -replace "(?s)`"client`":\s*\[.*?\]\s*,?", ""
        $content = $content -replace "(?s)`"environment`":\s*`"\*`"", "`"environment`": `"server`""
        if ($content -notmatch "`"environment`":") {
            $content = $content -replace "`"license`":", "`"environment`": `"server`",`n  `"license`":"
        }
        $content = $content -replace "example_mod\.mixins\.json", "vonix_server_utilities.mixins.json"
        Set-Content -Path $fmj -Value $content -Encoding UTF8
    }

    # 4. Update mods.toml
    $mt = "$dir\forge\src\main\resources\META-INF\mods.toml"
    if (Test-Path $mt) {
        $content = Get-Content $mt -Raw
        $content = $content -replace "modId\s*=\s*`"example_mod`"", "modId = `"vonix_server_utilities`""
        $content = $content -replace "displayName\s*=\s*`".*`"", "displayName = `"Vonix Server Utilities`""
        $content = $content -replace "description\s*=\s*'''[\s\S]*?'''", "description = '''Server-side utility mod.'''"
        $content = $content -replace "\[\[dependencies\.example_mod\]\]", "[[dependencies.vonix_server_utilities]]"
        Set-Content -Path $mt -Value $content -Encoding UTF8
    }

    # 5. Rename mixins json
    $mixinsOld = "$dir\common\src\main\resources\example_mod.mixins.json"
    $mixinsNew = "$dir\common\src\main\resources\vonix_server_utilities.mixins.json"
    if (Test-Path $mixinsOld) {
        Rename-Item -Path $mixinsOld -NewName "vonix_server_utilities.mixins.json"
    }

    # Update mixins json content
    if (Test-Path $mixinsNew) {
        $content = Get-Content $mixinsNew -Raw
        $content = $content -replace "`"package`":\s*`".*`"", "`"package`": `"network.vonix.serverutilities.mixin`""
        Set-Content -Path $mixinsNew -Value $content -Encoding UTF8
    }

    # 6. Forge/Fabric main classes (create them and delete old ones)
    $fabricDir = "$dir\fabric\src\main\java\network\vonix\serverutilities\fabric"
    $forgeDir = "$dir\forge\src\main\java\network\vonix\serverutilities\forge"
    
    New-Item -ItemType Directory -Force $fabricDir | Out-Null
    New-Item -ItemType Directory -Force $forgeDir | Out-Null

    $fabricClass = "$fabricDir\VonixServerUtilitiesFabric.java"
    $forgeClass = "$forgeDir\VonixServerUtilitiesForge.java"

    $fabricCode = @"
package network.vonix.serverutilities.fabric;

import net.fabricmc.api.ModInitializer;
import network.vonix.serverutilities.VonixServerUtilities;

public final class VonixServerUtilitiesFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        VonixServerUtilities.init();
    }
}
"@
    Set-Content -Path $fabricClass -Value $fabricCode -Encoding UTF8

    $forgeCode = @"
package network.vonix.serverutilities.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import network.vonix.serverutilities.VonixServerUtilities;

@Mod(VonixServerUtilities.MOD_ID)
public final class VonixServerUtilitiesForge {
    public VonixServerUtilitiesForge() {
        EventBuses.registerModEventBus(VonixServerUtilities.MOD_ID,
                FMLJavaModLoadingContext.get().getModEventBus());
        VonixServerUtilities.init();
    }
}
"@
    Set-Content -Path $forgeClass -Value $forgeCode -Encoding UTF8

    # Update forge mixin reference in build.gradle
    $fb = "$dir\forge\build.gradle"
    if (Test-Path $fb) {
        $content = Get-Content $fb -Raw
        $content = $content -replace "example_mod\.mixins\.json", "vonix_server_utilities.mixins.json"
        Set-Content -Path $fb -Value $content -Encoding UTF8
    }
    
    # Update common build.gradle to add SQLite
    $cb = "$dir\common\build.gradle"
    if (Test-Path $cb) {
        $content = Get-Content $cb -Raw
        if ($content -notmatch "sqlite-jdbc") {
            $content = $content -replace "dependencies\s*\{", "dependencies {`n    implementation 'org.xerial:sqlite-jdbc:3.45.1.0'"
            Set-Content -Path $cb -Value $content -Encoding UTF8
        }
    }

    # Update fabric build.gradle to add SQLite bundle
    $fab = "$dir\fabric\build.gradle"
    if (Test-Path $fab) {
        $content = Get-Content $fab -Raw
        if ($content -notmatch "sqlite-jdbc") {
            $content = $content -replace "shadowBundle project\(path: ':common', configuration: 'transformProductionFabric'\)", "shadowBundle project(path: ':common', configuration: 'transformProductionFabric')`n    shadowBundle 'org.xerial:sqlite-jdbc:3.45.1.0'"
            Set-Content -Path $fab -Value $content -Encoding UTF8
        }
    }

    # Update forge build.gradle to add SQLite bundle
    $forgeb = "$dir\forge\build.gradle"
    if (Test-Path $forgeb) {
        $content = Get-Content $forgeb -Raw
        if ($content -notmatch "sqlite-jdbc") {
            $content = $content -replace "shadowBundle project\(path: ':common', configuration: 'transformProductionForge'\)", "shadowBundle project(path: ':common', configuration: 'transformProductionForge')`n    shadowBundle 'org.xerial:sqlite-jdbc:3.45.1.0'"
            Set-Content -Path $forgeb -Value $content -Encoding UTF8
        }
    }
}

Write-Host "Done configuring all templates." -ForegroundColor Green
