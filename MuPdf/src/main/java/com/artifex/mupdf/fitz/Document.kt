// Copyright (C) 2004-2022 Artifex Software, Inc.
//
// This file is part of MuPDF.
//
// MuPDF is free software: you can redistribute it and/or modify it under the
// terms of the GNU Affero General Public License as published by the Free
// Software Foundation, either version 3 of the License, or (at your option)
// any later version.
//
// MuPDF is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
// FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
// details.
//
// You should have received a copy of the GNU Affero General Public License
// along with MuPDF. If not, see <https://www.gnu.org/licenses/agpl-3.0.en.html>
//
// Alternative licensing terms are available from the licensor.
// For commercial licensing, see <https://www.artifex.com/> or contact
// Artifex Software, Inc., 39 Mesa Street, Suite 108A, San Francisco,
// CA 94129, USA, for further information.
package com.artifex.mupdf.fitz

import android.util.Log
import com.artifex.mupdf.fitz.OutlineIterator.OutlineItem
import kotlinx.coroutines.*

open class Document protected constructor(protected var pointer: Long) {
    protected open external fun finalize()
    fun destroy() {
        pagesMap.clear()
        finalize()
    }

    external fun supportsAccelerator(): Boolean
    external fun saveAccelerator(filename: String?)
    external fun outputAccelerator(stream: SeekableOutputStream?)
    external fun needsPassword(): Boolean
    external fun authenticatePassword(password: String?): Boolean
    external fun countChapters(): Int
//    external fun countPages(chapter: Int): Int
    external fun countPages(chapter: Int): Int
    external fun loadPage(chapter: Int, number: Int): Page
    fun countPages(): Int {
        Log.d("document-1", "countPages begin")
        var np = 0
        val nc = countChapters()
        for (i in 0 until nc) np += countPagesWrap(i)
        Log.d("document-1", "countPages end, np=$np")
        return np
    }

    var curConfigStr: String = ""
    val pagesMap: HashMap<String, Int> = hashMapOf()

//    suspend fun countPagesOptForRange(from: Int, to: Int): Int = withContext(Dispatchers.Default) {
//        var np = 0
//        for (i in from until to) np += countPages(i)
//        return@withContext np;
//    }

    fun countPagesOpt(needAll: Boolean, callback: (value: Int) -> Unit): Int {
        Log.d("document-1", "countPages begin")
        var np = 0
        val nc = countChapters()
        if (nc >= 100 && !needAll) {
            GlobalScope.launch(Dispatchers.Default) {
                val allCount = countPages()
//                callback.invoke(allCount)
                callback(allCount)
            }
            np = 100
        } else {
            np = countPages()
        }
        return np
    }

    fun loadPage(loc: Location): Page {
        return loadPage(loc.chapter, loc.page)
    }

    fun loadPage(number: Int): Page {
        var start = 0
        val nc = countChapters()
        for (i in 0 until nc) {
            val np = countPagesWrap(i)
            if (number < start + np) return loadPage(i, number - start)
            start += np
        }
        throw IllegalArgumentException("page number out of range")
    }

    fun lastPage(): Location {
        val nc = countChapters()
        val np = countPagesWrap(nc - 1)
        return Location(nc - 1, np - 1)
    }

    fun nextPage(loc: Location): Location {
        val np = countPagesWrap(loc.chapter)
        if (loc.page + 1 == np) {
            val nc = countChapters()
            if (loc.chapter + 1 < nc) return Location(loc.chapter + 1, 0)
        } else {
            return Location(loc.chapter, loc.page + 1)
        }
        return loc
    }

    fun previousPage(loc: Location): Location {
        if (loc.page == 0) {
            if (loc.chapter > 0) {
                val np = countPagesWrap(loc.chapter - 1)
                return Location(loc.chapter - 1, np - 1)
            }
        } else {
            return Location(loc.chapter, loc.page - 1)
        }
        return loc
    }

    fun clampLocation(input: Location): Location {
        var c = input.chapter
        var p = input.page
        val nc = countChapters()
        if (c < 0) c = 0
        if (c >= nc) c = nc - 1
        val np = countPagesWrap(c)
        if (p < 0) p = 0
        if (p >= np) p = np - 1
        return if (input.chapter == c && input.page == p) input else Location(
            c,
            p
        )
    }

    fun locationFromPageNumber(number: Int): Location {
        var number = number
        var i: Int
        var start = 0
        var np = 0
        val nc = countChapters()
        if (number < 0) number = 0
        i = 0
        while (i < nc) {
            np = countPagesWrap(i)
            if (number < start + np) return Location(i, number - start)
            start += np
            ++i
        }
        return Location(i - 1, np - 1)
    }

    fun pageNumberFromLocation(loc: Location?): Int {
        val nc = countChapters()
        var start = 0
        if (loc == null) return -1
        for (i in 0 until nc) {
            if (i == loc.chapter) return start + loc.page
            start += countPagesWrap(i)
        }
        return -1
    }

