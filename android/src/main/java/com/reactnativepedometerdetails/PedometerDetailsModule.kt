package com.reactnativepedometerdetails

import com.alibaba.fastjson.JSON
import com.facebook.react.bridge.*
import com.reactnativepedometerdetails.step.StartUp
import com.reactnativepedometerdetails.step.helper.PermissionUtils
import com.reactnativepedometerdetails.step.helper.JSONToJSValue
import org.json.JSONArray


class PedometerDetailsModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var startUp:StartUp
    private var permissionUtils: PermissionUtils
    private var reactContext:ReactApplicationContext

    init {
        startUp = StartUp()
        startUp.startUpInit(reactContext);
        permissionUtils = PermissionUtils();
        this.reactContext = reactContext;
    }

    override fun getName(): String {
        return "PedometerDetails"
    }

    /**
     * 判断是否有权限
     *
     * @param promise
     */
    @ReactMethod
    fun isNeedRequestPermission(promise: Promise) {
        if (true == currentActivity?.let {
                permissionUtils.isNeedRequestPermission(
                    permissionUtils.healthPermissionKey,
                    it,
                    promise
                )
            }) {
            promise.resolve(permissionUtils.getErrorStateValue())
            return
        }
        return
    }

    /**
     * 请求权限
     *
     * @param promise
     */
    @ReactMethod
    fun requestPermission(promise: Promise) {
        currentActivity?.let {
            permissionUtils.requestPermission(
                permissionUtils.healthPermissionKey,
                it,
                promise
            )
        }
    }

    /**
     * 按照天得到数据
     */
    @ReactMethod
    fun getDaysSteps(date: Int, promise: Promise) {
        val js = Arguments.createMap()
        js.putInt("date", date)
        js.putInt("stepCount", startUp.getCachePaseoDBHelper().getDaysSteps(date))

        promise.resolve(js)
    }

    /**
     * 检索移动数据库中最近的行走日期
     */
    @ReactMethod
    fun readLastStepsDate(promise: Promise) {
        promise.resolve(startUp.getCachePaseoDBHelper().readLastStepsDate())
    }

    /**
     * 检索移动数据库中最近的行走时间
     */
    @ReactMethod
    fun readLastStepsTime(promise: Promise) {
        promise.resolve(startUp.getCachePaseoDBHelper().readLastStepsTime())
    }

    /**
     * 获取一天中每小时（或一年中的一天、一年中的一周或一年中的一个月）所采取的步骤数
     */
    @ReactMethod
    fun getStepsByTimeUnit(date: Int, timeUnit: String, asc: Boolean = true, weekStart: Int = 2, promise: Promise) {
        var s = JSON.parseArray(
            JSON.toJSONString(startUp.getCachePaseoDBHelper().getStepsByTimeUnit(date, timeUnit, asc, weekStart))
        )
        promise.resolve(JSONToJSValue.convertJsonToArray(s))
    }

    /**
     * 获取一天中每小时（或一年中的一天、一年中的一周或一年中的一个月）所采取的步骤数
     */
    @ReactMethod
    fun getSteps(timeUnit: String, theDate: Int, theDay: Int = 0, weekStart: Int = 2, promise: Promise) {
        promise.resolve(startUp.getCachePaseoDBHelper().getSteps(
            timeUnit, theDate, theDay, weekStart
        ))
    }

    /**
     * 检索以时间单位（天、周等）为单位的平均步骤数
     */
    @ReactMethod
    fun getAverageSteps(timeUnit: String, weekStart: Int = 2, promise: Promise) {
        promise.resolve(startUp.getCachePaseoDBHelper().getAverageSteps(
            timeUnit, 0, 0, weekStart
        ))
    }
}
