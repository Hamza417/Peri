package app.simple.peri.ui

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.drawToBitmap
import androidx.core.view.marginBottom
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.simple.peri.R
import app.simple.peri.constants.BundleConstants
import app.simple.peri.databinding.FragmentWallpaperScreenBinding
import app.simple.peri.databinding.WallpaperEditBinding
import app.simple.peri.models.Wallpaper
import app.simple.peri.preferences.MainPreferences
import app.simple.peri.utils.BitmapUtils.applySaturation
import app.simple.peri.utils.ConditionUtils.invert
import app.simple.peri.utils.FileUtils.toUri
import app.simple.peri.utils.ParcelUtils.parcelable
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.glide.ZoomableGlideImage
import java.io.ByteArrayOutputStream
import java.io.File

class WallpaperScreen : Fragment() {

    private var binding: FragmentWallpaperScreenBinding? = null
    private var wallpaper: Wallpaper? = null

    private var bitmap: Bitmap? = null
    private var uri: Uri? = null
    private val blurRadius = 25F

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentWallpaperScreenBinding.inflate(inflater, container, false)

        wallpaper = requireArguments().parcelable(BundleConstants.WALLPAPER)
        binding?.composeView?.transitionName = wallpaper?.uri

        binding?.composeView?.apply {
            // Dispose of the Composition when the view's LifecycleOwner
            // is destroyed
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            // Set the content of the ComposeView to a @Composable
            // function
            setContent {
                ZoomableGlideImage(
                        model = wallpaper?.uri?.toUri(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                        onLongPress = {
                                            binding?.fab?.performClick()
                                        }
                                )
                            },
                        alignment = Alignment.Center,
                        contentScale = ContentScale.FillHeight
                )
                {
                    it.addListener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            startPostponedEnterTransition()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                binding?.composeView?.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix().apply {
                                    setSaturation(requireArguments().getFloat(BundleConstants.SATURATION_VALUE, 0.5F).toSaturation())
                                })))
                            }

                            bitmap = binding?.composeView?.drawToBitmap()
                            return false
                        }
                    })
                        .transition(withCrossFade())
                        .disallowHardwareConfig()
                        .fitCenter()
                }
            }
        }

        return binding?.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().findViewById<CoordinatorLayout>(R.id.mainContainer).setBackgroundColor(Color.BLACK)
        fixNavigationBarOverlap()
        postponeEnterTransition()
        allowEnterTransitionOverlap = true
        allowReturnTransitionOverlap = true
        enterTransition = MaterialFadeThrough()
        returnTransition = MaterialFadeThrough()
        exitTransition = MaterialFadeThrough()

        sharedElementEnterTransition = MaterialContainerTransform().apply {
            duration = resources.getInteger(R.integer.animation_duration).toLong()
            scrimColor = Color.TRANSPARENT
        }

        binding?.fab?.setOnClickListener {
            if (MainPreferences.getAppEngine()) {
                /**
                 * Show list of options to set wallpaper
                 * on lock, home or both screens
                 */
                val list = arrayOf(
                        getString(R.string.home_screen),
                        getString(R.string.lock_screen),
                        getString(R.string.both))

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.set_as_wallpaper)
                    .setItems(list) { d, which ->
                        when (which) {
                            0 -> {
                                setWallpaper(WallpaperManager.FLAG_SYSTEM)
                                d.dismiss()
                            }

                            1 -> {
                                setWallpaper(WallpaperManager.FLAG_LOCK)
                                d.dismiss()
                            }

                            2 -> {
                                setWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                                d.dismiss()
                            }
                        }
                    }
                    .setNegativeButton(R.string.close) { d, _ ->
                        d.dismiss()
                    }
                    .show()
            } else {
                setWallpaper(-1)
            }
        }

        binding?.fab0?.setOnClickListener {
            val wallpaperEditBinding = WallpaperEditBinding.inflate(layoutInflater)
            wallpaperEditBinding.root.setBackgroundColor(Color.TRANSPARENT)

            wallpaperEditBinding.saturationSlider.value = requireArguments().getFloat(BundleConstants.SATURATION_VALUE, 0.5F)

            wallpaperEditBinding.saturationSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requireArguments().putFloat(BundleConstants.SATURATION_VALUE, value)
                        binding?.composeView?.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(ColorMatrix().apply {
                            setSaturation(value.toSaturation())
                        })))
                    }
                }
            }

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(wallpaperEditBinding.root)
                .show()

            dialog.window?.setDimAmount(0F)
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog)

            dialog.show()
        }

        /**
         * Remove the edit button if the device is not running
         * on Android 12 or above
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            binding?.fab0?.visibility = View.VISIBLE
        } else {
            binding?.fab0?.visibility = View.GONE
        }
    }

    private fun setWallpaper(mode: Int) {
        val loader = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.preparing)
            .setMessage(getString(R.string.copying))
            .show()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            kotlin.runCatching {
                val wallpaperManager = WallpaperManager.getInstance(requireContext())

                val bitmap = if (MainPreferences.getAppEngine()) {
                    binding?.composeView?.drawToBitmap()
                        ?.applySaturation(requireArguments().getFloat(BundleConstants.SATURATION_VALUE, 0.5F)
                                              .toSaturation())
                } else {
                    prepareFinalBitmap()
                        .applySaturation(requireArguments().getFloat(BundleConstants.SATURATION_VALUE, 0.5F)
                                             .toSaturation())
                }

                if (MainPreferences.getAppEngine().invert()) {
                    uri = bitmap?.let { getImageUri(it) }
                }

                withContext(Dispatchers.Main) {
                    kotlin.runCatching {
                        if (MainPreferences.getAppEngine()) {
                            wallpaperManager.setBitmap(bitmap, null, true, mode)
                        } else {
                            wallpaperManager.getCropAndSetWallpaperIntent(uri).run {
                                startActivity(this)
                            }
                        }
                        loader.dismiss()
                    }.onFailure {
                        loader.dismiss()
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.error)
                            .setMessage(it.message)
                            .setPositiveButton(R.string.close) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }
                }
            }.onFailure {
                it.printStackTrace()
                withContext(Dispatchers.Main) {
                    loader.dismiss()
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.error)
                        .setMessage(it.message ?: it.stackTraceToString())
                        .setPositiveButton(R.string.close) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            }
        }
    }

    private fun prepareFinalBitmap(): Bitmap {
        val bitmap = this.bitmap?.copy(this.bitmap!!.config, true)
        return bitmap!!
    }

    private fun getImageUri(inImage: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        val path = requireContext().filesDir?.absolutePath + "/" + "temp.png"

        // Copy the bitmap to the path
        val file = File(path)
        file.createNewFile()
        val fileOutputStream = file.outputStream()
        fileOutputStream.write(bytes.toByteArray())
        fileOutputStream.close()

        return FileProvider.getUriForFile(
                requireContext(),
                requireContext().applicationContext.packageName + ".provider", File(path))
    }

    private inline fun <T : View> T.afterMeasured(crossinline function: T.() -> Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    function()
                }
            }
        })
    }

    /**
     * Function that takes a Float input from 0.0F to 1.0F
     * and returns a Float from -255.0F to 255.0F depending
     * on the input
     */
    private fun Float.toBrightness(): Float {
        return (this - 0.5F) * 510
    }

    private fun Float.toSaturation(): Float {
        return this * 2
    }

    private fun Float.toContrast(): Float {
        return this * 10
    }

    /**
     * Making the Navigation system bar not overlapping with the activity
     */
    private fun fixNavigationBarOverlap() {
        ViewCompat.setOnApplyWindowInsetsListener(binding?.fab!!) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            if (binding?.fab?.marginBottom!! < insets.bottom) {
                binding?.fab?.apply {
                    layoutParams = (layoutParams as FrameLayout.LayoutParams).apply {
                        leftMargin += insets.left
                        rightMargin += insets.right
                        topMargin += insets.top
                        bottomMargin += insets.bottom
                    }
                }
            }

            /**
             * Return CONSUMED if you don't want want the window insets to keep being
             * passed down to descendant views.
             */
            WindowInsetsCompat.CONSUMED
        }
    }

    companion object {
        fun newInstance(wallpaper: Wallpaper): WallpaperScreen {
            val args = Bundle()
            args.putParcelable(BundleConstants.WALLPAPER, wallpaper)
            val fragment = WallpaperScreen()
            fragment.arguments = args
            return fragment
        }
    }
}