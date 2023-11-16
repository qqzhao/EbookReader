package com.longluo.ebookreader.ui.activity

import android.app.Activity
import android.content.*
import android.database.SQLException
import android.graphics.Point
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.util.Log
import kotlinx.coroutines.*
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import butterknife.BindView
import butterknife.OnClick
import com.baidu.tts.chainofresponsibility.logger.LoggerProxy
import com.baidu.tts.client.SpeechError
import com.baidu.tts.client.SpeechSynthesizer
import com.baidu.tts.client.SpeechSynthesizerListener
import com.baidu.tts.client.TtsMode
import com.google.android.material.appbar.AppBarLayout
import com.longluo.ebookreader.R
import com.longluo.ebookreader.base.BaseActivity
import com.longluo.ebookreader.db.BookMark
import com.longluo.ebookreader.db.BookMeta
import com.longluo.ebookreader.manager.ReadSettingManager
import com.longluo.ebookreader.model.PageFactory
import com.longluo.ebookreader.ui.dialog.ReadSettingDialog
import com.longluo.ebookreader.ui.dialog.ReadSettingDialog.SettingListener
import com.longluo.ebookreader.util.BrightnessUtils
import com.longluo.ebookreader.widget.page.PageMode
import com.longluo.ebookreader.widget.page.PageStyle
import com.longluo.ebookreader.widget.page.PageView
import io.github.longluo.util.StringUtils
import java.io.IOException
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class ReadActivity : BaseActivity(), SpeechSynthesizerListener {
    @JvmField
    @BindView(R.id.bookpage)
    var bookpage: PageView? = null

    @JvmField
    @BindView(R.id.tv_progress)
    var tv_progress: TextView? = null

    @JvmField
    @BindView(R.id.rl_progress)
    var rl_progress: RelativeLayout? = null

    @JvmField
    @BindView(R.id.read_tv_pre_chapter)
    var mTvPreChapter: TextView? = null

    @JvmField
    @BindView(R.id.read_sb_chapter_progress)
    var mSbReadProgress: SeekBar? = null

    @JvmField
    @BindView(R.id.read_tv_next_chapter)
    var mTvNextChapter: TextView? = null

    @JvmField
    @BindView(R.id.read_tv_contents)
    var mTvBookContents: TextView? = null

    @JvmField
    @BindView(R.id.read_tv_day_night_mode)
    var mTvDayNightMode: TextView? = null

    @JvmField
    @BindView(R.id.read_tv_setting)
    var mTvReadSetting: TextView? = null

    @JvmField
    @BindView(R.id.rl_bottom)
    var rl_bottom: RelativeLayout? = null

    @JvmField
    @BindView(R.id.tv_stop_tts_read)
    var tv_stop_read: TextView? = null

    @JvmField
    @BindView(R.id.rl_read_bottom)
    var rl_read_bottom: RelativeLayout? = null

    @JvmField
    @BindView(R.id.toolbar)
    var toolbar: Toolbar? = null

    @JvmField
    @BindView(R.id.appbar)
    var appbar: AppBarLayout? = null
    private lateinit var readSettingManager: ReadSettingManager
    private val lp: WindowManager.LayoutParams? = null
    private var bookMeta: BookMeta? = null
    private lateinit var pageFactory: PageFactory
    private var screenWidth = 0
    private var screenHeight = 0
    private var lastPosition: Long = 0 // 存储上次阅读未知

    // popwindow是否显示
    private var isShow = false
    private var mSettingDialog: ReadSettingDialog? = null
    private var isNightMode = false

    // 语音合成客户端
    private lateinit var mSpeechSynthesizer: SpeechSynthesizer
    private var isSpeaking = false
    private val isOnlineSDK = true
    private var wholePageStr = ""
    private var pageSegmentStr = ""
    private var mainHandler: Handler? = null

    // 接收电池信息更新的广播
    private val myReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                Log.e(LOG_TAG, Intent.ACTION_BATTERY_CHANGED)
                val level = intent.getIntExtra("level", 0)
                pageFactory!!.updateBattery(level)
            } else if (intent.action == Intent.ACTION_TIME_TICK) {
                Log.e(LOG_TAG, Intent.ACTION_TIME_TICK)
                pageFactory!!.updateTime()
            }
        }
    }

    override fun getLayoutRes(): Int {
        return R.layout.activity_read
    }

    override fun initData(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 19) {
            bookpage!!.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
        toolbar!!.title = ""
        setSupportActionBar(toolbar)
        toolbar!!.setNavigationIcon(R.mipmap.return_button)
        toolbar!!.setNavigationOnClickListener { finish() }
        readSettingManager = ReadSettingManager.getInstance()
        pageFactory = PageFactory.getInstance()
        val mfilter = IntentFilter()
        mfilter.addAction(Intent.ACTION_BATTERY_CHANGED)
        mfilter.addAction(Intent.ACTION_TIME_TICK)
        registerReceiver(myReceiver, mfilter)
        mSettingDialog = ReadSettingDialog(this)

        //获取屏幕宽高
        val manage = windowManager
        val display = manage.defaultDisplay
        val displaysize = Point()
        display.getSize(displaysize)
        screenWidth = displaysize.x
        screenHeight = displaysize.y

        //保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        //隐藏
        hideSystemUI()

        //改变屏幕亮度
        if (ReadSettingManager.getInstance().isBrightnessAuto) {
            BrightnessUtils.setDefaultBrightness(this)
        } else {
            BrightnessUtils.setBrightness(this, ReadSettingManager.getInstance().brightness)
        }

        //获取intent中的携带的信息
        val intent = intent
        bookMeta = intent.getSerializableExtra(EXTRA_BOOK) as BookMeta?
        bookpage!!.setPageMode(readSettingManager!!.getPageMode())
        pageFactory.setPageWidget(bookpage)
        try {
            pageFactory.openBook(bookMeta)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "打开电子书失败", Toast.LENGTH_SHORT).show()
        }
        isNightMode = readSettingManager.isNightMode()
        initView()
        initBaiduTTs()

        GlobalScope.launch {
            delay(1000) // 延时2秒
            restorePref()
        }