    external fun search(chapter: Int, page: Int, needle: String?): Array<Array<Quad?>?>?
    external fun resolveLink(uri: String?): Location
    fun resolveLink(link: Outline): Location {
        return resolveLink(link.uri)
    }

    fun resolveLink(link: Link): Location {
        return resolveLink(link.uri)
    }

    external fun resolveLinkDestination(uri: String?): LinkDestination
    fun resolveLinkDestination(item: OutlineItem): LinkDestination {
        return resolveLinkDestination(item.uri)
    }

    fun resolveLinkDestination(link: Outline): LinkDestination {
        return resolveLinkDestination(link.uri)
    }

    fun resolveLinkDestination(link: Link): LinkDestination {
        return resolveLinkDestination(link.uri)
    }


    fun layoutWrap(width: Float, height: Float, em: Float) {
        curConfigStr = "config_$width|$height|$em"
        layout(width, height, em)
    }

    fun countPagesWrap(chapter: Int): Int {
        val key = "${curConfigStr}_$chapter"
        var value = pagesMap[key];
        if (value == null) {
            Log.d("document-1", "chapter=$chapter: $value")
            value = countPages(chapter)
            pagesMap.put(key, value);
        }
        return value
    }

    external fun loadOutline(): Array<Outline?>?
    external fun outlineIterator(): OutlineIterator?
    external fun getMetaData(key: String?): String?
    external fun setMetaData(key: String?, value: String?)
    val isReflowable: Boolean
        external get

    external fun layout(width: Float, height: Float, em: Float)
    external fun findBookmark(mark: Long): Location?
    external fun makeBookmark(chapter: Int, page: Int): Long
    fun makeBookmark(loc: Location): Long {
        return makeBookmark(loc.chapter, loc.page)
    }

    external fun hasPermission(permission: Int): Boolean
    val isUnencryptedPDF: Boolean
        external get

    external fun formatLinkURI(dest: LinkDestination?): String?

    open val isPDF: Boolean
        get() = false

    companion object {
        init {
            Context.init()
        }

        const val META_FORMAT = "format"
        const val META_ENCRYPTION = "encryption"
        const val META_INFO_AUTHOR = "info:Author"
        const val META_INFO_TITLE = "info:Title"
        const val META_INFO_SUBJECT = "info:Subject"
        const val META_INFO_KEYWORDS = "info:Keywords"
        const val META_INFO_CREATOR = "info:Creator"
        const val META_INFO_PRODUCER = "info:Producer"
        const val META_INFO_CREATIONDATE = "info:CreationDate"
        const val META_INFO_MODIFICATIONDATE = "info:ModDate"
        @JvmStatic protected external fun openNativeWithPath(filename: String?, accelerator: String?): Document
        @JvmStatic protected external fun openNativeWithBuffer(
            magic: String?,
            buffer: ByteArray?,
            accelerator: ByteArray?
        ): Document

        @JvmStatic protected external fun openNativeWithStream(
            magic: String?,
            stream: SeekableInputStream?,
            accelerator: SeekableInputStream?
        ): Document

        @JvmStatic protected external fun openNativeWithPathAndStream(
            filename: String?,
            accelerator: SeekableInputStream?
        ): Document

        @JvmStatic fun openDocument(filename: String?): Document {
            return openNativeWithPath(filename, null)
        }

        @JvmStatic fun openDocument(filename: String?, accelerator: String?): Document {
            return openNativeWithPath(filename, accelerator)
        }

        @JvmStatic fun openDocument(filename: String?, accelerator: SeekableInputStream?): Document {
            return openNativeWithPathAndStream(filename, accelerator)
        }

        @JvmStatic fun openDocument(buffer: ByteArray?, magic: String?): Document {
            return openNativeWithBuffer(magic, buffer, null)
        }

        @JvmStatic fun openDocument(buffer: ByteArray?, magic: String?, accelerator: ByteArray?): Document {
            return openNativeWithBuffer(magic, buffer, accelerator)
        }

        @JvmStatic fun openDocument(stream: SeekableInputStream?, magic: String?): Document {
            return openNativeWithStream(magic, stream, null)
        }

        @JvmStatic fun openDocument(
            stream: SeekableInputStream?,
            magic: String?,
            accelerator: SeekableInputStream?
        ): Document {
            return openNativeWithStream(magic, stream, accelerator)
        }

        @JvmStatic external fun recognize(magic: String?): Boolean
        const val PERMISSION_PRINT = 'p'.code
        const val PERMISSION_COPY = 'c'.code
        const val PERMISSION_EDIT = 'e'.code
        const val PERMISSION_ANNOTATE = 'n'.code
        const val PERMISSION_FORM = 'f'.code
        const val PERMISSION_ACCESSBILITY = 'y'.code
        const val PERMISSION_ASSEMBLE = 'a'.code
        const val PERMISSION_PRINT_HQ = 'h'.code
    }
}