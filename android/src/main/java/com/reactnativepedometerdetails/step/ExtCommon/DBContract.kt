package com.reactnativepedometerdetails.step.ExtCommon

import android.provider.BaseColumns

object DBContract {

    // Inner class that defines the table contents
    class StepsTable : BaseColumns {
        companion object {
            val TABLE_NAME = "hours"
            val COLUMN_DATE_ID = "_id"
            val COLUMN_DATE = "date"
            val COLUMN_TIME = "hour"
            val COLUMN_STARTSTEPS = "startSteps"
            val COLUMN_ENDSTEPS = "endSteps"
            val COLUMN_EDIT = "edit"
        }
    }

    // Inner class that defines the table contents
    class ActivityTypeTable : BaseColumns {
        companion object {
            val TABLE_NAME = "activityType"
            val COLUMN_DATE_ID = "_id"
            val COLUMN_DATE = "date"
            val COLUMN_STARTTIME = "startTime"
            val COLUMN_ENDTIME = "endTime"
            val COLUMN_STARTSTEPS = "startSteps"
            val COLUMN_ENDSTEPS = "endSteps"
            val COLUMN_ACTIVITY_TYPE = "activityType"
            val COLUMN_NOTES = "notes"
        }
    }
}