package app.simple.peri.ui

import android.animation.Animator
import android.animation.Animator.AnimatorListener
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import app.simple.peri.R
import app.simple.peri.constants.BundleConstants
import app.simple.peri.databinding.FragmentWallpaperScreenBinding
import app.simple.peri.databinding.WallpaperEditBinding
import app.simple.peri.glide.utils.GlideUtils.loadWallpaper
import app.simple.peri.models.Wallpaper
import app.simple.peri.tools.StackBlur
import app.simple.peri.utils.BitmapUtils.changeBitmapContrastBrightness
import app.simple.peri.utils.ParcelUtils.parcelable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.google.android.material.slider.Slider.OnSliderTouchListener
import com.google.android.material.transition.MaterialContainerTransform
import com.google.android.material.transition.MaterialFadeThrough
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

class WallpaperScreen : Fragment() {

    private var binding: FragmentWallpaperScreenBinding? = null
    private var wallpaper: Wallpaper? = null

    private var bitmap: Bitmap? = null
    private var uri: Uri? = null
    private val blurRadius = 150F

    private var currentBlurValue = 0F
    private var currentBrightnessValue = 0.5F
    private var currentContrastValue = 0.1F
    private var currentSaturationValue = 0.5F

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentWallpaperScreenBinding.inflate(inflater, container, false)

        wallpaper = requireArguments().parcelable(BundleConstants.WALLPAPER)
        binding?.wallpaper?.transitionName = wallpaper?.uri

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().findViewById<CoordinatorLayout>(R.id.mainContainer).setBackgroundColor(Color.BLACK)
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

        binding?.wallpaper?.loadWallpaper(wallpaper!!) {
            bitmap = it

            /**
             * Scroll to center when wallpaper is loaded
             */
            binding?.wallpaper?.doOnLayout {
                binding?.wallpaperScrollView?.afterMeasured {
                    val scrollTo = binding?.wallpaper?.width?.div(2)
                        ?.minus(binding?.wallpaperScrollView?.width?.div(2)!!)

                    if (scrollTo != null) {
                        scrollTo(scrollTo, 0)
                    }
                }
            }

            startPostponedEnterTransition()

            binding?.blurSliderContainer?.animate()
                ?.alpha(1f)
                ?.setInterpolator(DecelerateInterpolator(1.5F))
                ?.setDuration(resources.getInteger(R.integer.animation_duration).toLong())
                ?.setStartDelay(resources.getInteger(R.integer.animation_duration).toLong())
                ?.setListener(object : AnimatorListener {
                    override fun onAnimationStart(p0: Animator) {
                        binding?.blurSliderContainer?.visibility = View.VISIBLE
                    }

                    override fun onAnimationEnd(p0: Animator) {
                    }

                    override fun onAnimationCancel(p0: Animator) {
                    }

                    override fun onAnimationRepeat(p0: Animator) {
                    }

                })
                ?.start()
        }

        binding?.edit?.setOnClickListener {
            val wallpaperEditBinding = WallpaperEditBinding.inflate(layoutInflater)

            if (currentBlurValue > 0) {
                wallpaperEditBinding.blurSlider.value = currentBlurValue
            } else {
                wallpaperEditBinding.blurSlider.value = 0F
            }

            if (currentBrightnessValue > 0) {
                wallpaperEditBinding.brightnessSlider.value = currentBrightnessValue
            } else {
                wallpaperEditBinding.brightnessSlider.value = 0.5F
            }

            if (currentContrastValue > 0) {
                wallpaperEditBinding.contrastSlider.value = currentContrastValue
            } else {
                wallpaperEditBinding.contrastSlider.value = 0.1F
            }

            if (currentSaturationValue > 0) {
                wallpaperEditBinding.saturationSlider.value = currentSaturationValue
            } else {
                wallpaperEditBinding.saturationSlider.value = 0.5F
            }

            wallpaperEditBinding.blurSlider.addOnChangeListener { _, value, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    currentBlurValue = value
                    val blurRadius = this.blurRadius * 10
                    try {
                        binding?.wallpaper?.setRenderEffect(
                                RenderEffect
                                    .createBlurEffect(value * blurRadius, value * blurRadius, Shader.TileMode.CLAMP))
                    } catch (e: IllegalArgumentException) {
                        binding?.wallpaper?.setRenderEffect(null)
                    }
                }
            }

            wallpaperEditBinding.blurSlider.addOnSliderTouchListener(object : OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        binding?.wallpaper?.setRenderEffect(null)
                    }

