package com.longluo.viewer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.longluo.viewer.ReaderView.ViewMapper
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.*

class DocumentActivity : Activity() {
    /* The core rendering instance */
    internal enum class TopBarMode {
        Main, Search, More
    }

    private val OUTLINE_REQUEST = 0
    private val PERMISSION_REQUEST = 0
    private var core: MuPDFCore? = null
    private var mFileName: String? = null
    private var mFileKey: String? = null
    private var mDocView: ReaderView? = null
    private var mButtonsView: View? = null
    private var mButtonsVisible = false
    private var mPasswordView: EditText? = null
    private var mFilenameView: TextView? = null
    private var mPageSlider: SeekBar? = null
    private var mPageSliderRes = 0
    private var mPageNumberView: TextView? = null
    private var mSearchButton: ImageButton? = null
    private var mOutlineButton: ImageButton? = null
    private var mTopBarSwitcher: ViewAnimator? = null
    private var mLinkButton: ImageButton? = null
    private var mBackButton: ImageButton? = null
    private var mTopBarMode = TopBarMode.Main
    private var mSearchBack: ImageButton? = null
    private var mSearchFwd: ImageButton? = null
    private var mSearchClose: ImageButton? = null
    private var mSearchText: EditText? = null
    private var mSearchTask: SearchTask? = null
    private var mAlertBuilder: AlertDialog.Builder? = null
    private var mLinkHighlight = false
    private val mHandler = Handler()
    private val mAlertsActive = false
    private val mAlertDialog: AlertDialog? = null
    private var mFlatOutline: ArrayList<OutlineActivity.Item>? = null
    protected var mDisplayDPI = 0

    private var mLayoutEM = 10 // 字体大小
    private var mLayoutW = 312
    private var mLayoutH = 504
    private var lastPageNum = 0 // 上次看到的页数

    protected var mLayoutButton: View? = null
    protected var mLayoutPopupMenu: PopupMenu? = null
    private fun toHex(digest: ByteArray): String {
        val builder = StringBuilder(2 * digest.size)
        for (b in digest) builder.append(String.format("%02x", b))
        return builder.toString()
    }

    private fun println(str: String?) {
        Log.d("XXX", "$str");
    }

    private fun openFile(path: String?): MuPDFCore? {
        val lastSlashPos: Int = path!!.lastIndexOf("/") ?: 0;
        mFileName = if (lastSlashPos == -1) path else path.substring(lastSlashPos + 1)
        println("Trying to open $path")
        try {
            mFileKey = mFileName
            core = MuPDFCore(path)
        } catch (e: Exception) {
            println("$e")
            return null
        } catch (e: OutOfMemoryError) {
            //  out of memory is not an Exception, so we catch it separately.
            println("$e")
            return null
        }
        return core
    }

    private fun openBuffer(buffer: ByteArray?, magic: String?): MuPDFCore? {
        println("Trying to open byte buffer")
        try {
            mFileKey = toHex(MessageDigest.getInstance("MD5").digest(buffer))
            core = MuPDFCore(buffer, magic)
        } catch (e: Exception) {
            println("$e")
            return null
        }
        return core
    }

