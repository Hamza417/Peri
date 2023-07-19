package app.simple.waller.ui

import android.app.WallpaperManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import app.simple.waller.R
import app.simple.waller.constants.BundleConstants
import app.simple.waller.databinding.FragmentWallpaperScreenBinding
import app.simple.waller.glide.utils.GlideUtils.loadWallpaper
import app.simple.waller.models.Wallpaper
import app.simple.waller.utils.FileUtils.toUri
import app.simple.waller.utils.ParcelUtils.parcelable
import com.google.android.material.transition.MaterialContainerTransform

class WallpaperScreen : Fragment() {

    private var binding: FragmentWallpaperScreenBinding? = null
    private var wallpaper: Wallpaper? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = FragmentWallpaperScreenBinding.inflate(inflater, container, false)

        wallpaper = requireArguments().parcelable(BundleConstants.WALLPAPER)
        binding?.wallpaper?.transitionName = wallpaper?.uri

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        sharedElementEnterTransition = MaterialContainerTransform().apply {
            duration = resources.getInteger(R.integer.animation_duration).toLong()
            setAllContainerColors(Color.TRANSPARENT)
            scrimColor = Color.TRANSPARENT
        }

        binding?.wallpaper?.loadWallpaper(wallpaper!!) {
            /**
             * Scroll to center when wallpaper is loaded
             */
            binding?.wallpaper?.doOnLayout {
                binding?.wallpaperScrollView?.afterMeasured {
                    val scrollTo = binding?.wallpaper?.width?.div(2)?.minus(binding?.wallpaperScrollView?.width?.div(2)!!)
                    if (scrollTo != null) {
                        scrollTo(scrollTo, 0)
                    }
                }
            }

            startPostponedEnterTransition()
        }

        binding?.setAsWallpaper?.setOnClickListener {
            val intent = WallpaperManager.getInstance(requireContext()).getCropAndSetWallpaperIntent(wallpaper?.uri?.toUri())
            startActivity(intent)
        }
    }

    private inline fun <T : View> T.afterMeasured(crossinline f: T.() -> Unit) {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (measuredWidth > 0 && measuredHeight > 0) {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    f()
                }
            }
        })
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