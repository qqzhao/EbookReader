package com.longluo.ebookreader.util.Epub2

import android.content.Context
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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

//import io.flutter.plugin.common.BinaryMessenger;
//import io.flutter.plugin.common.EventChannel;
//import io.flutter.plugin.common.MethodChannel;
class Reader internal constructor(
    private val context: Context,
    private val readerConfig: ReaderConfig
) : OnHighlightListener, ReadLocatorListener, OnClosedListener {
    var folioReader: FolioReader

    //    public MethodChannel.Result result;
    //    private EventChannel eventChannel;
    //    private EventChannel.EventSink pageEventSink;
    //    private BinaryMessenger messenger;
    private var read_locator: ReadLocator? = null

    init {
        highlightsAndSave
        //setPageHandler(messenger);
        folioReader = FolioReader.get()
            .setOnHighlightListener(this)
            .setReadLocatorListener(this)
            .setOnClosedListener(this)
        //        pageEventSink = sink;
    }

    fun open(bookPath: String, lastLocation: String?) {
        Thread {
            try {
                Log.i("SavedLocation", "-> savedLocation -> $lastLocation")
                if (lastLocation != null && !lastLocation.isEmpty()) {
                    val readLocator = ReadLocator.fromJson(lastLocation)
                    folioReader.setReadLocator(readLocator)
                }
                folioReader.setConfig(readerConfig.config, true)
                    .openBook(bookPath)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun close() {
        folioReader.close()
    }//You can do anything on successful saving highlight list

    //    private void setPageHandler(BinaryMessenger messenger) {
    ////        final MethodChannel channel = new MethodChannel(registrar.messenger(), "page");
    ////        channel.setMethodCallHandler(new EpubKittyPlugin());
    //        Log.i("event sink is", "in set page handler:");
    //        eventChannel = new EventChannel(messenger, PAGE_CHANNEL);
    //
    //        try {
    //
    //            eventChannel.setStreamHandler(new EventChannel.StreamHandler() {
    //
    //                @Override
    //                public void onListen(Object o, EventChannel.EventSink eventSink) {
    //
    //                    Log.i("event sink is", "this is eveent sink:");
    //
    //                    pageEventSink = eventSink;
    //                    if (pageEventSink == null) {
    //                        Log.i("empty", "Sink is empty");
    //                    }
    //                }
    //
    //                @Override
    //                public void onCancel(Object o) {
    //
    //                }
    //            });
    //        } catch (Error err) {
    //            Log.i("and error", "error is " + err.toString());
    //        }
    //    }
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
        //        if (pageEventSink != null) {
//            pageEventSink.success(read_locator.toJson());
//        }
    }

    override fun onHighlight(highlight: HighLight, type: HighLightAction) {}
    override fun saveReadLocator(readLocator: ReadLocator) {
        read_locator = readLocator
    }

    companion object {
        private const val PAGE_CHANNEL = "sage"
        private var curReader: Reader? = null;

        fun openEpubFile(context: Context, filePath: String, config: ReaderConfig, lastLocation: String?) {
            val reader = Reader(context, config)
            curReader = reader;
            reader.open(filePath, null)
        }

        fun closeReader() {
            curReader?.close();
        }
    }
}