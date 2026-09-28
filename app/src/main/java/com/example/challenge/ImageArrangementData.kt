package com.example.challenge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.example.R
import kotlin.random.Random

object ImageArrangementData {

    data class ImageResourceItem(
        val title: String,
        val resId: Int
    )

    // Built-in list of at least 10 drawable resources in the app.
    // Easy to add more by adding any R.drawable.* item here.
    val DRAWABLE_IMAGES: List<ImageResourceItem> = listOf(
        ImageResourceItem("Sunrise Mountains", R.drawable.ic_arr_sunrise_mountains),
        ImageResourceItem("Morning Coffee & Rooster", R.drawable.ic_arr_coffee_rooster),
        ImageResourceItem("City Skyline Sunrise", R.drawable.ic_arr_city_skyline),
        ImageResourceItem("Forest River Stream", R.drawable.ic_arr_forest_stream),
        ImageResourceItem("Hot Air Balloon", R.drawable.ic_arr_hot_air_balloon),
        ImageResourceItem("Coastal Lighthouse", R.drawable.ic_arr_lighthouse_ocean),
        ImageResourceItem("Desert Sunburst", R.drawable.ic_arr_desert_sunburst),
        ImageResourceItem("Wake-Up Energy", R.drawable.ic_arr_alarm_energy),
        ImageResourceItem("Tropical Horizon", R.drawable.ic_arr_tropical_beach),
        ImageResourceItem("Cosmic Rocket", R.drawable.ic_arr_space_shuttle)
    )

    /**
     * Picks an image from the built-in list.
     * Selection is fully random every time: sometimes a fresh image,
     * sometimes a reused image with a new shuffle.
     */
    fun pickRandomImage(previousResId: Int? = null): ImageResourceItem {
        if (DRAWABLE_IMAGES.isEmpty()) {
            return ImageResourceItem("Alarm", R.drawable.force_alarm_logo)
        }
        val roll = Random.nextFloat()
        return if (previousResId != null && roll < 0.65f) {
            // Pick a different/fresh image 65% of the time
            val candidates = DRAWABLE_IMAGES.filter { it.resId != previousResId }
            if (candidates.isNotEmpty()) candidates.random() else DRAWABLE_IMAGES.random()
        } else {
            // Pick any image (could be the same one reused)
            DRAWABLE_IMAGES.random()
        }
    }

    /**
     * Renders any drawable resource into a clean Bitmap with given dimensions.
     */
    fun renderDrawableToBitmap(context: Context, resId: Int, targetWidth: Int, targetHeight: Int): Bitmap {
        val drawable = ContextCompat.getDrawable(context, resId)
            ?: throw IllegalArgumentException("Resource $resId not found")

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetWidth, targetHeight)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * Slices a Bitmap into a grid of (cols x rows) pieces.
     * Returned list contains pieces in original/correct order (index 0..total-1).
     */
    fun sliceBitmap(source: Bitmap, cols: Int, rows: Int): List<Bitmap> {
        val pieceWidth = source.width / cols
        val pieceHeight = source.height / rows
        val slices = mutableListOf<Bitmap>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val x = c * pieceWidth
                val y = r * pieceHeight
                val slice = Bitmap.createBitmap(source, x, y, pieceWidth, pieceHeight)
                slices.add(slice)
            }
        }
        return slices
    }
}
