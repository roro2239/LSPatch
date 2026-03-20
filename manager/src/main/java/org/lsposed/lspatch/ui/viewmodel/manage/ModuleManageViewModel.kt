package org.lsposed.lspatch.ui.viewmodel.manage

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import org.lsposed.lspatch.lspApp
import org.lsposed.lspatch.util.LSPPackageManager
import java.util.Properties
import java.util.zip.ZipFile

class ModuleManageViewModel : ViewModel() {

    companion object {
        private const val TAG = "ModuleManageViewModel"
        private const val MODERN_JAVA_INIT = "META-INF/xposed/java_init.list"
        private const val MODERN_MODULE_PROP = "META-INF/xposed/module.prop"
    }

    class XposedInfo(
        val api: Int,
        val description: String,
        val scope: List<String>
    )

    private class ModernModuleInfo(
        val minApi: Int,
        val description: String
    )

    private fun extractIntPart(raw: String?): Int {
        if (raw.isNullOrBlank()) return 0
        var result = 0
        for (c in raw) {
            if (c in '0'..'9') {
                result = result * 10 + (c - '0')
            } else {
                break
            }
        }
        return result
    }

    private fun getLegacyApi(metaData: android.os.Bundle?): Int? {
        val raw = metaData?.get("xposedminversion") ?: return null
        return when (raw) {
            is Int -> raw
            is String -> extractIntPart(raw).takeIf { it > 0 }
            else -> null
        }
    }

    private fun getModernModuleInfo(app: ApplicationInfo): ModernModuleInfo? {
        val apks = buildList {
            app.splitSourceDirs?.let { addAll(it) }
            add(app.sourceDir)
        }
        for (apk in apks) {
            val info = runCatching {
                ZipFile(apk).use { zip ->
                    if (zip.getEntry(MODERN_JAVA_INIT) == null) return@use null
                    var minApi = 100
                    var description = ""
                    zip.getEntry(MODERN_MODULE_PROP)?.let { propEntry ->
                        val properties = Properties()
                        zip.getInputStream(propEntry).use(properties::load)
                        val parsed = extractIntPart(properties.getProperty("minApiVersion"))
                        if (parsed > 0) minApi = parsed
                        description = properties.getProperty("description")?.trim().orEmpty()
                    }
                    ModernModuleInfo(minApi, description)
                }
            }.getOrNull()
            if (info != null) return info
        }
        return null
    }

    private fun getLegacyDescription(metaData: android.os.Bundle?): String {
        return metaData?.getString("xposeddescription")?.trim().orEmpty()
    }

    private fun getModernDescription(app: ApplicationInfo, modernInfo: ModernModuleInfo, metaData: android.os.Bundle?): String {
        val appDescription = app.loadDescription(lspApp.packageManager)?.toString()?.trim().orEmpty()
        if (appDescription.isNotEmpty()) return appDescription
        if (modernInfo.description.isNotEmpty()) return modernInfo.description
        return getLegacyDescription(metaData)
    }

    val appList: List<Pair<LSPPackageManager.AppInfo, XposedInfo>> by derivedStateOf {
        LSPPackageManager.appList.mapNotNull { appInfo ->
            if (!appInfo.isXposedModule) return@mapNotNull null
            val metaData = appInfo.app.metaData
            val modernInfo = getModernModuleInfo(appInfo.app)
            val api = modernInfo?.minApi ?: getLegacyApi(metaData) ?: return@mapNotNull null
            val description = if (modernInfo != null) {
                getModernDescription(appInfo.app, modernInfo, metaData)
            } else {
                getLegacyDescription(metaData)
            }
            appInfo to XposedInfo(
                api,
                description,
                emptyList() // TODO: scope
            )
        }.also {
            Log.d(TAG, "Loaded ${it.size} Xposed modules")
        }
    }
}
