package dev.ujhhgtg.wekit.utils.fs

import android.os.Environment
import dev.ujhhgtg.wekit.BuildConfig
import dev.ujhhgtg.wekit.utils.HostInfo
import java.nio.file.Path
import kotlin.io.path.div

object KnownPaths {

    val internalStorage: Path by lazy {
        Environment.getExternalStorageDirectory().toPath()
    }

    val moduleData by lazy {
        (internalStorage / "Android" / "data" / HostInfo.packageName / BuildConfig.TAG)
            .createDirectoriesNoThrow()
    }

    val codeCacheDir by lazy {
        HostInfo.application.codeCacheDir.toPath()
    }

    val moduleCache by lazy {
        (internalStorage / "Android" / "data" / HostInfo.packageName / "cache" / BuildConfig.TAG)
            .createDirectoriesNoThrow()
    }

    val downloads by lazy {
        (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toPath() / "WeKit")
            .createDirectoriesNoThrow()
    }
}
