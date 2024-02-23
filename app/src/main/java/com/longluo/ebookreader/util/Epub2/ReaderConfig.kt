package com.longluo.ebookreader.util.Epub2

import android.content.Context
import android.graphics.Color
import com.folioreader.Config

class ReaderConfig(
    context: Context?, identifier: String?, themeColor: String?,
    scrollDirection: String, allowSharing: Boolean, showTts: Boolean, nightMode: Boolean
) {
    private val identifier: String? = null
    private val themeColor: String? = null
    private val scrollDirection: String? = null
    private val allowSharing = false
    private val showTts = false
    private val nightMode = false
    var config: Config

    init {

//        config = AppUtil.getSavedConfig(context);
//        if (config == null)
        config = Config()
        if (scrollDirection == "vertical") {
            config.allowedDirection = Config.AllowedDirection.ONLY_VERTICAL
        } else if (scrollDirection == "horizontal") {
            config.allowedDirection = Config.AllowedDirection.ONLY_HORIZONTAL
        } else {
            config.allowedDirection = Config.AllowedDirection.VERTICAL_AND_HORIZONTAL
        }
        config.setThemeColorInt(Color.parseColor(themeColor))
        config.setNightThemeColorInt(Color.parseColor(themeColor))
        config.isShowRemainingIndicator = true
        config.isShowTts = showTts
        config.isNightMode = nightMode
    }
}