package com.longluo.ebookreader.util.Epub2

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.util.Log
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.folioreader.FolioReader
import com.folioreader.FolioReader.OnClosedListener
import com.folioreader.model.HighLight
import com.folioreader.model.HighLight.HighLightAction
import com.folioreader.model.locators.ReadLocator
import com.folioreader.util.OnHighlightListener
import com.folioreader.util.ReadLocatorListener
import com.hjq.toast.ToastUtils
import com.longluo.ebookreader.R
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class Reader internal constructor(
    private val context: Context,
    private val readerConfig: ReaderConfig,
    private val filePath: String,
) : OnHighlightListener, ReadLocatorListener, OnClosedListener {
    var folioReader: FolioReader

    private var read_locator: ReadLocator? = null
    private var lastLocation: String? = null

    init {
        highlightsAndSave
        //setPageHandler(messenger);
        folioReader = FolioReader.get()
            .setOnHighlightListener(this)
            .setReadLocatorListener(this)
            .setOnClosedListener(this)
        restorePref()
    }

    @SuppressLint("LogNotTimber")
    fun open(bookPath: String) {
        ToastUtils.show("准备打开文档")
        Thread {
            try {
                Log.i("SavedLocation", "-> savedLocation -> $lastLocation")
                if (lastLocation != null && !lastLocation!!.isEmpty()) {
                    val readLocator = ReadLocator.fromJson(lastLocation)
                    folioReader.setReadLocator(readLocator)

                folioReader.setConfig(readerConfig.config, true)
                    .openBook(bookPath)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun close() {
        folioReader.close()
    }

    val highlightsAndSave: Unit
        get() {
            Thread {
                var highlightList: ArrayList<HighLight?>? = null
                val objectMapper = ObjectMapper()
                try {
                    val content = loadAssetTextAsString("highlights/highlights_data.json");
                    if (content != null) {
                        highlightList = objectMapper.readValue(
                            content,
                            object : TypeReference<List<HighlightData?>?>() {})
                    }

                } catch (e: IOException) {
                    e.printStackTrace()
                }
//                if (highlightList == null) {
//                    folioReader.saveReceivedHighLights(highlightList) {
//                        //You can do anything on successful saving highlight list
//                    }
//                }
            }.start()
        }

    @SuppressLint("LogNotTimber")
    private fun loadAssetTextAsString(name: String): String? {
        var `in`: BufferedReader? = null
        try {
            val buf = StringBuilder()
            val `is` = context.assets.open(name)
            `in` = BufferedReader(InputStreamReader(`is`))
            var str: String?
            var isFirst = true
            while (`in`.readLine().also { str = it } != null) {
                if (isFirst) isFirst = false else buf.append('\n')
                buf.append(str)
            }
            return buf.toString()
        } catch (e: IOException) {
            Log.e("Reader", "Error opening asset $name")
        } finally {
            if (`in` != null) {
                try {
                    `in`.close()
                } catch (e: IOException) {
                    Log.e("Reader", "Error closing asset $name")
                }
            }
        }
        return null
    }

    override fun onFolioReaderClosed() {
        Log.i("readLocator", "-> saveReadLocator -> " + read_locator!!.toJson())
        storePref()
        ToastUtils.show("存储进度成功")
        folioReader.close()
    }

    private val pageStoreKey get() = "page_$filePath"
    private val pageFontSizeStoreKey get() = "font_size_$filePath"
    private fun storePref() {
        read_locator?.toJson()?.let {
            val prefs = (context as Activity).getPreferences(MODE_PRIVATE)
            val edit = prefs.edit()
            edit.putString(pageStoreKey, it)
//        edit.putInt(pageFontSizeStoreKey, mLayoutEM)
            edit.apply()
        }
    }

    @SuppressLint("LogNotTimber")
    private fun restorePref() {
        val prefs = (context as Activity).getPreferences(MODE_PRIVATE)
//        mLayoutEM = prefs.getInt(pageFontSizeStoreKey, 10);
        val lastLocationTmp = prefs.getString(pageStoreKey, null);
        lastLocationTmp?.let {
            lastLocation = it
            Log.d("restorePref", "lastLocation = $lastLocation")
        }
    }

    override fun onHighlight(highlight: HighLight, type: HighLightAction) {}
    override fun saveReadLocator(readLocator: ReadLocator) {
        read_locator = readLocator
    }

    companion object {
        private const val PAGE_CHANNEL = "sage"
        private var curReader: Reader? = null;

        fun openEpubFile(context: Context, filePath: String, config: ReaderConfig, lastLocation: String?) {
            val reader = Reader(context, config, filePath)
            curReader = reader
            reader.open(filePath)
        }

        fun closeReader() {
            curReader?.close();
        }
    }
}