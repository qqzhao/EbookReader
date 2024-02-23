package com.longluo.ebookreader.util.Epub2

import com.folioreader.model.HighLight
import java.util.*

/**
 * Class contain data structure for highlight data. If user want to
 * provide external highlight data to folio activity. class should implement to
 * [HighLight] with contains required members.
 *
 *
 * Created by gautam chibde on 12/10/17.
 */
class HighlightData : HighLight {
    private val bookId: String? = null
    private val content: String? = null
    private val date: Date? = null
    private val type: String? = null
    private val pageNumber = 0
    private val pageId: String? = null
    private val rangy: String? = null
    private val uuid: String? = null
    private val note: String? = null
    override fun toString(): String {
        return "HighlightData{" +
                "bookId='" + bookId + '\'' +
                ", content='" + content + '\'' +
                ", date=" + date +
                ", type='" + type + '\'' +
                ", pageNumber=" + pageNumber +
                ", pageId='" + pageId + '\'' +
                ", rangy='" + rangy + '\'' +
                ", uuid='" + uuid + '\'' +
                ", note='" + note + '\'' +
                '}'
    }

    override fun getBookId(): String {
        return bookId!!
    }

    override fun getContent(): String {
        return content!!
    }

    override fun getDate(): Date {
        return date!!
    }

    override fun getType(): String {
        return type!!
    }

    override fun getPageNumber(): Int {
        return pageNumber
    }

    override fun getPageId(): String {
        return pageId!!
    }

    override fun getRangy(): String {
        return rangy!!
    }

    override fun getUUID(): String {
        return uuid!!
    }

    override fun getNote(): String {
        return note!!
    }
}