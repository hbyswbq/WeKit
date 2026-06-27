package dev.ujhhgtg.wekit.features.api.core

import dev.ujhhgtg.wekit.dexkit.abc.IResolveDex
import dev.ujhhgtg.wekit.dexkit.dsl.dexConstructor
import dev.ujhhgtg.wekit.features.api.net.WeNetSceneApi
import dev.ujhhgtg.wekit.features.core.ApiFeature
import dev.ujhhgtg.wekit.features.core.Feature
import dev.ujhhgtg.wekit.utils.WeLogger

@Feature(name = "联系人服务", categories = ["API"], description = "提供联系人管理能力")
object WeContactApi : ApiFeature(), IResolveDex {

    // C1350 case 26: m4953("com.tencent.mm.pluginsdk.model") + C2812.m4143("MicroMsg.NetSceneVerifyUser.dkverify", "/cgi-bin/micromsg-bin/verifyuser")
    // C1261: "ConstructorNetSceneEq3" → constructor paramCount=3 (auto-detect 6-8 from ctor overloads in WAuxv m1785)
    private val ctorNetSceneVerifyUser by dexConstructor {
        searchPackages("com.tencent.mm.pluginsdk.model")
        matcher {
            usingEqStrings("MicroMsg.NetSceneVerifyUser.dkverify", "getLabelIdList, %s")
        }
    }

    fun verifyUser(userId: String, ticket: String, scene: Int) {
        try {
            val netScene = ctorNetSceneVerifyUser.newInstance(3, userId, ticket, scene, "", 0)
            WeNetSceneApi.sendNetScene(netScene)
        } catch (e: Exception) {
            WeLogger.e("WeSecurityApi", "verifyUser failed", e)
        }
    }
}
