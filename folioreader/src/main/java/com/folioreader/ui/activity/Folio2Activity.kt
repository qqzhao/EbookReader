package com.folioreader.ui.activity

import android.graphics.Color
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.Menu
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.folioreader.Config
import com.folioreader.Constants
import com.folioreader.R
import com.folioreader.model.locators.ReadLocator
import com.folioreader.model.locators.SearchLocator
import com.folioreader.ui.adapter.FolioPageFragmentAdapter
import com.folioreader.ui.fragment.MediaControllerFragment
import com.folioreader.ui.view.DirectionalViewpager
import com.folioreader.ui.view.FolioAppBarLayout
import com.folioreader.util.AppUtil
import com.folioreader.util.UiUtil
import com.folioreader.viewmodels.PageTrackerViewModel
import org.readium.r2.shared.Link
import org.readium.r2.streamer.parser.PubBox
import org.readium.r2.streamer.server.Server

class Folio2Activity : AppCompatActivity() {

    private var bookFileNameReal: String? = null
    val bookFileName: String? get() = bookFileNameReal?.md5();

    private var mFolioPageViewPager: DirectionalViewpager? = null
    private var actionBar: ActionBar? = null
    private var appBarLayout: FolioAppBarLayout? = null
    private var toolbar: Toolbar? = null
    private var createdMenu: Menu? = null
    private var distractionFreeMode: Boolean = true
    private var handler: Handler? = null

    private var currentChapterIndex: Int = 0
    private var mFolioPageFragmentAdapter: FolioPageFragmentAdapter? = null
    private var entryReadLocator: ReadLocator? = null
    private var lastReadLocator: ReadLocator? = null
    private var bookmarkReadLocator: ReadLocator? = null

    private var outState: Bundle? = null
    private var savedInstanceState: Bundle? = null

    private var r2StreamerServer: Server? = null
    private var pubBox: PubBox? = null
    private var spine: List<Link>? = null

    private var mBookId: String? = null
    private var mEpubFilePath: String? = null
    private var mEpubSourceType: FolioActivity.EpubSourceType? = null
    private var mEpubRawId = 0
    private var mediaControllerFragment: MediaControllerFragment? = null
    private var direction: Config.Direction = Config.Direction.VERTICAL
    private var portNumber: Int = Constants.DEFAULT_PORT_NUMBER
    private var streamerUri: Uri? = null

    private var searchUri: Uri? = null
    private var searchAdapterDataBundle: Bundle? = null
    private var searchQuery: CharSequence? = null
    private var searchLocator: SearchLocator? = null

    private var displayMetrics: DisplayMetrics? = null
    private var density: Float = 0.toFloat()
    private var topActivity: Boolean? = null
    private var taskImportance: Int = 0

    private val statusBarHeight: Int
        get() {
            var result = 0
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) result = resources.getDimensionPixelSize(resourceId)
            return result
        }

    // page count
    private lateinit var pageTrackerViewModel: PageTrackerViewModel


    override fun onCreate(savedInstanceState: Bundle?) {

        setContentView(R.layout.folio_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        super.onCreate(savedInstanceState)

        initActionBar()
    }

    private fun initActionBar() {
        appBarLayout = findViewById(R.id.appBarLayout)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        actionBar = supportActionBar

        val config = AppUtil.getSavedConfig(applicationContext)!!

        val drawable = ContextCompat.getDrawable(this, R.drawable.ic_drawer)
        UiUtil.setColorIntToDrawable(config.currentThemeColor, drawable!!)
        toolbar!!.navigationIcon = drawable

        toolbar!!.setBackgroundColor(Color.RED)
//        if (config.isNightMode) {
//            setNightMode()
//        } else {
//            setDayMode()
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val color: Int = if (config.isNightMode) {
                ContextCompat.getColor(this, R.color.black)
            } else {
                val attrs = intArrayOf(android.R.attr.navigationBarColor)
                val typedArray = theme.obtainStyledAttributes(attrs)
                typedArray.getColor(0, ContextCompat.getColor(this, R.color.white))
            }
            window.navigationBarColor = color
        }

        if (Build.VERSION.SDK_INT < 16) {
            // Fix for appBarLayout.fitSystemWindows() not being called on API < 16
            appBarLayout!!.setTopMargin(statusBarHeight)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu);
        try {
            createdMenu = menu
            menuInflater.inflate(R.menu.menu_main, menu)

            val config = AppUtil.getSavedConfig(applicationContext)!!

//            toolbar?.getOverflowIcon()?.setColorFilter(config.currentThemeColor, PorterDuff.Mode.SRC_ATOP);
//            for (i in 0 until menu.size()) {
//                val drawable: Drawable = menu.getItem(i).getIcon()
//                if (drawable != null) {
//                    drawable.mutate()
//                    drawable.setColorFilter(
//                        config.currentThemeColor,
//                        PorterDuff.Mode.SRC_ATOP
//                    )
//                }
//            }


            UiUtil.setColorIntToDrawable(
                config.currentThemeColor, menu.findItem(R.id.itemBookmark).icon
            )
            UiUtil.setColorIntToDrawable(
                config.currentThemeColor, menu.findItem(R.id.itemSearch).icon
            )
            UiUtil.setColorIntToDrawable(
                config.currentThemeColor, menu.findItem(R.id.itemConfig).icon
            )
            UiUtil.setColorIntToDrawable(config.currentThemeColor, menu.findItem(R.id.itemTts).icon)

            if (!config.isShowTts) menu.findItem(R.id.itemTts).isVisible = false
        } catch (e: Exception) {
            Log.e("FOLIOREADER", e.message.toString())
        }

        return true
    }


    fun toggleSystemUI() {
        if (distractionFreeMode) {
            showSystemUI()
        } else {
            hideSystemUI()
        }
    }

    private fun showSystemUI() {
        Log.v(FolioActivity.LOG_TAG, "-> showSystemUI")

        if (Build.VERSION.SDK_INT >= 16) {
            val decorView = window.decorView
            decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            if (appBarLayout != null) appBarLayout!!.setTopMargin(statusBarHeight)
            onSystemUiVisibilityChange(View.SYSTEM_UI_FLAG_VISIBLE)
        }
    }

    private fun hideSystemUI() {
        Log.v(FolioActivity.LOG_TAG, "-> hideSystemUI")

        if (Build.VERSION.SDK_INT >= 16) {
            val decorView = window.decorView
            decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                    // Set the content to appear under the system bars so that the
                    // content doesn't resize when the system bars hide and show.
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    // Hide the nav bar and status bar
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            )
            // Specified 1 just to mock anything other than View.SYSTEM_UI_FLAG_VISIBLE
            onSystemUiVisibilityChange(1)
        }
    }

    fun onSystemUiVisibilityChange(visibility: Int) {
        Log.v(FolioActivity.LOG_TAG, "-> onSystemUiVisibilityChange -> visibility = $visibility")

        distractionFreeMode = visibility != View.SYSTEM_UI_FLAG_VISIBLE
        Log.v(FolioActivity.LOG_TAG, "-> distractionFreeMode = $distractionFreeMode")

        if (actionBar != null) {
            if (distractionFreeMode) {
                actionBar!!.hide()
            } else {
                actionBar!!.show()
            }
        }
    }
}