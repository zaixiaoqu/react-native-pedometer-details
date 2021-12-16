package com.reactnativepedometerdetails.step

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.hardware.SensorManager
import com.facebook.react.bridge.ReactApplicationContext
import com.reactnativepedometerdetails.step.background.StepCounterService

class StartUp {
    private var startSteps = 0
    private var lastStepDate = 0
    private lateinit var paseoDBHelper: PaseoDBHelper

    public fun startUpInit(context: ReactApplicationContext) {
        try {
            // set up the manager for the step counting service
            context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

            // point to the Paseo database that stores all the daily steps data
            paseoDBHelper = PaseoDBHelper(context)

            // get the date of the last record from the steps table in the database
            lastStepDate = paseoDBHelper.readLastStepsDate()

            // get the start steps of the last record from the steps table in the database
            startSteps = paseoDBHelper.readLastStartSteps()


            // do not start the step counting service if it is already running
            if (!isServiceRunning(context, "com.reactnativepedometerdetails.step.background.StepCounterService")) {
                startStepService(context)
            }
        } catch (e:Exception) {}
    }

    // check if the step counting service is already running (to avoid starting a second one)
    private fun isServiceRunning(context: ReactApplicationContext, serviceName: String): Boolean {
        var serviceRunning = false
        try {
            val theActivityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = theActivityManager.getRunningServices(50)
            val eachService: Iterator<ActivityManager.RunningServiceInfo> = runningServices.iterator()

            // loop through the running services
            while (eachService.hasNext()) {
                val runningServiceInfo = eachService
                    .next()

                // check if this service's name for a match to the one passed in to this function as
                //  an argument (will most commonly be paseo's step counting service)
                if (runningServiceInfo.service.className == serviceName) {
                    serviceRunning = true

                }
            }
        } catch (e:Exception) {}

        // if true, the service was found running
        return serviceRunning
    }


    // start the step counting and activity detection services
    private fun startStepService(context: ReactApplicationContext) {
        context.startService(Intent(context, StepCounterService::class.java))
    }


    // stop the step counting service and activity detection services
    public fun stopServices(context: ReactApplicationContext) {
        // stop the step counting service
        context.stopService(Intent(context, StepCounterService::class.java))
    }

    public fun getCachePaseoDBHelper() : PaseoDBHelper {
        return paseoDBHelper;
    }
}