                    binding?.wallpaper?.setImageBitmap(
                            bitmap?.changeBitmapContrastBrightness(
                                    currentContrastValue.toContrast(),
                                    currentBrightnessValue.toBrightness(),
                                    currentSaturationValue.toSaturation()))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val blurRadius = this@WallpaperScreen.blurRadius * 10
                        try {
                            binding?.wallpaper?.setRenderEffect(
                                    RenderEffect
                                        .createBlurEffect(
                                                wallpaperEditBinding.blurSlider.value * blurRadius,
                                                wallpaperEditBinding.blurSlider.value * blurRadius, Shader.TileMode.CLAMP))
                        } catch (e: IllegalArgumentException) {
                            binding?.wallpaper?.setRenderEffect(null)
                        }
                    }
                }

                override fun onStopTrackingTouch(slider: Slider) {
                    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            if (wallpaperEditBinding.blurSlider.value > 0) {
                                val bitmap = prepareFinalBitmap()

                                withContext(Dispatchers.Main) {
                                    binding?.wallpaper?.setImageBitmap(bitmap)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        binding?.wallpaper?.setImageBitmap(bitmap)

                                        val cm = ColorMatrix(floatArrayOf(
                                                currentContrastValue.toContrast(), 0f, 0f, 0f, currentBrightnessValue.toBrightness(),
                                                0f, currentContrastValue.toContrast(), 0f, 0f, currentBrightnessValue.toBrightness(),
                                                0f, 0f, currentContrastValue.toContrast(), 0f, currentBrightnessValue.toBrightness(),
                                                0f, 0f, 0f, 1f, 0f
                                        ))

                                        cm.postConcat(ColorMatrix().apply {
                                            setSaturation(currentSaturationValue.toSaturation())
                                        })

                                        binding?.wallpaper?.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm)))
                                    } else {
                                        binding?.wallpaper?.setImageBitmap(
                                                bitmap?.changeBitmapContrastBrightness(
                                                        currentContrastValue.toContrast(),
                                                        currentBrightnessValue.toBrightness(),
                                                        currentSaturationValue.toSaturation()))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                binding?.wallpaper?.setImageBitmap(this@WallpaperScreen.bitmap)
                            }
                        }
                    }
                }
            })

            wallpaperEditBinding.brightnessSlider.addOnChangeListener { _, value, _ ->
                currentBrightnessValue = value

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cm = ColorMatrix(floatArrayOf(
                            currentContrastValue.toContrast(), 0f, 0f, 0f, value.toBrightness(),
                            0f, currentContrastValue.toContrast(), 0f, 0f, value.toBrightness(),
                            0f, 0f, currentContrastValue.toContrast(), 0f, value.toBrightness(),
                            0f, 0f, 0f, 1f, 0f
                    ))

                    cm.postConcat(ColorMatrix().apply {
                        setSaturation(currentSaturationValue.toSaturation())
                    })

                    binding?.wallpaper?.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm)))
                } else {
                    binding?.wallpaper?.setImageBitmap(
                            bitmap?.changeBitmapContrastBrightness(
                                    currentContrastValue.toContrast(), value.toBrightness(), currentSaturationValue.toSaturation()))
                }
            }

            wallpaperEditBinding.contrastSlider.addOnChangeListener { _, value, _ ->
                currentContrastValue = value
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cm = ColorMatrix(floatArrayOf(
                            value.toContrast(), 0f, 0f, 0f, currentBrightnessValue.toBrightness(),
                            0f, value.toContrast(), 0f, 0f, currentBrightnessValue.toBrightness(),
                            0f, 0f, value.toContrast(), 0f, currentBrightnessValue.toBrightness(),
                            0f, 0f, 0f, 1f, 0f
                    ))

                    cm.postConcat(ColorMatrix().apply {
                        setSaturation(currentSaturationValue.toSaturation())
                    })

                    binding?.wallpaper?.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm)))
                } else {
                    binding?.wallpaper?.setImageBitmap(
                            bitmap?.changeBitmapContrastBrightness(
                                    value.toContrast(), currentBrightnessValue.toBrightness(), currentSaturationValue.toSaturation()))
                }
            }

            wallpaperEditBinding.saturationSlider.addOnChangeListener { _, value, _ ->
                currentSaturationValue = value
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val cm = ColorMatrix(floatArrayOf(
                            currentContrastValue.toContrast(), 0f, 0f, 0f, currentBrightnessValue.toBrightness(),
                            0f, currentContrastValue.toContrast(), 0f, 0f, currentBrightnessValue.toBrightness(),
                            0f, 0f, currentContrastValue.toContrast(), 0f, currentBrightnessValue.toBrightness(),
                            0f, 0f, 0f, 1f, 0f
                    ))

                    cm.postConcat(ColorMatrix().apply {
                        setSaturation(value.toSaturation())
                    })

                    binding?.wallpaper?.setRenderEffect(RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(cm)))
                } else {
                    binding?.wallpaper?.setImageBitmap(
                            bitmap?.changeBitmapContrastBrightness(
                                    currentContrastValue.toContrast(), currentBrightnessValue.toBrightness(), value.toSaturation()))
                }
            }

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(wallpaperEditBinding.root)
                .show()

            dialog.window?.setDimAmount(0F)
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                dialog.window?.setBackgroundBlurRadius(25)
            }
        }

        binding?.setAsWallpaper?.setOnClickListener {
            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.preparing)
                .setMessage(getString(R.string.copying))
                .show()

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val wallpaperManager = WallpaperManager.getInstance(requireContext())
                val bitmap = prepareFinalBitmap()
                uri = getImageUri(bitmap)

                withContext(Dispatchers.Main) {
                    wallpaperManager.getCropAndSetWallpaperIntent(uri).let {
                        dialog.dismiss()
                        startActivity(it)
                    }
                }
            }
        }
    }

    private fun prepareFinalBitmap(): Bitmap {
        val bitmap = this.bitmap
            ?.copy(this.bitmap!!.config, true)
            ?.changeBitmapContrastBrightness(
                    currentContrastValue.toContrast(), currentBrightnessValue.toBrightness(), currentSaturationValue.toSaturation())

        val blurRadius = currentBlurValue * this.blurRadius

        try {
            StackBlur().blurRgb(bitmap!!, blurRadius.roundToInt())
        } catch (e: Exception) {
            // baa baa black sheep
        }

        return bitmap!!
    }

    private fun getImageUri(inImage: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.PNG, 100, bytes)
        val path = requireContext().filesDir?.absolutePath + "/" + "temp.png"

        // Copy the bitmap to the path
        val file = File(path)
        file.createNewFile()
        val fo = file.outputStream()
        fo.write(bytes.toByteArray())
        fo.close()

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