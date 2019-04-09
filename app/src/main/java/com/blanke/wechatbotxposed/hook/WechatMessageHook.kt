package com.blanke.wechatbotxposed.hook

import android.util.Log
import com.blanke.wechatbotxposed.hook.SendMsgHooker.wxMsgSplitStr
import com.gh0u1l5.wechatmagician.spellbook.interfaces.IMessageStorageHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WechatMessageHook : IMessageStorageHook {

    //图灵机器人API  获取KEY请访问 : http://www.tuling123.com/
    private val BASE_URL: String = "http://openapi.tuling123.com/openapi/api/v2"
    private val API_KEY: String = "2f2ad22b60e04e8d8245da334db58b0d" //请使用自己申请的图灵机器人的KEY哦
    private var USER_ID: String = "default" //用户唯一ID

    override fun onMessageStorageCreated(storage: Any) {

    }

    override fun onMessageStorageInserted(msgId: Long, msgObject: Any) {
        XposedBridge.log("onMessageStorageInserted msgId=$msgId,msgObject=$msgObject")
        printMsgObj(msgObject)
        // 这些都是消息的属性，内容，发送人，类型等
        val field_content = XposedHelpers.getObjectField(msgObject, "field_content") as String?
        val field_talker = XposedHelpers.getObjectField(msgObject, "field_talker") as String?
        val field_type = (XposedHelpers.getObjectField(msgObject, "field_type") as Int).toInt()
        val field_isSend = (XposedHelpers.getObjectField(msgObject, "field_isSend") as Int).toInt()
        XposedBridge.log("field_content=$field_content,field_talker=$field_talker," +
                "field_type=$field_type,field_isSend=$field_isSend")
        if (field_isSend == 1) {// 代表自己发出的，不处理
            return
        }
        if (field_type == 1) { //文本消息
            // field_content 中:后的部分就是消息内容
            //请求图灵机器人开始
            XposedBridge.log("请求图灵机器人开始")
            val serverURL: String = "$BASE_URL"
            val url = URL(serverURL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 300000
            connection.connectTimeout = 300000
            connection.doOutput = true
            var postStr:String = ""
            postStr += "{"
            postStr += "\"reqType\":0,"
            postStr += "\"perception\": {"
            postStr += "\"inputText\": {"
            postStr += "\"text\": \"${field_content?.substringAfter(":","hello")?.trim()}\""
            postStr += "}"
            postStr += "},"
            postStr += "\"userInfo\": {"
            postStr += "\"apiKey\": \"$API_KEY\","
            postStr += "\"userId\": \"$USER_ID\""
            postStr += "}"
            postStr += "}"
            XposedBridge.log("请求数据：$postStr")
            val postData: ByteArray = postStr.toByteArray(StandardCharsets.UTF_8)

            connection.setRequestProperty("charset", "utf-8")
            connection.setRequestProperty("Content-lenght", postData.size.toString())
            connection.setRequestProperty("Content-Type", "application/json")

            try {
                val outputStream: DataOutputStream = DataOutputStream(connection.outputStream)
                outputStream.write(postData)
                outputStream.flush()
                XposedBridge.log("请求图灵机器人 post结束")
            } catch (e: Exception) {
                e.printStackTrace()
            }
            XposedBridge.log("请求图灵机器人 connection.responseCode="+connection.responseCode)
            if (connection.responseCode == HttpURLConnection.HTTP_OK || connection.responseCode == HttpURLConnection.HTTP_CREATED) {
                try {
                    val reader: BufferedReader = BufferedReader(InputStreamReader(connection.inputStream))
                    var line: String
                    var result = ""
                    do {
                        line = reader.readLine()
                        if (line != null) {
                            result += line
                        }
                    } while (reader!=null && line != null)
                    connection.inputStream.close()
                    //请求图灵机器人结束
                    //解析返回的Json串
                    XposedBridge.log("请求图灵机器人 result=$result")
                    var jsonObject: JSONObject = JSONObject(result)
                    var jsonArray:JSONArray = jsonObject.getJSONArray("results")
                    if(jsonArray.length() > 0){
                        for (i in 0..(jsonArray.length() - 1)){
                            val item = jsonArray.getJSONObject(i)
                            var replyContent: String = jsonObject.optString("values")
                            Objects.ChattingFooterEventImpl?.apply {
                                //发送消息
                                val content = "$replyContent"
                                val success = Methods.ChattingFooterEventImpl_SendMsg.invoke(this, content) as Boolean
                                XposedBridge.log("reply msg success = $success")
                            }
                        }
                    }

                } catch (exception: Exception) {
                    exception.printStackTrace()
                    throw Exception("Exception while push the notification  $exception.message")
                }
            }
            //
            var replyContent: String = field_content?.substringAfter(":","hello")?.trim()!!
            Objects.ChattingFooterEventImpl?.apply {
                //发送消息
                val content = "$replyContent"
                val success = Methods.ChattingFooterEventImpl_SendMsg.invoke(this, content) as Boolean
                XposedBridge.log("reply msg success = $success")
            }
        }
    }

    private fun printMsgObj(msg: Any) {
        val fieldNames = msg::class.java.fields
        fieldNames.forEach {
            val field = it.get(msg)
            if (field is Array<*>) {
                val s = StringBuffer()
                field.forEach {
                    s.append(it.toString() + " , ")
                }
                XposedBridge.log("$it = $s")
            } else {
                XposedBridge.log("$it = $field")
            }
        }
    }
}