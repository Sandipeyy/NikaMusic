// puts a look module's values on nika's look settings, and gives the user's own back when it goes off

package com.sandip.nikamusic.modules

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.sandip.nikamusic.constants.BlurQuality
import com.sandip.nikamusic.constants.BlurQualityKey
import com.sandip.nikamusic.constants.BlurStrengthPxKey
import com.sandip.nikamusic.constants.ChromeGlassStyle
import com.sandip.nikamusic.constants.ChromeGlassStyleKey
import com.sandip.nikamusic.constants.DynamicThemeKey
import com.sandip.nikamusic.constants.ElementBlurKey
import com.sandip.nikamusic.constants.EnableProgressiveBlurKey
import com.sandip.nikamusic.constants.FlagshipBackgroundStyleKey
import com.sandip.nikamusic.constants.HomeStyle
import com.sandip.nikamusic.constants.HomeStyleKey
import com.sandip.nikamusic.constants.IpodTheme
import com.sandip.nikamusic.constants.IpodThemeKey
import com.sandip.nikamusic.constants.IpodWheelColor
import com.sandip.nikamusic.constants.IpodWheelColorKey
import com.sandip.nikamusic.constants.LyricsMotionStyle
import com.sandip.nikamusic.constants.LyricsMotionStyleKey
import com.sandip.nikamusic.constants.PlayerBackgroundStyle
import com.sandip.nikamusic.constants.PlayerBackgroundStyleKey
import com.sandip.nikamusic.constants.PlayerStyle
import com.sandip.nikamusic.constants.PlayerStyleKey
import com.sandip.nikamusic.constants.PlaylistStyle
import com.sandip.nikamusic.constants.PlaylistStyleKey
import com.sandip.nikamusic.constants.PureBlackKey
import com.sandip.nikamusic.constants.SelectedThemeColorKey
import com.sandip.nikamusic.constants.ThumbnailCornerRadiusKey
import com.sandip.nikamusic.module.LookSetting
import com.sandip.nikamusic.modules.skin.parseHexColor
import com.sandip.nikamusic.utils.dataStore
import org.json.JSONObject

internal class ModuleLooks(private val context: Context) {

    // what each module replaced, kept apart from the settings so it outlives the app closing
    private val backups = context.getSharedPreferences("module_looks", Context.MODE_PRIVATE)

    fun isApplied(id: String): Boolean = backups.contains(id)

    suspend fun apply(id: String, look: Map<String, String>) {
        if (isApplied(id)) return
        val replaced = JSONObject()
        context.dataStore.edit { prefs ->
            look.forEach { (name, raw) ->
                val setting = Settings[name] ?: return@forEach
                val previous = setting.read(prefs)
                if (setting.write(prefs, raw)) replaced.put(name, previous ?: JSONObject.NULL)
            }
        }
        backups.edit { putString(id, replaced.toString()) }
    }

    suspend fun restore(id: String, look: Map<String, String>) {
        val replaced = backups.getString(id, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return
        context.dataStore.edit { prefs ->
            replaced.keys().forEach { name ->
                val setting = Settings[name] ?: return@forEach
                // changed by hand since the look went on: that's the user's choice now, it stays
                if (setting.read(prefs) != setting.normalise(look[name])) return@forEach
                if (replaced.isNull(name)) setting.clear(prefs) else setting.write(prefs, replaced.getString(name))
            }
        }
        backups.edit { remove(id) }
    }

    /** one look setting, the module's text turned into the setting's own type, anything out of range turned away */
    private class Setting<T : Any>(val key: Preferences.Key<T>, val parse: (String) -> T?) {
        fun read(prefs: Preferences): String? = prefs[key]?.toString()
        fun normalise(raw: String?): String? = raw?.let(parse)?.toString()
        fun clear(prefs: MutablePreferences) {
            prefs.remove(key)
        }
        fun write(prefs: MutablePreferences, raw: String): Boolean {
            val value = parse(raw) ?: return false
            prefs[key] = value
            return true
        }
    }

    private companion object {
        inline fun <reified E : Enum<E>> enumName(raw: String): String? =
            raw.trim().uppercase().takeIf { name -> enumValues<E>().any { it.name == name } }

        fun bool(raw: String): Boolean? = raw.trim().lowercase().toBooleanStrictOrNull()

        fun number(raw: String, range: ClosedFloatingPointRange<Float>): Float? = raw.trim().toFloatOrNull()?.takeIf { it in range }

        val Settings: Map<String, Setting<*>> = mapOf(
            LookSetting.PLAYER_STYLE to Setting(PlayerStyleKey) { enumName<PlayerStyle>(it) },
            LookSetting.HOME_STYLE to Setting(HomeStyleKey) { enumName<HomeStyle>(it) },
            LookSetting.PLAYLIST_STYLE to Setting(PlaylistStyleKey) { enumName<PlaylistStyle>(it) },
            LookSetting.COVER_ROUNDING to Setting(ThumbnailCornerRadiusKey) { number(it, 0f..45f) },
            LookSetting.LYRICS_MOTION to Setting(LyricsMotionStyleKey) { enumName<LyricsMotionStyle>(it) },
            LookSetting.BLUR_QUALITY to Setting(BlurQualityKey) { enumName<BlurQuality>(it) },
            LookSetting.BLUR_STRENGTH to Setting(BlurStrengthPxKey) { number(it, 10f..50f) },
            LookSetting.PROGRESSIVE_BLUR to Setting(EnableProgressiveBlurKey) { bool(it) },
            LookSetting.ELEMENT_BLUR to Setting(ElementBlurKey) { bool(it) },
            LookSetting.PURE_BLACK to Setting(PureBlackKey) { bool(it) },
            LookSetting.ACCENT_COLOR to Setting(SelectedThemeColorKey) { parseHexColor(it)?.toArgb() },
            LookSetting.DYNAMIC_THEME to Setting(DynamicThemeKey) { bool(it) },
            LookSetting.PLAYER_BACKGROUND to Setting(PlayerBackgroundStyleKey) { enumName<PlayerBackgroundStyle>(it) },
            LookSetting.FLAGSHIP_BACKGROUND to Setting(FlagshipBackgroundStyleKey) { enumName<PlayerBackgroundStyle>(it) },
            LookSetting.IPOD_THEME to Setting(IpodThemeKey) { enumName<IpodTheme>(it) },
            LookSetting.IPOD_WHEEL to Setting(IpodWheelColorKey) { enumName<IpodWheelColor>(it) },
            LookSetting.BOTTOM_BAR to Setting(ChromeGlassStyleKey) { enumName<ChromeGlassStyle>(it) },
        )
    }
}
