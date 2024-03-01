package com.longluo.ebookreader.ui.fragment

import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.longluo.ebookreader.R
import com.longluo.ebookreader.app.TitleBarFragment
import com.longluo.ebookreader.db.BookMark
import com.longluo.ebookreader.db.BookMeta
import com.longluo.ebookreader.ui.activity.HomeActivity
import com.longluo.ebookreader.ui.adapter.BookshelfAdapter
import com.longluo.ebookreader.util.BookUtils
import com.longluo.ebookreader.widget.itemdecoration.DividerItemDecoration
import org.litepal.LitePal.delete
import org.litepal.LitePal.deleteAll
import org.litepal.LitePal.findAll
import org.litepal.crud.LitePalSupport
import java.io.File

class BookshelfFragment : TitleBarFragment<HomeActivity?>() {
    private var mBookshelf: RecyclerView? = null
    private var mBooks: MutableList<BookMeta>? = null
    private var mBookshelfAdapter: BookshelfAdapter? = null

    //点击书本的位置
    private var itemPosition = 0

    private var useMethod1: Boolean? = null
    override fun getLayoutId(): Int {
        return R.layout.bookshelf_fragment
    }

    override fun initView() {
        mBookshelf = findViewById(R.id.bookShelf)
    }

    override fun initData() {
        mBooks = findAll(BookMeta::class.java)
        mBookshelfAdapter = BookshelfAdapter(requireActivity(), mBooks!!)
        mBookshelf!!.layoutManager = GridLayoutManager(activity, 4)
        mBookshelf!!.adapter = mBookshelfAdapter
        mBookshelf!!.addItemDecoration(
            DividerItemDecoration(
                activity
            )
        )
        mBookshelfAdapter!!.notifyDataSetChanged()
        initListener()
    }

    fun refreshData() {
        mBooks = findAll(BookMeta::class.java)
        mBookshelfAdapter!!.setBookLists(mBooks!!)
        mBookshelfAdapter!!.notifyDataSetChanged()
    }

    private fun initListener() {
        mBookshelfAdapter!!.setOnItemClickListener(object : BookshelfAdapter.OnItemClickListener {
            override fun onClick(position: Int) {
                if (mBooks!!.size > position) {
                    itemPosition = position
                    val bookMeta = mBooks!![itemPosition]
                    bookMeta.id = mBooks!![0].id
                    val path = bookMeta.bookPath
                    val file = File(path)
//                    if (!file.exists()) {
//                        AlertDialog.Builder(activity)
//                            .setTitle(activity!!.getString(R.string.app_name))
//                            .setMessage(path + "文件不存在,是否删除该书本？")
//                            .setPositiveButton(R.string.delete) { dialog, which ->
//                                deleteBook(position)
//                                //mShelfAdapter.setmBookList(mBooks);
//                            }.setCancelable(true).show()
//                        return
//                    }
                    if(useMethod1 == null) {
                        AlertDialog.Builder(activity)
                            .setTitle("提示")
                            .setMessage("是否使用原来的方式？")
                            .setNegativeButton("方式1") { dialog, which ->
                                useMethod1 = true;
                                dialog.dismiss()
                                BookUtils.openBook(activity!!, bookMeta)
                            }
                            .setPositiveButton("方式2") { dialog, which ->
                                useMethod1 = false
                                BookUtils.openBook2(activity!!, bookMeta)
                            }.setCancelable(true).show()
                        return
                    }
                    if (useMethod1 == true) {
                        BookUtils.openBook(activity!!, bookMeta)
                    } else {
                        BookUtils.openBook2(activity!!, bookMeta)
                    }
                }
            }
        })
        mBookshelfAdapter!!.setOnItemLongClickListener(object :
            BookshelfAdapter.OnItemLongClickListener {
            override fun onClick(position: Int) {
                AlertDialog.Builder(activity)
                    .setTitle("提示")
                    .setMessage("是否删除书本？")
                    .setNegativeButton("取消") { dialog, which -> dialog.dismiss() }
                    .setPositiveButton("删除") { dialog, which ->
                        deleteBook(position)
                    }.setCancelable(true).show()
            }
        })
    }

    private fun deleteBook(position: Int) {
        Thread {
            try {
                val item = mBooks!![position];
                item.delete()
//                delete(BookMark::class.java, mBooks!![position].id.toLong())
            } catch (e: java.lang.Exception) {
                Log.d("XX", "dd = $e")
            }
            Handler(Looper.getMainLooper()).post {
                mBooks!!.clear()
                //mBooks.addAll(LitePal.where("bookPath = ?", bookPath).find(BookMark.class));
                refreshData()
            }
        }.start()
    }

    override fun isStatusBarEnabled(): Boolean {
        // 使用沉浸式状态栏
        return !super.isStatusBarEnabled()
    }

    override fun onResume() {
        super.onResume()
        mBooks = findAll(BookMeta::class.java)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    companion object {
        fun newInstance(): BookshelfFragment {
            return BookshelfFragment()
        }
    }
}