    /**
     * Called when the activity is first created.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mDisplayDPI = metrics.densityDpi
        mAlertBuilder = AlertDialog.Builder(this)
        if (core == null) {
            if (savedInstanceState != null && savedInstanceState.containsKey("FileName")) {
                mFileName = savedInstanceState.getString("FileName")
            }
        }
        if (core == null) {
            val intent = intent
            var buffer: ByteArray? = null
            if (Intent.ACTION_VIEW == intent.action) {
                val uri = intent.data
                println("URI to open is: $uri")
                if (uri!!.scheme == "file") {
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_DENIED
                    ) ActivityCompat.requestPermissions(
                        this, arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        ), PERMISSION_REQUEST
                    )
                    val path = uri.path
                    core = openFile(path)
                } else {
                    try {
                        val `is` = contentResolver.openInputStream(uri)
                        var len: Int = 0
                        val bufferStream = ByteArrayOutputStream()
                        val data = ByteArray(16384)
                        while (`is`!!.read(data, 0, data.size).also { len = it } != -1) {
                            bufferStream.write(data, 0, len)
                        }
                        bufferStream.flush()
                        buffer = bufferStream.toByteArray()
                        `is`.close()
                    } catch (e: IOException) {
                        val reason = e.toString()
                        val res = resources
                        val alert = mAlertBuilder!!.create()
                        val text = "${Locale.ROOT}${res.getString(R.string.cannot_open_document_Reason)}$reason";
                        setTitle(text);
                        alert.setButton(
                            AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss)
                        ) { dialog, which -> finish() }
                        alert.show()
                        return
                    }
                    core = openBuffer(buffer, intent.type)
                }
                SearchTaskResult.set(null)
            }
            if (core != null && core!!.needsPassword()) {
                requestPassword(savedInstanceState)
                return
            }
            if (core != null && core!!.countPages() == 0) {
                core = null
            }
        }
        if (core == null) {
            val alert = mAlertBuilder!!.create()
            alert.setTitle(R.string.cannot_open_document)
            alert.setButton(
                AlertDialog.BUTTON_POSITIVE, getString(R.string.dismiss)
            ) { dialog, which -> finish() }
            alert.setOnCancelListener { finish() }
            alert.show()
            return
        }
        createUI(savedInstanceState)
    }

    fun requestPassword(savedInstanceState: Bundle?) {
        mPasswordView = EditText(this)
        mPasswordView!!.inputType = EditorInfo.TYPE_TEXT_VARIATION_PASSWORD
        mPasswordView!!.transformationMethod = PasswordTransformationMethod()
        val alert = mAlertBuilder!!.create()
        alert.setTitle(R.string.enter_password)
        alert.setView(mPasswordView)
        alert.setButton(
            AlertDialog.BUTTON_POSITIVE, getString(R.string.okay)
        ) { dialog, which ->
            if (core!!.authenticatePassword(mPasswordView!!.text.toString())) {
                createUI(savedInstanceState)
            } else {
                requestPassword(savedInstanceState)
            }
        }
        alert.setButton(
            AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel)
        ) { dialog, which -> finish() }
        alert.show()
    }

    fun relayoutDocument() {
        val loc = core!!.layout(mDocView!!.mCurrent, mLayoutW, mLayoutH, mLayoutEM)
        mFlatOutline = null
        mDocView!!.mHistory.clear()
        mDocView!!.refresh()
        mDocView!!.displayedViewIndex = loc
    }

    private val pageStoreKey get() = "page$mFileKey"
    private val pageFontSizeStoreKey get() = "font_size_$mFileKey"
    private fun storePref() {
        if (mFileKey != null && mDocView != null) {
            val prefs = getPreferences(MODE_PRIVATE)
            val edit = prefs.edit()
            edit.putInt(pageStoreKey, mDocView!!.displayedViewIndex)
            edit.putInt(pageFontSizeStoreKey, mLayoutEM)
            edit.apply()
        }
    }

    private fun restorePref() {
        val prefs = getPreferences(MODE_PRIVATE)
        mLayoutEM = prefs.getInt(pageFontSizeStoreKey, 10);
        lastPageNum = prefs.getInt(pageStoreKey, 0);
//        mDocView!!.setDisplayedViewIndex(lastPageNum)
    }

    override fun onPause() {
        super.onPause()
        if (mSearchTask != null) mSearchTask!!.stop()
        storePref()
    }

    fun createUI(savedInstanceState: Bundle?) {
        if (core == null) return

        // Now create the UI.
        // First create the document view
        mDocView = object : ReaderView(this) {
            override fun onMoveToChild(i: Int) {
                if (core == null) return
                val text = "${Locale.ROOT}${i+1} / ${core!!.countPages()}"
                mPageNumberView?.text = text
                mPageSlider!!.max = (core!!.countPages() - 1) * mPageSliderRes
                mPageSlider!!.progress = i * mPageSliderRes
                super.onMoveToChild(i)
            }

            override fun onTapMainDocArea() {
                if (!mButtonsVisible) {
                    showButtons()
                } else {
                    if (mTopBarMode == TopBarMode.Main) hideButtons()
                }
            }

            override fun onDocMotion() {
                hideButtons()
            }

            public override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                if (core!!.isReflowable) {
                    mLayoutW = w * 72 / mDisplayDPI
                    mLayoutH = h * 72 / mDisplayDPI
                    relayoutDocument()
                    mDocView!!.displayedViewIndex = lastPageNum
                } else {
                    refresh()
                }
            }
        }
        mDocView!!.setAdapter(PageAdapter(this, core))
        mSearchTask = object : SearchTask(this, core) {
            override fun onTextFound(result: SearchTaskResult) {
                SearchTaskResult.set(result)
                // Ask the ReaderView to move to the resulting page
                mDocView!!.setDisplayedViewIndex(result.pageNumber)
                // Make the ReaderView act on the change to SearchTaskResult
                // via overridden onChildSetup method.
                mDocView!!.resetupChildren()
            }
        }

        // Make the buttons overlay, and store all its
        // controls in variables
        makeButtonsView()

        // Set up the page slider
        val smax = Math.max(core!!.countPages() - 1, 1)
        mPageSliderRes = (10 + smax - 1) / smax * 2

        // Set the file-name text
        val docTitle = core!!.title
        if (docTitle != null) mFilenameView!!.text = docTitle else mFilenameView!!.text = mFileName

        // Activate the seekbar
        mPageSlider!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                mDocView!!.pushHistory()
                mDocView!!.setDisplayedViewIndex((seekBar.progress + mPageSliderRes / 2) / mPageSliderRes)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                updatePageNumView((progress + mPageSliderRes / 2) / mPageSliderRes)
            }
        })

        // Activate the search-preparing button
        mSearchButton!!.setOnClickListener { searchModeOn() }
        mSearchClose!!.setOnClickListener { searchModeOff() }

        // Search invoking buttons are disabled while there is no text specified
        mSearchBack!!.isEnabled = false
        mSearchFwd!!.isEnabled = false
        mSearchBack!!.setColorFilter(Color.argb(255, 128, 128, 128))
        mSearchFwd!!.setColorFilter(Color.argb(255, 128, 128, 128))

        // React to interaction with the text widget
        mSearchText!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                val haveText = s.toString().length > 0
                setButtonEnabled(mSearchBack, haveText)
                setButtonEnabled(mSearchFwd, haveText)

                // Remove any previous search results
                if (SearchTaskResult.get() != null && mSearchText!!.text.toString() != SearchTaskResult.get().txt) {
                    SearchTaskResult.set(null)
                    mDocView!!.resetupChildren()
                }
            }

            override fun beforeTextChanged(
                s: CharSequence, start: Int, count: Int,
                after: Int
            ) {

            }

            override fun onTextChanged(
                s: CharSequence, start: Int, before: Int,
                count: Int
            ) {
            }
        })

        //React to Done button on keyboard
        mSearchText!!.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) search(1)
            false
        }
        mSearchText!!.setOnKeyListener { v, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) search(
                1
            )
            false
        }

        // Activate search invoking buttons
        mSearchBack!!.setOnClickListener { search(-1) }
        mSearchFwd!!.setOnClickListener { search(1) }
        mLinkButton!!.setOnClickListener { setLinkHighlight(!mLinkHighlight) }
        mBackButton!!.setOnClickListener { finish() }
        if (core!!.isReflowable) {
            mLayoutButton!!.visibility = View.VISIBLE
            mLayoutPopupMenu = PopupMenu(this, mLayoutButton)
            mLayoutPopupMenu!!.menuInflater.inflate(R.menu.layout_menu, mLayoutPopupMenu!!.menu)
            mLayoutPopupMenu!!.setOnMenuItemClickListener { item ->
                val oldLayoutEM = mLayoutEM.toFloat()
                val id = item.itemId
                if (id == R.id.action_layout_6pt) mLayoutEM =
                    6 else if (id == R.id.action_layout_7pt) mLayoutEM =
                    7 else if (id == R.id.action_layout_8pt) mLayoutEM =
                    8 else if (id == R.id.action_layout_9pt) mLayoutEM =
                    9 else if (id == R.id.action_layout_10pt) mLayoutEM =
                    10 else if (id == R.id.action_layout_11pt) mLayoutEM =
                    11 else if (id == R.id.action_layout_12pt) mLayoutEM =
                    12 else if (id == R.id.action_layout_13pt) mLayoutEM =
                    13 else if (id == R.id.action_layout_14pt) mLayoutEM =
                    14 else if (id == R.id.action_layout_15pt) mLayoutEM =
                    15 else if (id == R.id.action_layout_16pt) mLayoutEM = 16
                if (oldLayoutEM != mLayoutEM.toFloat()) relayoutDocument()
                true
            }
            mLayoutButton!!.setOnClickListener { mLayoutPopupMenu!!.show() }
        }
        if (core!!.hasOutline()) {
            mOutlineButton!!.setOnClickListener {
                if (mFlatOutline == null) mFlatOutline = core!!.outline
                if (mFlatOutline != null) {
                    val intent = Intent(this@DocumentActivity, OutlineActivity::class.java)
                    val bundle = Bundle()
                    bundle.putInt("POSITION", mDocView!!.getDisplayedViewIndex())
                    bundle.putSerializable("OUTLINE", mFlatOutline)
                    intent.putExtras(bundle)
                    startActivityForResult(intent, OUTLINE_REQUEST)
                }
            }
        } else {
            mOutlineButton!!.visibility = View.GONE
        }

        // Reenstate last state if it was recorded
        restorePref()
        if (savedInstanceState == null || !savedInstanceState.getBoolean(
                "ButtonsHidden",
                false
            )
        ) showButtons()
        if (savedInstanceState != null && savedInstanceState.getBoolean(
                "SearchMode",
                false
            )
        ) searchModeOn()

        // Stick the document view and the buttons overlay into a parent view
        val layout = RelativeLayout(this)
        layout.setBackgroundColor(Color.DKGRAY)
        layout.addView(mDocView)
        layout.addView(mButtonsView)
        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            OUTLINE_REQUEST -> if (resultCode >= RESULT_FIRST_USER) {
                mDocView!!.pushHistory()
                mDocView!!.displayedViewIndex = resultCode - RESULT_FIRST_USER
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }



    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (mFileKey != null && mDocView != null) {
            if (mFileName != null) outState.putString("FileName", mFileName)

            storePref()
        }
        if (!mButtonsVisible) outState.putBoolean("ButtonsHidden", true)
        if (mTopBarMode == TopBarMode.Search) outState.putBoolean("SearchMode", true)
    }

    public override fun onDestroy() {
        if (mDocView != null) {
            mDocView!!.applyToChildren(object : ViewMapper() {
                public override fun applyToView(view: View) {
                    (view as PageView).releaseBitmaps()
                }
            })
        }
        if (core != null) core!!.onDestroy()
        core = null
        super.onDestroy()
    }

    private fun setButtonEnabled(button: ImageButton?, enabled: Boolean) {
        button!!.isEnabled = enabled
        button.setColorFilter(
            if (enabled) Color.argb(255, 255, 255, 255) else Color.argb(
                255,
                128,
                128,
                128
            )
        )
    }

    private fun setLinkHighlight(highlight: Boolean) {
        mLinkHighlight = highlight
        // LINK_COLOR tint
        mLinkButton!!.setColorFilter(
            if (highlight) Color.argb(
                0xFF,
                0x00,
                0x66,
                0xCC
            ) else Color.argb(0xFF, 255, 255, 255)
        )
        // Inform pages of the change.
        mDocView!!.setLinksEnabled(highlight)
    }

    private fun showButtons() {
        if (core == null) return
        if (!mButtonsVisible) {
            mButtonsVisible = true
            // Update page number text and slider
            val index = mDocView!!.displayedViewIndex
            updatePageNumView(index)
            mPageSlider!!.max = (core!!.countPages() - 1) * mPageSliderRes
            mPageSlider!!.progress = index * mPageSliderRes
            if (mTopBarMode == TopBarMode.Search) {
                mSearchText!!.requestFocus()
                showKeyboard()
            }
            var anim: Animation =
                TranslateAnimation(0f, 0f, -mTopBarSwitcher!!.height.toFloat(), 0f)
            anim.duration = 200
            anim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    mTopBarSwitcher!!.visibility = View.VISIBLE
                }

                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {}
            })
            mTopBarSwitcher!!.startAnimation(anim)
            anim = TranslateAnimation(0f, 0f, mPageSlider!!.height.toFloat(), 0f)
            anim.setDuration(200)
            anim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    mPageSlider!!.visibility = View.VISIBLE
                }

                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    mPageNumberView!!.visibility = View.VISIBLE
                }
            })
            mPageSlider!!.startAnimation(anim)
        }
    }

    private fun hideButtons() {
        if (mButtonsVisible) {
            mButtonsVisible = false
            hideKeyboard()
            var anim: Animation =
                TranslateAnimation(0f, 0f, 0f, -mTopBarSwitcher!!.height.toFloat())
            anim.duration = 200
            anim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    mTopBarSwitcher!!.visibility = View.INVISIBLE
                }
            })
            mTopBarSwitcher!!.startAnimation(anim)
            anim = TranslateAnimation(0f, 0f, 0f, mPageSlider!!.height.toFloat())
            anim.setDuration(200)
            anim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {
                    mPageNumberView!!.visibility = View.INVISIBLE
                }

                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    mPageSlider!!.visibility = View.INVISIBLE
                }
            })
            mPageSlider!!.startAnimation(anim)
        }
    }

    private fun searchModeOn() {
        if (mTopBarMode != TopBarMode.Search) {
            mTopBarMode = TopBarMode.Search
            //Focus on EditTextWidget
            mSearchText!!.requestFocus()
            showKeyboard()
            mTopBarSwitcher!!.displayedChild = mTopBarMode.ordinal
        }
    }

    private fun searchModeOff() {
        if (mTopBarMode == TopBarMode.Search) {
            mTopBarMode = TopBarMode.Main
            hideKeyboard()
            mTopBarSwitcher!!.displayedChild = mTopBarMode.ordinal
            SearchTaskResult.set(null)
            // Make the ReaderView act on the change to mSearchTaskResult
            // via overridden onChildSetup method.
            mDocView!!.resetupChildren()
        }
    }

    private fun updatePageNumView(index: Int) {
        if (core == null) return
        var text = "${Locale.ROOT}${index + 1} / ${core!!.countPages()}";
        mPageNumberView!!.text = text;
//        mPageNumberView!!.setText(
//            String.format(
//                Locale.ROOT,
//                "%d / %d",
//                index + 1,
//                core!!.countPages()
//            )
//        )
    }

    private fun makeButtonsView() {
        mButtonsView = layoutInflater.inflate(R.layout.document_activity, null)
        mFilenameView = mButtonsView!!.findViewById<View>(R.id.docNameText) as TextView
        mPageSlider = mButtonsView!!.findViewById<View>(R.id.pageSlider) as SeekBar
        mPageNumberView = mButtonsView!!.findViewById<View>(R.id.pageNumber) as TextView
        mSearchButton = mButtonsView!!.findViewById<View>(R.id.searchButton) as ImageButton
        mOutlineButton = mButtonsView!!.findViewById<View>(R.id.outlineButton) as ImageButton
        mTopBarSwitcher = mButtonsView!!.findViewById<View>(R.id.switcher) as ViewAnimator
        mSearchBack = mButtonsView!!.findViewById<View>(R.id.searchBack) as ImageButton
        mSearchFwd = mButtonsView!!.findViewById<View>(R.id.searchForward) as ImageButton
        mSearchClose = mButtonsView!!.findViewById<View>(R.id.searchClose) as ImageButton
        mSearchText = mButtonsView!!.findViewById<View>(R.id.searchText) as EditText
        mLinkButton = mButtonsView!!.findViewById<View>(R.id.linkButton) as ImageButton
        mBackButton = mButtonsView!!.findViewById<View>(R.id.backButton) as ImageButton
        mLayoutButton = mButtonsView!!.findViewById(R.id.layoutButton)
        mTopBarSwitcher!!.visibility = View.INVISIBLE
        mPageNumberView!!.visibility = View.INVISIBLE
        mPageSlider!!.visibility = View.INVISIBLE
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm?.showSoftInput(mSearchText, 0)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm?.hideSoftInputFromWindow(mSearchText!!.windowToken, 0)
    }

    private fun search(direction: Int) {
        hideKeyboard()
        val displayPage = mDocView!!.displayedViewIndex
        val r = SearchTaskResult.get()
        val searchPage = r?.pageNumber ?: -1
        mSearchTask!!.go(mSearchText!!.text.toString(), direction, displayPage, searchPage)
    }

    override fun onSearchRequested(): Boolean {
        if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
            hideButtons()
        } else {
            showButtons()
            searchModeOn()
        }
        return super.onSearchRequested()
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (mButtonsVisible && mTopBarMode != TopBarMode.Search) {
            hideButtons()
        } else {
            showButtons()
            searchModeOff()
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onBackPressed() {
        if (!mDocView!!.popHistory()) super.onBackPressed()
    }
}