package com.reactnativepedometerdetails.step.background


import android.app.IntentService
import android.content.Intent
import com.reactnativepedometerdetails.step.ExtCommon.PaseoDBHelper

class DetectedActivitiesIntentService : IntentService(TAG) {

    var active = false  // flag - true if detected activity is currently ongoing
    var lastStartTime = 0L
    var lastEndTime = 0L
    var minPauseTime = 5L * 1000 * 60  // *** this should be set by the user in the PASEO settings screen
    var minActiveTime = 10L * 1000 * 60 //  *** this should be set...
    var currentTime = 0

    val minConfidence = 50


    lateinit var paseoDBHelper : PaseoDBHelper



    override fun onCreate() {
        super.onCreate()

        // point to the Paseo database that stores all the daily steps data
        paseoDBHelper = PaseoDBHelper(this)
    }




    override fun onHandleIntent(intent: Intent?) {
/* *** need to be re-written without any need for google play services (need to write own activity detection)
        val result = ActivityRecognitionResult.extractResult(intent)

        // Get the list of the probable activities associated with the current state of the
        //  device. Each activity is associated with a confidence level between 0 and 100.
        val detectedActivities = result.probableActivities as ArrayList<*>

        // loop through all of the detected activities and process the ones with high confidence
        for (activity in detectedActivities) {
            activity as DetectedActivity

            // only broadcast out the activity with at least the minimum required confidence value
            if (activity.confidence > minConfidence) {
                broadcastActivity(activity)
            }
        }
*/
    }



/* *** need to be re-written without any need for google play services (need to write own activity detection)
    private fun broadcastActivity(activity: DetectedActivity) {
        // send message to application activity so that it can react to new steps being sensed
        val intent = Intent("activity_intent")
        intent.putExtra("type", activity.type)
        intent.putExtra("confidence", activity.confidence)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
    */



    companion object {

        protected val TAG = DetectedActivitiesIntentService::class.java.simpleName
    }


}// Use the TAG to name the worker thread.