//        restorePref()
    }

    private fun initView() {
        toggleNightMode()
        mainHandler = object : Handler() {
            /*
             * @param msg
             */
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                if (msg.obj != null) {
//                    print(msg.obj.toString());
                }
            }
        }
    }

    override fun initListener() {
        mSbReadProgress!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            var pro = 0f

            // 触发操作，拖动
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                pro = (progress / 10000.0).toFloat()
                showProgress(pro)
            }

            // 表示进度条刚开始拖动，开始拖动时候触发的操作
            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            // 停止拖动时候
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                pageFactory!!.changeProgress(pro)
            }
        })
        mSettingDialog!!.setOnCancelListener { hideSystemUI() }
        mSettingDialog!!.setSettingListener(object : SettingListener {
            override fun changeSystemBright(isSystem: Boolean, brightness: Float) {
                if (!isSystem) {
                    BrightnessUtils.setBrightness(this@ReadActivity, brightness.toInt())
                } else {
                    val bh = BrightnessUtils.getScreenBrightness(this@ReadActivity)
                    BrightnessUtils.setBrightness(this@ReadActivity, bh)
                }
            }

            override fun changeFontSize(fontSize: Int) {
                pageFactory!!.changeFontSize(fontSize)
            }

            override fun changeTypeFace(typeface: Typeface) {
                pageFactory!!.changeTypeface(typeface)
            }

            override fun changeBookPageStyle(pageStyle: PageStyle) {
                pageFactory!!.changeBookPageStyle(pageStyle)
            }

            override fun changePageMode(pageMode: PageMode) {
                bookpage!!.setPageMode(pageMode)
            }
        })
        pageFactory!!.setPageEvent { progress ->
            val message = Message()
            message.what = MESSAGE_CHANGEPROGRESS
            message.obj = progress
            mHandler.sendMessage(message)
        }
        bookpage!!.setTouchListener(object : PageView.TouchListener {
            override fun center() {
                if (isShow) {
                    hideReadSetting()
                } else {
                    showReadSetting()
                }
            }

            override fun prePage(): Boolean {
                if (isShow || isSpeaking) {
                    return false
                }
                pageFactory!!.prePage()
                return if (pageFactory!!.isfirstPage()) {
                    false
                } else true
            }

            override fun nextPage(): Boolean {
                Log.e("setTouchListener", "nextPage")
                if (isShow || isSpeaking) {
                    return false
                }
                pageFactory!!.nextPage()
                return if (pageFactory!!.islastPage()) {
                    false
                } else true
            }

            override fun cancel() {
                pageFactory!!.cancelPage()
            }
        })
    }

    private val mHandler: Handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                MESSAGE_CHANGEPROGRESS -> {
                    val progress = msg.obj as Float
                    setSeekBarProgress(progress)
                }
            }
        }
    }

    private val startPosStoreKey get() = "${pageFactory.bookPath}_startPos"
    private fun storePref() {
        val prefs = getPreferences(MODE_PRIVATE)
        val edit = prefs.edit()
        lastPosition = pageFactory.currentPage.begin
        Log.d(LOG_TAG, "storePref = $lastPosition")
        edit.putLong(startPosStoreKey, lastPosition)
        edit.apply()
    }

    private fun restorePref() {
        val prefs = getPreferences(MODE_PRIVATE)
        lastPosition = prefs.getLong(startPosStoreKey, 0)
        Log.d(LOG_TAG, "restorePref = $lastPosition")
        pageFactory.changeChapter(lastPosition)
    }

    override fun onResume() {
        super.onResume()
        if (!isShow) {
            hideSystemUI()
        }
        if (mSpeechSynthesizer != null) {
            mSpeechSynthesizer!!.resume()
        }
    }

    override fun onStop() {
        super.onStop()
        if (mSpeechSynthesizer != null) {
            mSpeechSynthesizer!!.stop()
        }
        storePref();
    }

    override fun onDestroy() {
        super.onDestroy()
        pageFactory!!.clear()
        bookpage = null
        unregisterReceiver(myReceiver)
        isSpeaking = false
        if (mSpeechSynthesizer != null) {
            mSpeechSynthesizer!!.release()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isShow) {
                hideReadSetting()
                return true
            }
            if (mSettingDialog!!.isShowing) {
                mSettingDialog!!.hide()
                return true
            }
            finish()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.read, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_add_bookmark) {
            addBookmark()
        } else if (id == R.id.action_read_book) {
            startTtsReadBook()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun addBookmark() {
//        if (pageFactory.getCurrentPage() != null) {
//            List<BookMark> bookMarkList = LitePal.where("bookPath = ? and begin = ?", pageFactory.getBookPath(), pageFactory.getCurrentPage().getBegin() + "").find(BookMark.class);
//
//            if (!bookMarkList.isEmpty()) {
//                Toast.makeText(ReadActivity.this, "该书签已存在", Toast.LENGTH_SHORT).show();
//            } else {
//
//            }
//        }
        val bookMark = BookMark()
        var word: String? = ""
        for (line in pageFactory!!.currentPage.lines) {
            word += line
        }
        try {
            val sf = SimpleDateFormat(
                "yyyy-MM-dd HH:mm ss"
            )
            val time = sf.format(Date())
            bookMark.time = time
            bookMark.begin = pageFactory!!.currentPage.begin
            bookMark.text = word
            bookMark.bookPath = pageFactory!!.bookPath
            bookMark.save()
            Toast.makeText(this@ReadActivity, "书签添加成功", Toast.LENGTH_SHORT).show()
        } catch (e: SQLException) {
            Toast.makeText(this@ReadActivity, "该书签已存在", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this@ReadActivity, "添加书签失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startTtsReadBook() {
        if (mSpeechSynthesizer != null) {
            wholePageStr = pageFactory!!.currentPage.wholePageStr
            val len = wholePageStr.length
            Log.d(LOG_TAG, "len = $len, str=$wholePageStr")
            if (len < 60) {
                pageSegmentStr = wholePageStr.substring(0, len)
                wholePageStr = ""
            } else {
                pageSegmentStr = wholePageStr.substring(0, 60)
                wholePageStr = wholePageStr.substring(60)
            }
            Log.d(LOG_TAG, "After len = " + wholePageStr.length + ", str=" + wholePageStr)
            val result = mSpeechSynthesizer!!.speak(pageSegmentStr)
            if (result < 0) {
                Log.e(LOG_TAG, "error result = $result")
            } else {
                hideReadSetting()
                isSpeaking = true
            }
        }
    }
    //    public BookPageWidget getPageWidget() {
    //        return bookpage;
    //    }
    /**
     * 隐藏菜单。沉浸式阅读
     */
    private fun hideSystemUI() {
        // Set the IMMERSIVE flag.
        // Set the content to appear under the system bars so that the content
        // doesn't resize when the system bars hide and show.
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN //  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION // hide nav bar
                or View.SYSTEM_UI_FLAG_FULLSCREEN // hide status bar
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    private fun showSystemUI() {
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_LAYOUT_STABLE //                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    //显示书本进度
    fun showProgress(progress: Float) {
        if (rl_progress!!.visibility != View.VISIBLE) {
            rl_progress!!.visibility = View.VISIBLE
        }
        setProgress(progress)
    }

    //隐藏书本进度
    override fun hideProgress() {
        rl_progress!!.visibility = View.GONE
    }

    private fun toggleNightMode() {
        if (isNightMode) {
            mTvDayNightMode!!.text = StringUtils.getString(this, R.string.mode_day)
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_read_menu_morning)
            mTvDayNightMode!!.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
        } else {
            mTvDayNightMode!!.text = StringUtils.getString(this, R.string.mode_night)
            val drawable = ContextCompat.getDrawable(this, R.drawable.ic_read_menu_night)
            mTvDayNightMode!!.setCompoundDrawablesWithIntrinsicBounds(null, drawable, null, null)
        }
    }

    private fun setProgress(progress: Float) {
        val decimalFormat = DecimalFormat("00.00") //构造方法的字符格式这里如果小数不足2位,会以0补足.
        val p = decimalFormat.format(progress * 100.0) //format 返回的是字符串
        tv_progress!!.text = "$p%"
    }

    fun setSeekBarProgress(progress: Float) {
        mSbReadProgress!!.progress = (progress * 10000).toInt()
    }

    private fun showReadSetting() {
        isShow = true
        rl_progress!!.visibility = View.GONE
        if (isSpeaking) {
            val topAnim = AnimationUtils.loadAnimation(this, R.anim.dialog_top_enter)
            rl_read_bottom!!.startAnimation(topAnim)
            rl_read_bottom!!.visibility = View.VISIBLE
        } else {
            showSystemUI()
            val bottomAnim = AnimationUtils.loadAnimation(this, R.anim.dialog_enter)
            val topAnim = AnimationUtils.loadAnimation(this, R.anim.dialog_top_enter)
            rl_bottom!!.startAnimation(topAnim)
            appbar!!.startAnimation(topAnim)
            //        ll_top.startAnimation(topAnim);
            rl_bottom!!.visibility = View.VISIBLE
            //        ll_top.setVisibility(View.VISIBLE);
            appbar!!.visibility = View.VISIBLE
        }
    }

    private fun hideReadSetting() {
        isShow = false
        val bottomAnim = AnimationUtils.loadAnimation(this, R.anim.dialog_exit)
        val topAnim = AnimationUtils.loadAnimation(this, R.anim.dialog_top_exit)
        if (rl_bottom!!.visibility == View.VISIBLE) {
            rl_bottom!!.startAnimation(topAnim)
        }
        if (appbar!!.visibility == View.VISIBLE) {
            appbar!!.startAnimation(topAnim)
        }
        if (rl_read_bottom!!.visibility == View.VISIBLE) {
            rl_read_bottom!!.startAnimation(topAnim)
        }
        rl_bottom!!.visibility = View.GONE
        rl_read_bottom!!.visibility = View.GONE
        appbar!!.visibility = View.GONE
        hideSystemUI()
    }

    /**
     * 注意此处为了说明流程，故意在UI线程中调用。
     * 实际集成中，该方法一定在新线程中调用，并且该线程不能结束。具体可以参考NonBlockSyntherizer的写法
     */
    private fun initBaiduTTs() {
        LoggerProxy.printable(true) // 日志打印在logcat中

        // 1. 获取实例
        mSpeechSynthesizer = SpeechSynthesizer.getInstance()
        mSpeechSynthesizer.setContext(this)

        // 2. 设置listener
        mSpeechSynthesizer.setSpeechSynthesizerListener(this)

        // 3. 设置appId，appKey.secretKey
        var result = mSpeechSynthesizer.setAppId("25367863")
        result = mSpeechSynthesizer.setApiKey(
            "atqtokGtwgi8GIVkYMxGClnZ",
            "Of6IheDaVL6547r4HL6kv5WhpwX3NGsA"
        )

        // 5. 以下setParam 参数选填。不填写则默认值生效
        // 设置在线发声音人： 0 普通女声（默认） 1 普通男声  3 情感男声<度逍遥> 4 情感儿童声<度丫丫>
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0")
        // 设置合成的音量，0-15 ，默认 5
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_VOLUME, "9")
        // 设置合成的语速，0-15 ，默认 5
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEED, "5")
        // 设置合成的语调，0-15 ，默认 5
        mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_PITCH, "5")

        // x. 额外 ： 自动so文件是否复制正确及上面设置的参数
        val params: Map<String, String> = HashMap()
        // 复制下上面的 mSpeechSynthesizer.setParam参数

        // 6. 初始化
        result = mSpeechSynthesizer.initTts(TtsMode.ONLINE)
    }

    @OnClick(
        R.id.read_tv_pre_chapter,
        R.id.read_tv_next_chapter,
        R.id.read_tv_contents,
        R.id.read_tv_day_night_mode,
        R.id.read_tv_setting,
        R.id.tv_stop_tts_read
    )
    fun onClick(view: View) {
        when (view.id) {
            R.id.read_tv_pre_chapter -> pageFactory!!.preChapter()
            R.id.read_tv_next_chapter -> pageFactory!!.nextChapter()
            R.id.read_tv_contents -> {
                val intent = Intent(this@ReadActivity, BookMarkActivity::class.java)
                startActivity(intent)
            }
            R.id.read_tv_day_night_mode -> {
                isNightMode = !isNightMode
                toggleNightMode()
                readSettingManager!!.isNightMode = isNightMode
                pageFactory!!.setDayOrNight(isNightMode)
            }
            R.id.read_tv_setting -> {
                hideReadSetting()
                mSettingDialog!!.show()
            }
            R.id.tv_stop_tts_read -> if (mSpeechSynthesizer != null) {
                mSpeechSynthesizer!!.stop()
                isSpeaking = false
                hideReadSetting()
            }
            else -> {}
        }
    }

    override fun onSynthesizeStart(s: String) {
        Log.d(LOG_TAG, "onSynthesizeStart, s=$s")
    }

    /**
     * 合成数据和进度的回调接口，分多次回调
     *
     * @param utteranceId
     * @param data        合成的音频数据。该音频数据是采样率为16K，2字节精度，单声道的pcm数据。
     * @param progress    文本按字符划分的进度，比如:你好啊 进度是0-3
     */
    override fun onSynthesizeDataArrived(
        utteranceId: String,
        data: ByteArray,
        progress: Int,
        engineType: Int
    ) {
        Log.d(LOG_TAG, "onSynthesizeDataArrived, progress=$progress")
    }

    /**
     * 合成正常结束，每句合成正常结束都会回调，如果过程中出错，则回调onError，不再回调此接口
     *
     * @param utteranceId
     */
    override fun onSynthesizeFinish(utteranceId: String) {
        Log.d(LOG_TAG, "onSynthesizeFinish, utteranceId=$utteranceId")
    }

    /**
     * 播放开始，每句播放开始都会回调
     *
     * @param utteranceId
     */
    override fun onSpeechStart(utteranceId: String) {
        Log.d(LOG_TAG, "onSpeechStart, utteranceId=$utteranceId")
    }

    /**
     * 播放进度回调接口，分多次回调
     *
     * @param utteranceId
     * @param progress    文本按字符划分的进度，比如:你好啊 进度是0-3
     */
    override fun onSpeechProgressChanged(utteranceId: String, progress: Int) {
        Log.d(LOG_TAG, "onSpeechProgressChanged, utteranceId=$utteranceId")
    }

    /**
     * 播放正常结束，每句播放正常结束都会回调，如果过程中出错，则回调onError,不再回调此接口
     *
     * @param utteranceId
     */
    override fun onSpeechFinish(utteranceId: String) {
        Log.d(LOG_TAG, "onSpeechFinish, utteranceId=$utteranceId")
        if (wholePageStr.length > 0) {
            val len = wholePageStr.length
            Log.d(LOG_TAG, "len = $len, str=$wholePageStr")
            if (len < 60) {
                pageSegmentStr = wholePageStr.substring(0, len)
                wholePageStr = ""
            } else {
                pageSegmentStr = wholePageStr.substring(0, 60)
                wholePageStr = wholePageStr.substring(60)
            }
            //            Log.d(LOG_TAG, "After len = " + wholePageStr.length() + ", str=" + wholePageStr);
            val result = mSpeechSynthesizer!!.speak(pageSegmentStr)
            if (result < 0) {
                Log.e(LOG_TAG, "error result = $result")
            } else {
                hideReadSetting()
                isSpeaking = true
            }
        } else {
            pageFactory!!.nextPage()
            if (pageFactory!!.islastPage()) {
                isSpeaking = false
                Toast.makeText(this@ReadActivity, "小说已经读完了", Toast.LENGTH_SHORT)
            } else {
                isSpeaking = true
                pageFactory!!.currentPage.setWholePageStr()
                wholePageStr = pageFactory!!.currentPage.wholePageStr
                pageSegmentStr = wholePageStr.substring(0, 60)
                wholePageStr = wholePageStr.substring(60)
                mSpeechSynthesizer!!.speak(pageSegmentStr)
            }
        }
    }

    /**
     * 当合成或者播放过程中出错时回调此接口
     *
     * @param utteranceId
     * @param error       包含错误码和错误信息
     */
    override fun onError(utteranceId: String, error: SpeechError) {
        Log.e(
            LOG_TAG,
            "onError, utteranceId=" + utteranceId + ", error=" + error.code + "," + error.description
        )
        mSpeechSynthesizer!!.stop()
        isSpeaking = false
    }

    companion object {
        private val LOG_TAG = ReadActivity::class.java.simpleName
        const val REQUEST_MORE_SETTING = 101
        private const val EXTRA_BOOK = "bookList"
        private const val MESSAGE_CHANGEPROGRESS = 1
        @JvmStatic
        fun openBook(context: Activity, bookMeta: BookMeta?): Boolean {
            if (bookMeta == null) {
                throw NullPointerException("BookList can not be null")
            }
            val intent = Intent(context, ReadActivity::class.java)
            intent.putExtra(EXTRA_BOOK, bookMeta)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            context.overridePendingTransition(R.anim.in_from_right, R.anim.out_to_left)
            context.startActivity(intent)
            return true
        }
    }
}