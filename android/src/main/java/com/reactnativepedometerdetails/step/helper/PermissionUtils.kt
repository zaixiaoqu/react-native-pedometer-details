package com.reactnativepedometerdetails.step.helper

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.SparseArray
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.Promise
import com.facebook.react.modules.core.PermissionAwareActivity
import com.facebook.react.modules.core.PermissionListener

class PermissionUtils : PermissionListener {

    val healthPermissionKey = Manifest.permission.ACTIVITY_RECOGNITION;
    private val SETTING_NAME = "@RNSNPermissions:NonRequestables"
    private val ERROR_INVALID_ACTIVITY = "E_INVALID_ACTIVITY"
    private val GRANTED = "granted"
    private val DENIED = "denied"
    private val BLOCKED = "blocked"
    private var mRequestCode = 0

    private var mSharedPrefs: SharedPreferences? = null
    private var mRequests: SparseArray<Request>? = null
    private var permissionAwareActivity: PermissionAwareActivity? = null;

    private class Request(var rationaleStatuses: BooleanArray, var callback: Callback)

    public fun requestPermission(permission: String, activity: Activity, promise: Promise) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            promise.resolve(
                if (activity.applicationContext.checkPermission(permission, Process.myPid(), Process.myUid())
                    == PackageManager.PERMISSION_GRANTED
                ) GRANTED else BLOCKED
            )
            return
        }
        mRequests = SparseArray<Request>()
        mSharedPrefs = activity.applicationContext.getSharedPreferences(SETTING_NAME, Context.MODE_PRIVATE);
        if (activity.applicationContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            promise.resolve(GRANTED)
            return
        } else if (mSharedPrefs != null && mSharedPrefs!!.getBoolean(permission, false)) {
            promise.resolve(BLOCKED) // not supporting reset the permission with "Ask me every time"
            return
        }
        try {
            permissionAwareActivity = activity as PermissionAwareActivity
            val rationaleStatuses = BooleanArray(1)
            rationaleStatuses[0] = activity.shouldShowRequestPermissionRationale(permission)

            mRequests!!.put(
                mRequestCode, Request(
                    rationaleStatuses,
                    Callback { args ->
                        val results = args[0] as IntArray
                        if (results.size > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                            promise.resolve(GRANTED)
                        } else {
                            val activity = args[1] as PermissionAwareActivity
                            val rationaleStatuses = args[2] as BooleanArray
                            if (rationaleStatuses[0] &&
                                !activity.shouldShowRequestPermissionRationale(permission)
                            ) {
                                mSharedPrefs!!.edit().putBoolean(permission, true).commit() // enforce sync
                                promise.resolve(BLOCKED)
                            } else {
                                promise.resolve(DENIED)
                            }
                        }
                    })
            )
            permissionAwareActivity!!.requestPermissions(arrayOf(permission), mRequestCode, this)
            mRequestCode++
        } catch (e: Exception) {
            promise.resolve(ERROR_INVALID_ACTIVITY)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>?,
        grantResults: IntArray?
    ): Boolean {
        try {
            val request: Request = mRequests!![requestCode]
            request.callback.invoke(
                grantResults,
                permissionAwareActivity,
                request.rationaleStatuses
            )
            mRequests!!.remove(requestCode)
            return mRequests!!.size() == 0
        } catch (e: Exception) {
        }
        return false;
    }
}