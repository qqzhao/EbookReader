package com.longluo.ebookreader.ui.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.Settings
import android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.gyf.immersionbar.ImmersionBar
import com.longluo.ebookreader.R
import com.longluo.ebookreader.app.AppActivity
import com.longluo.ebookreader.app.AppFragment
import com.longluo.ebookreader.manager.ActivityManager
import com.longluo.ebookreader.other.DoubleClickHelper
import com.longluo.ebookreader.ui.adapter.NavigationAdapter
import com.longluo.ebookreader.ui.fragment.BookshelfFragment
import com.longluo.ebookreader.ui.fragment.FileExplorerFragment
import com.longluo.ebookreader.ui.fragment.MineFragment
import io.github.longluo.base.FragmentPagerAdapter


/**
 * 首页界面
 */
class HomeActivity : AppActivity(), NavigationAdapter.OnNavigationListener {
    private var mViewPager: ViewPager? = null
    private var mNavigationView: RecyclerView? = null
    private var mNavigationAdapter: NavigationAdapter? = null
    private var mPagerAdapter: FragmentPagerAdapter<AppFragment<*>?>? = null
    private var bookshelfFragment: BookshelfFragment? = null
    override fun getLayoutId(): Int {
        return R.layout.home_activity
    }

    override fun initView() {
        mViewPager = findViewById(R.id.vp_home_pager)
        mNavigationView = findViewById(R.id.rv_home_navigation)
        mNavigationAdapter = NavigationAdapter(this)
        mNavigationAdapter!!.addItem(
            NavigationAdapter.MenuItem(
                getString(R.string.home_nav_bookshelf),
                ContextCompat.getDrawable(this, R.drawable.home_bookshelf_selector)
            )
        )
        mNavigationAdapter!!.addItem(
            NavigationAdapter.MenuItem(
                getString(R.string.home_nav_found),
                ContextCompat.getDrawable(this, R.drawable.home_explore_selector)
            )
        )
        mNavigationAdapter!!.addItem(
            NavigationAdapter.MenuItem(
                getString(R.string.home_nav_me),
                ContextCompat.getDrawable(this, R.drawable.home_my_selector)
            )
        )
        mNavigationAdapter!!.setOnNavigationListener(this)
        mNavigationView?.setAdapter(mNavigationAdapter)

        requestPermission()
    }

    override fun initData() {
        mPagerAdapter = FragmentPagerAdapter(this)
        bookshelfFragment = BookshelfFragment.newInstance()
        mPagerAdapter!!.addFragment(bookshelfFragment)
        mPagerAdapter!!.addFragment(FileExplorerFragment.newInstance())
        mPagerAdapter!!.addFragment(MineFragment.newInstance())
        mViewPager!!.adapter = mPagerAdapter
        onNewIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        switchFragment(mPagerAdapter!!.getFragmentIndex(getSerializable(INTENT_KEY_IN_FRAGMENT_CLASS)))
        when {
            intent.action == Intent.ACTION_SEND -> {
                if ("text/plain" == intent.type) {
                    handleSendText(intent) // Handle text being sent
                } else if (intent.type?.startsWith("image/") == true) {
                    handleSendImage(intent) // Handle single image being sent
                } else  {
                    Log.d(TAG, "ACTION_SEND other case")
                }
            }
            intent?.action == Intent.ACTION_SEND_MULTIPLE
                    && intent.type?.startsWith("image/") == true -> {
                handleSendMultipleImages(intent) // Handle multiple images being sent
            }
            else -> {
                Log.d(TAG, "handleSendText")
            }
        }
    }

    private fun handleSendText(intent: Intent) {
        Log.d(TAG, "handleSendText, intent extra = ${intent.extras}")
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
            Log.d(TAG, "handleSendText text length = ${it.length}")
        }

        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
            Log.d(TAG, "handleSendText uri = ${it}")
        }
    }

    private fun handleSendImage(intent: Intent) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
            Log.d(TAG, "handleSendImage")
        }
    }

    private fun handleSendMultipleImages(intent: Intent) {
        intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.let {
            Log.d(TAG, "handleSendMultipleImages")
        }
    }


    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存当前 Fragment 索引位置
        outState.putInt(INTENT_KEY_IN_FRAGMENT_INDEX, mViewPager!!.currentItem)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // 恢复当前 Fragment 索引位置
        switchFragment(savedInstanceState.getInt(INTENT_KEY_IN_FRAGMENT_INDEX))
    }

    private fun switchFragment(fragmentIndex: Int) {
        if (fragmentIndex == -1) {
            return
        }
        when (fragmentIndex) {
            0, 1, 2 -> {
                mViewPager!!.currentItem = fragmentIndex
                mNavigationAdapter!!.selectedPosition = fragmentIndex
            }
            else -> {}
        }
    }

    /**
     * [NavigationAdapter.OnNavigationListener]
     */
    override fun onNavigationItemSelected(position: Int): Boolean {
        return when (position) {
            0, 1, 2 -> {
                mViewPager!!.currentItem = position
                true
            }
            else -> false
        }
    }

    override fun createStatusBarConfig(): ImmersionBar {
        return super.createStatusBarConfig() // 指定导航栏背景颜色
            .navigationBarColor(R.color.white)
    }

    override fun onBackPressed() {
        if (!DoubleClickHelper.isOnDoubleClick()) {
            toast(R.string.home_exit_hint)
            return
        }

        // 移动到上一个任务栈，避免侧滑引起的不良反应
        moveTaskToBack(false)
        postDelayed({
            // 进行内存优化，销毁掉所有的界面
            ActivityManager.getInstance().finishAllActivities()
        }, 300)
    }

    override fun onDestroy() {
        super.onDestroy()
        mViewPager!!.adapter = null
        mNavigationView!!.adapter = null
        mNavigationAdapter!!.setOnNavigationListener(null)
    }

    fun refreshBookShelf() {
        bookshelfFragment!!.refreshData()
    }

    private val REQUEST_PERMISSION_CODE = 1
    private fun requestPermission() {
//        val permission_read = ContextCompat.checkSelfPermission(
//            this@HomeActivity,
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        )
//        if (permission_read != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(
//                this@HomeActivity,
//                arrayOf<String>(Manifest.permission.READ_EXTERNAL_STORAGE),
//                REQUEST_PERMISSION_CODE
//            )
//        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Toast.makeText(this, "已获得访问所有文件权限", Toast.LENGTH_SHORT).show()
            } else {
                val builder = AlertDialog.Builder(this)
                    .setMessage("本程序需要您同意允许访问所有文件权限")
                    .setPositiveButton("确定") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    }
                builder.show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (resultCode == RESULT_OK) {
                // 权限已授予
                // 执行需要访问所有文件的操作
            } else {
                // 权限被拒绝
                // 可以在此处向用户解释为什么需要该权限，并提供手动授权的方式
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_PERMISSION_CODE) {
            Log.i("TAG", "request permission success")
        }
        super.onRequestPermissionsResult(requestCode, permissions!!, grantResults!!)
    }

    companion object {
        private const val INTENT_KEY_IN_FRAGMENT_INDEX = "fragmentIndex"
        private const val INTENT_KEY_IN_FRAGMENT_CLASS = "fragmentClass"
        val TAG = "HomeActivity"
        @JvmOverloads
        fun start(
            context: Context,
            fragmentClass: Class<out AppFragment<*>?>? = BookshelfFragment::class.java
        ) {
            val intent = Intent(context, HomeActivity::class.java)
            intent.putExtra(INTENT_KEY_IN_FRAGMENT_CLASS, fragmentClass)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}