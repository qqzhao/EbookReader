package com.longluo.ebookreader.util

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.text.TextUtils
import androidx.fragment.app.FragmentActivity
import com.longluo.ebookreader.App
import com.longluo.ebookreader.bean.Cache
import com.longluo.ebookreader.db.BookContent
import com.longluo.ebookreader.db.BookMeta
import com.longluo.ebookreader.libs.LibAntiword
import com.longluo.ebookreader.libs.LibMobi
import com.longluo.ebookreader.ui.activity.ReadActivity
import com.longluo.ebookreader.util.Epub2.Reader.Companion.openEpubFile
import com.longluo.ebookreader.util.Epub2.ReaderConfig
import com.longluo.viewer.DocumentActivity
import org.litepal.LitePal.update
import timber.log.Timber
import java.io.*
import java.lang.ref.WeakReference

class BookUtils {
    //    protected final ArrayList<WeakReference<char[]>> myArray = new ArrayList<>();
    protected val myArray = ArrayList<Cache>()

    //目录
    private val directoryList: MutableList<BookContent> = ArrayList()
    private var strCharsetName: String? = null
    private var bookName: String? = null
    private var bookPath: String? = null
    var bookLen: Long = 0
        private set
    var position: Long = 0
        private set
    private var bookMeta: BookMeta? = null

