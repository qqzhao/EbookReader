package com.longluo.ebookreader.ui.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.longluo.ebookreader.R
import com.longluo.ebookreader.db.BookMeta
import com.longluo.ebookreader.ui.adapter.view.BookshelfViewHolder

class BookshelfAdapter(private val mContext: Context, private var mBookList: List<BookMeta>) :
    RecyclerView.Adapter<BookshelfViewHolder>() {
    private var listener: OnItemClickListener? = null
    private var longClickListener: OnItemLongClickListener? = null

    interface OnItemClickListener {
        fun onClick(position: Int)
    }

    interface OnItemLongClickListener {
        fun onClick(position: Int)
    }

    fun setOnItemClickListener(listener: OnItemClickListener?) {
        this.listener = listener
    }

    fun setOnItemLongClickListener(longClickListener: OnItemLongClickListener?) {
        this.longClickListener = longClickListener
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BookshelfViewHolder {
        val view =
            LayoutInflater.from(mContext).inflate(R.layout.layout_shelf_item, parent, false)
        return BookshelfViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: BookshelfViewHolder,
        @SuppressLint("RecyclerView") position: Int
    ) {
        val item = mBookList[position];
        holder.tvBookName.text = item.bookName
        holder.tvBookType.text = item.bookPath.subSequence(item.bookPath.length-3, item.bookPath.length)
        holder.itemView.setOnClickListener { v: View? ->
            if (listener != null) {
                listener!!.onClick(position)
            }
        }
        holder.itemView.setOnLongClickListener { v: View? ->
            if (longClickListener != null) {
                longClickListener!!.onClick(position)
            }
            true
        }
    }

    override fun getItemCount(): Int {
        return mBookList.size
    }

    fun getItem(position: Int): Any {
        return mBookList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    fun setBookLists(books: List<BookMeta>) {
        mBookList = books
    }
}