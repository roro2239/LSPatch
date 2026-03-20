package org.lsposed.lspatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.lspatch.config.Configs
import org.lsposed.lspatch.config.MyKeyStore
import org.lsposed.lspatch.share.Constants
import org.lsposed.lspatch.share.PatchConfig
import org.lsposed.patch.LSPatch
import org.lsposed.patch.util.Logger
import java.util.Collections.addAll

object Patcher {

    class Options(
        private val injectDex: Boolean,
        private val config: PatchConfig,
        private val apkPaths: List<String>,
        private val embeddedModules: List<String>?
    ) {
        fun toStringArray(): Array<String> {
            return buildList {
                addAll(apkPaths)
                add("-o"); add(lspApp.tmpApkDir.absolutePath)
                if (config.debuggable) add("-d")
                add("-l"); add(config.sigBypassLevel.toString())
                if (config.useManager) add("--manager")
                if (config.overrideVersionCode) add("-r")
                if (Configs.detailPatchLogs) add("-v")
                embeddedModules?.forEach {
                    add("-m"); add(it)
                }
                if(injectDex) add("--injectdex")
                if (!MyKeyStore.useDefault) {
                    addAll(arrayOf("-k", MyKeyStore.file.path, Configs.keyStorePassword, Configs.keyStoreAlias, Configs.keyStoreAliasPassword))
                }
            }.toTypedArray()
        }
    }

    suspend fun patch(logger: Logger, options: Options) {
        withContext(Dispatchers.IO) {
            LSPatch(logger, *options.toStringArray()).doCommandLine()

            val outputDir = lspApp.patchedApkDir
            outputDir.mkdirs()
            outputDir.listFiles()?.forEach {
                if (it.name.endsWith(Constants.PATCH_FILE_SUFFIX)) it.delete()
            }
            lspApp.tmpApkDir.walk()
                .filter { it.name.endsWith(Constants.PATCH_FILE_SUFFIX) }
                .forEach { apk ->
                    val file = outputDir.resolve(apk.name)
                    apk.inputStream().use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            logger.i("Patched files are saved to ${outputDir.absolutePath}")
        }
    }
}