    init {
        val file = File(cachedPath)
        if (!file.exists()) {
            file.mkdir()
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun openBook(bookMeta: BookMeta) {
        this.bookMeta = bookMeta
        //如果当前缓存不是要打开的书本就缓存书本同时删除缓存
        if (bookPath == null || bookPath != bookMeta.bookPath) {
            cleanCacheFile()
            bookPath = bookMeta.bookPath
            bookName = FileUtils.getFileName(bookPath)
            cacheBook()
        }
    }

    private fun cleanCacheFile() {
        val file = File(cachedPath)
        if (!file.exists()) {
            file.mkdir()
        } else {
            val files = file.listFiles()
            for (i in files.indices) {
                files[i].delete()
            }
        }
    }

    fun next(back: Boolean): Int {
        position += 1
        if (position > bookLen) {
            position = bookLen
            return -1
        }
        val result = current()
        if (back) {
            position -= 1
        }
        return result.toInt()
    }

    fun nextLine(): CharArray? {
        if (position >= bookLen) {
            return null
        }
        var line = ""
        while (position < bookLen) {
            val word = next(false)
            if (word == -1) {
                break
            }
            val wordChar = word.toChar()
            if (wordChar.toString() + "" == "\r" && next(true).toChar().toString() + "" == "\n") {
                next(false)
                break
            }
            line += wordChar
        }
        return line.toCharArray()
    }

    fun preLine(): CharArray? {
        if (position <= 0) {
            return null
        }
        var line = ""
        while (position >= 0) {
            val word = pre(false)
            if (word == -1) {
                break
            }
            val wordChar = word.toChar()
            if (wordChar.toString() + "" == "\n" && pre(true).toChar().toString() + "" == "\r") {
                pre(false)
                //                line = "\r\n" + line;
                break
            }
            line = wordChar.toString() + line
        }
        return line.toCharArray()
    }

    fun current(): Char {
        var cachePos = 0
        var pos = 0
        var len = 0
        for (i in myArray.indices) {
            val size = myArray[i].size
            if (size + len - 1 >= position) {
                cachePos = i
                pos = (position - len).toInt()
                break
            }
            len += size.toInt()
        }
        val charArray = block(cachePos)
        return charArray[pos]
    }

    fun pre(back: Boolean): Int {
        position -= 1
        if (position < 0) {
            position = 0
            return -1
        }
        val result = current()
        if (back) {
            position += 1
        }
        return result.toInt()
    }

    fun setPostition(position: Long) {
        this.position = position
    }

    //缓存书本
    @Throws(IOException::class)
    private fun cacheBook() {
        if (TextUtils.isEmpty(bookMeta!!.charset)) {
            strCharsetName = FileUtils.getCharset(bookPath)
            if (strCharsetName == null) {
                strCharsetName = "utf-8"
            }
            val values = ContentValues()
            values.put("charset", strCharsetName)
            update(BookMeta::class.java, values, bookMeta!!.id.toLong())
        } else {
            strCharsetName = bookMeta!!.charset
        }
        val file = File(bookPath)
        val reader = InputStreamReader(FileInputStream(file), strCharsetName)
        var index = 0
        bookLen = 0
        directoryList.clear()
        myArray.clear()
        while (true) {
            var buf = CharArray(cachedSize)
            val result = reader.read(buf)
            if (result == -1) {
                reader.close()
                break
            }
            var bufStr = String(buf)
            bufStr = bufStr.replace("\r\n+\\s*".toRegex(), "\r\n\u3000\u3000")
            bufStr = bufStr.replace("\u0000".toRegex(), "")
            buf = bufStr.toCharArray()
            bookLen += buf.size.toLong()
            val cache = Cache()
            cache.size = buf.size.toLong()
            cache.data = WeakReference(buf)
            myArray.add(cache)
            try {
                val cacheBook = File(fileName(index))
                if (!cacheBook.exists()) {
                    cacheBook.createNewFile()
                }
                val writer = OutputStreamWriter(FileOutputStream(fileName(index)), "UTF-16LE")
                writer.write(buf)
                writer.close()
            } catch (e: IOException) {
                throw RuntimeException("Error during writing " + fileName(index))
            }
            index++
        }
        object : Thread() {
            override fun run() {
                chapter
            }
        }.start()
    }

    //获取章节
    @get:Synchronized
    val chapter: Unit
        get() {
            try {
                var size: Long = 0
                for (i in myArray.indices) {
                    val buf = block(i)
                    val bufStr = String(buf)
                    val paragraphs = bufStr.split("\r\n".toRegex()).dropLastWhile { it.isEmpty() }
                        .toTypedArray()
                    for (str in paragraphs) {
                        if (str.length <= 30 && (str.matches(Regex(".*第.{1,8}章.*")) || str.matches(Regex(".*第.{1,8}节.*")))) {
                            val bookContent = BookContent()
                            bookContent.bookContentStartPos = size
                            bookContent.bookContent = str
                            bookContent.bookPath = bookPath
                            directoryList.add(bookContent)
                        }
                        size += if (str.contains("\u3000\u3000")) {
                            (str.length + 2).toLong()
                        } else if (str.contains("\u3000")) {
                            (str.length + 1).toLong()
                        } else {
                            str.length.toLong()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    fun getDirectoryList(): List<BookContent> {
        return directoryList
    }

    protected fun fileName(index: Int): String {
        return cachedPath + bookName + index
    }

    //获取书本缓存
    fun block(index: Int): CharArray {
        if (myArray.size == 0) {
            return CharArray(1)
        }
        var block = myArray[index].data.get()
        if (block == null) {
            try {
                val file = File(fileName(index))
                val size = file.length().toInt()
                if (size < 0) {
                    throw RuntimeException("Error during reading " + fileName(index))
                }
                block = CharArray(size / 2)
                val reader = InputStreamReader(
                    FileInputStream(file),
                    "UTF-16LE"
                )
                if (reader.read(block) != block.size) {
                    throw RuntimeException("Error during reading " + fileName(index))
                }
                reader.close()
            } catch (e: IOException) {
                throw RuntimeException("Error during reading " + fileName(index))
            }
            val cache = myArray[index]
            cache.data = WeakReference(block)
        }
        return block
    }

    companion object {
        private const val LOG_TAG = "BookUtils"
        private val cachedPath =
            Environment.getExternalStorageDirectory().toString() + "/ebookreader/"

        //存储的字符数
        const val cachedSize = 30000
        @JvmStatic
        fun isBookFormatSupport(fileName: String): Boolean {
            return if (fileName.endsWith(".txt") || fileName.endsWith(".epub") || fileName.endsWith(
                    ".mobi"
                )
                || fileName.endsWith(".azw") || fileName.endsWith(".azw3") || fileName.endsWith(".pdf")
                || fileName.endsWith(".doc") || fileName.endsWith(".docx")
            ) {
                true
            } else false
        }

        @JvmStatic
        fun openBook(activity: FragmentActivity, bookMeta: BookMeta) {
            val filePath = bookMeta.bookPath
            val file = File(filePath)
            val suffix = FileUtils.getSuffix(filePath)
            Timber.d("openBook: filePath=$filePath, suffix=$suffix")
            if (suffix == "txt") {
                ReadActivity.openBook(activity, bookMeta)
            } else if (suffix == "epub" || suffix == "pdf") {
                openEpubPdfBook(activity, file)
            } else if (suffix == "mobi" || suffix == "azw" || suffix == "azw3" || suffix == "azw4") {
                openMobiAzwBook(activity, file)
            }
        }

        @JvmStatic
        fun openBook2(activity: FragmentActivity, bookMeta: BookMeta) {
            val filePath = bookMeta.bookPath
            val file = File(filePath)
            val suffix = FileUtils.getSuffix(filePath)
            Timber.d("openBook: filePath=$filePath, suffix=$suffix")
            if (suffix == "txt") {
                ReadActivity.openBook(activity, bookMeta)
            } else if (suffix == "epub" || suffix == "pdf") {
                val config = ReaderConfig(
                    activity,
                    "iosBook",
                    "#2196f3",
                    "alldirections",
                    true,
                    true,
                    true
                )
                openEpubFile(activity, filePath, config, "")
                //            openEpubPdfBook(activity, file);
            } else if (suffix == "mobi" || suffix == "azw" || suffix == "azw3" || suffix == "azw4") {
                openMobiAzwBook(activity, file)
            }
        }

        fun openEpubPdfBook(activity: FragmentActivity, file: File?) {
            val intent = Intent(activity, DocumentActivity::class.java)
            // API>=21: intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT); /* launch as a new document */
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT) /* launch as a new document */
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.fromFile(file)
            activity.startActivity(intent)
        }

        fun openMobiAzwBook(activity: FragmentActivity, file: File) {
            val path = file.absolutePath
            val folderPath = path.substring(0, path.lastIndexOf("/"))
            val hashCodeStr = path.hashCode().toString() + ""
            val convertFilePath = folderPath + File.separator + hashCodeStr + ".epub"
            Timber.d(
                "openMobiAzwBook: file=" + path + ", folder=" + folderPath
                        + ",convertFilePath=" + convertFilePath
            )
            val convertFile = File(convertFilePath)
            if (!convertFile.exists()) {
                LibMobi.convertToEpub(path, File(folderPath, hashCodeStr).path)
            }
            val firstConvertFile =
                File(folderPath + File.separator + hashCodeStr + hashCodeStr + ".epub")
            if (firstConvertFile.exists()) {
                firstConvertFile.renameTo(File(convertFilePath))
            }
            openEpubPdfBook(activity, convertFile)
        }

        fun openDocFile(activity: FragmentActivity, file: File) {
            val path = file.absolutePath
            val folderPath = path.substring(0, path.lastIndexOf("/"))
            val hashCodeStr = path.hashCode().toString() + ""
            val convertFilePath = folderPath + File.separator + hashCodeStr + ".epub"
            Timber.d(
                "openMobiAzwBook: file=" + path + ", folder=" + folderPath
                        + ",convertFilePath=" + convertFilePath
            )
            val convertFile = File(convertFilePath)
            if (!convertFile.exists()) {
                LibAntiword.convertDocToHtml(path, File(folderPath, hashCodeStr).path)
            }
            val firstConvertFile =
                File(folderPath + File.separator + hashCodeStr + hashCodeStr + ".epub")
            if (firstConvertFile.exists()) {
                firstConvertFile.renameTo(File(convertFilePath))
            }
            openEpubPdfBook(activity, convertFile)
        }
    }
}