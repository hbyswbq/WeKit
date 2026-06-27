package dev.ujhhgtg.wekit.features.items.scripting_java

import bsh.Interpreter
import dev.ujhhgtg.comptime.This
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.features.core.SwitchFeature
import dev.ujhhgtg.wekit.utils.WeLogger
import dev.ujhhgtg.wekit.utils.fs.KnownPaths
import dev.ujhhgtg.wekit.utils.fs.createDirectoriesNoThrow
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

@Feature(name = "脚本引擎", categories = ["脚本 (Java)"], description = "执行 Java 脚本")
object JavaScriptingHook : SwitchFeature() {

    private val TAG = This.Class.simpleName

    private val SCRIPTS_DIR = (KnownPaths.moduleData / "scripts_java").createDirectoriesNoThrow()

    val scripts = ConcurrentHashMap<String, JavaPlugin>()

    override fun onEnable() {
        WeLogger.d(TAG, "loading java scripts...")
        for (scriptDir in SCRIPTS_DIR.listDirectoryEntries().filter { it.isDirectory() }) {
            val dirName = scriptDir.name
            val mainFile = scriptDir / "main.java"
            val infoFile = scriptDir / "info.prop"
            if (!Files.exists(mainFile) || !Files.exists(infoFile)) {
                WeLogger.w(TAG, "skipping '$dirName': missing main.java or info.prop")
                continue
            }

            val content = runCatching { mainFile.readText() }.getOrElse { continue }
            val infoPropContent = runCatching { infoFile.readText() }.getOrElse { continue }
            val info = JavaPlugin.parseInfoProp(infoPropContent)
            WeLogger.d(TAG, "loaded script, name='${info.name}', length=${content.length}")

            val plugin = JavaPlugin(
                name = dirName,
                dir = scriptDir,
                info = info,
                content = content,
                interpreter = Interpreter(null, "")
            )
            scripts[dirName] = plugin
        }

        JavaEngine.executeAllOnLoad(scripts)
    }
}
