package com.reactnativepedometerdetails.step.helper

import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableNativeMap

import com.facebook.react.bridge.WritableMap
import com.facebook.react.bridge.WritableArray


class JSONToJSValue {
    companion object {
        public fun convertJsonToMap(jsonObject: JSONObject): WritableMap {
            val map: WritableMap = WritableNativeMap()
            try {
                for ((key, value) in jsonObject.entries) {
                    val value: Any? = jsonObject.get(key)
                    if (value is JSONObject) {
                        map.putMap(key, convertJsonToMap(value as JSONObject))
                    } else if (value is JSONArray) {
                        map.putArray(key, convertJsonToArray(value as JSONArray))
                    } else if (value is Boolean) {
                        map.putBoolean(key, value)
                    } else if (value is Int) {
                        map.putInt(key, value)
                    } else if (value is Double) {
                        map.putDouble(key, value)
                    } else if (value is String) {
                        map.putString(key, value)
                    } else {
                        map.putString(key, value.toString())
                    }
                }
            } catch (e:Exception) {

            }
            return map
        }

        public fun convertJsonToArray(jsonArray: JSONArray): WritableArray {
            val array: WritableArray = Arguments.createArray()
            try {
                for (i in 0 until jsonArray.size) {
                    val value = jsonArray[i]
                    if (value is JSONObject) {
                        array.pushMap(convertJsonToMap(value))
                    } else if (value is JSONArray) {
                        array.pushArray(convertJsonToArray(value))
                    } else if (value is Boolean) {
                        array.pushBoolean(value)
                    } else if (value is Int) {
                        array.pushInt(value)
                    } else if (value is Double) {
                        array.pushDouble(value)
                    } else if (value is String) {
                        array.pushString(value)
                    }
                }
            } catch (e:Exception) {
            }
            return array
        }
    }

}