package app.simple.waller.glide.wallpaper

import android.content.Context
import app.simple.waller.models.Wallpaper

class Wallpaper(
        val wallpaper: Wallpaper,
        val context: Context) {

    override fun equals(other: Any?): Boolean {
        if (other is Wallpaper) {
            return wallpaper == other
        }
        return false
    }

    override fun hashCode(): Int {
        return wallpaper.hashCode()
    }
}