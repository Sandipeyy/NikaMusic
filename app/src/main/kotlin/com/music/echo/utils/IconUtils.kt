package echo.music.iad1tya.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIconType(val value: Int) {
  DEFAULT(0),
  BILLIE_EILISH(13),
  SABRINA_CARPENTER(14),
  SABRINA_CARPENTER_2(15),
  DOG(20)
}

object IconUtils {
  private val aliases =
    mapOf(
      AppIconType.DEFAULT to "echo.music.iad1tya.MainActivityAlias",
      AppIconType.BILLIE_EILISH to "echo.music.iad1tya.MainActivityBillieEilish",
      AppIconType.SABRINA_CARPENTER to "echo.music.iad1tya.MainActivitySabrina",
      AppIconType.SABRINA_CARPENTER_2 to "echo.music.iad1tya.MainActivitySabrina2",
      AppIconType.DOG to "echo.music.iad1tya.MainActivityDog"
    )

  fun setIcon(context: Context, iconType: AppIconType) {
    val pm = context.packageManager
    aliases.forEach { (type, className) ->
      pm.setComponentEnabledSetting(
        ComponentName(context, className),
        if (type == iconType) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        PackageManager.DONT_KILL_APP
      )
    }
  }
}
