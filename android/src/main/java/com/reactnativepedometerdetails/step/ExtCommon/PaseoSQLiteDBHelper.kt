package com.reactnativepedometerdetails.step.ExtCommon


import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils.substring
import com.reactnativepedometerdetails.step.helper.HeaderCommon
import java.text.SimpleDateFormat
import java.util.*



class PaseoDBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION)
{
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_STEPS_ENTRIES)

        db.execSQL(SQL_CREATE_ACTIVITIES_ENTRIES)
    }



    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database upgrade policy is
        if (oldVersion < 3) {

            db.execSQL("ALTER TABLE " + DBContract.StepsTable.TABLE_NAME + " ADD " +
                    DBContract.StepsTable.COLUMN_EDIT + " INTEGER DEFAULT 0")
        }
    }



    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }



    @Throws(SQLiteConstraintException::class)
    fun insertSteps(hours: StepsModel): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase

        // Create a new map of values, where column names are the keys
        val values = ContentValues()
        values.put(DBContract.StepsTable.COLUMN_DATE, hours.date)
        values.put(DBContract.StepsTable.COLUMN_TIME, hours.hour)
        values.put(DBContract.StepsTable.COLUMN_STARTSTEPS, hours.startSteps)
        values.put(DBContract.StepsTable.COLUMN_ENDSTEPS, hours.endSteps)

        // Insert the new row, returning the primary key value of the new row
        db.insert(DBContract.StepsTable.TABLE_NAME, null, values)

        db.close()

        return true
    }



    @Throws(SQLiteConstraintException::class)
    fun updateEndSteps(steps: StepsModel): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase

        var rowID = 0
        var cursor: Cursor? = null

        try {
            cursor = db.rawQuery("SELECT " + DBContract.StepsTable.COLUMN_DATE_ID + " FROM " + DBContract.StepsTable.TABLE_NAME +
                    " WHERE " + DBContract.StepsTable.COLUMN_EDIT + " IS NOT 1" +
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            cursor?.close()
            db.close()
            return false
        }

        if (cursor!!.moveToFirst()) {
            rowID = cursor.getInt(cursor.getColumnIndexOrThrow(DBContract.StepsTable.COLUMN_DATE_ID))
        }

        try {
            db.execSQL("UPDATE " + DBContract.StepsTable.TABLE_NAME + " SET " + DBContract.StepsTable.COLUMN_ENDSTEPS + " = " + steps.endSteps +
                    " WHERE " + DBContract.StepsTable.COLUMN_DATE_ID + " = " + rowID)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor.close()
            db.close()
            return false
        }

        cursor.close()
        db.close()

        return true
    }



    // get the number of steps taken in each hour of a day
    fun getDaysSteps(date: Int): Int {

        var daySteps = 0
        val db = writableDatabase
        var cursor: Cursor? = null

        try {
            cursor = db.rawQuery("SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                    DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                    " FROM " + DBContract.StepsTable.TABLE_NAME +
                    " WHERE " + DBContract.StepsTable.COLUMN_DATE + " = " + date +
                    " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + "", null)
        } catch (e: SQLiteException) {
            cursor?.close()
            db.close()
            return daySteps
        }

        if (cursor!!.moveToFirst()) {
            daySteps = cursor.getInt(0)
        }

        cursor.close()
        db.close()

        return daySteps
    }



    // get the number of steps taken in each hour of a day (or day of year, or week of year, or month of year)
    fun getStepsByTimeUnit(date: Int, timeUnit: String, asc: Boolean = true, weekStart: Int = 2): ArrayList<Array<Int>> {

        val daySteps = ArrayList<Array<Int>>()
        val db = writableDatabase
        val cursor: Cursor?
        var theQuery = ""

        when (timeUnit) {
            "hours" -> {
                theQuery = "SELECT CAST(" + DBContract.StepsTable.COLUMN_TIME + " AS INTEGER) AS  theHour" +
                        ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", " + DBContract.StepsTable.COLUMN_DATE +
                        ", max(edit) as edited" +
                        " FROM " + DBContract.StepsTable.TABLE_NAME +
                        " WHERE " + DBContract.StepsTable.COLUMN_DATE + " = " + date +
                        " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ", " + DBContract.StepsTable.COLUMN_TIME +
                        " ORDER BY theHour"
            }
            "days" -> {
                theQuery = "SELECT " + DBContract.StepsTable.COLUMN_DATE + ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", 0 AS dummy " +
                        ", max(edit) as edited" +
                        " FROM " + DBContract.StepsTable.TABLE_NAME +
                        " GROUP BY " + DBContract.StepsTable.COLUMN_DATE // +
            }
            "weeks" -> {
                // start of week is Monday (default)
                var dateModifier = "+0 day"

                //  also need to add one day (in the sql query) as SQLite always considers Monday as the first day
                if (weekStart != 2) {
                    dateModifier = "+1 day"
                }

                theQuery = "SELECT " +
                        "CAST(strftime('%W', start_of_week) AS INTEGER) AS week, " +
                        "SUM(endSteps - startSteps) AS sumSteps, " +
                        "strftime('%Y', start_of_week) AS year, " +
                        "MAX(edit) as edited, start_of_week " +
                        "FROM " +
                        "(select " +
                        DBContract.StepsTable.COLUMN_ENDSTEPS + " AS endSteps, " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + " AS startSteps, " +
                        DBContract.StepsTable.COLUMN_EDIT + ", " +
                        "date(DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\"), 'weekday 0', '-6 days') AS start_of_week " +
                        "from hours) " +
                        "GROUP BY week, year ORDER BY year, week"
            }
            "months" -> {
                theQuery = "SELECT " +
                        " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) AS INT) AS month " +
                        ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                        ", max(edit) as edited" +
                        " FROM " + DBContract.StepsTable.TABLE_NAME +
                        " GROUP BY month, year ORDER BY year, month"
            }
            "years" -> {
                theQuery = "SELECT " +
                        " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                        ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", 0 AS dummy " +
                        ", max(edit) as edited" +
                        " FROM " + DBContract.StepsTable.TABLE_NAME +
                        " GROUP BY year"
            }
        }
        try {
            cursor = db.rawQuery(theQuery, null)
        } catch (e: SQLiteException) {
            return daySteps
        }

        // create an arrayList storing the steps by hour for this day
        if (cursor != null && cursor.moveToFirst()) {

            //add row to list
            do {
                daySteps.add(arrayOf(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3)))
            }
            while (cursor.moveToNext())
            cursor.close()
        }

        cursor.close()
        db.close()

        return daySteps
    }



    // get the number of steps taken in each hour of a day (two fields, the recorded number of steps, the user entered number of steps)
    fun getEditedStepsByHour(date: Int, asc: Boolean = true): ArrayList<Array<Int>> {

        val editedSteps = ArrayList<Array<Int>>()
        val db = writableDatabase
        val cursor: Cursor?
        val theQuery = "SELECT CAST(" + DBContract.StepsTable.COLUMN_TIME + " AS INTEGER) AS  theHour" +
                ", SUM((" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " + DBContract.StepsTable.COLUMN_STARTSTEPS + ") * " +
                "(" + DBContract.StepsTable.COLUMN_EDIT + " IS NOT 1)) AS recordedSteps " +
                ", SUM((" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " + DBContract.StepsTable.COLUMN_STARTSTEPS  + ") * " +
                "(" + DBContract.StepsTable.COLUMN_EDIT + " = 1)) AS editedSteps " +
                ", " + DBContract.StepsTable.COLUMN_DATE +
                ", max(" + DBContract.StepsTable.COLUMN_EDIT + ") " +
                " FROM " + DBContract.StepsTable.TABLE_NAME +
                " WHERE " + DBContract.StepsTable.COLUMN_DATE + " = " + date +
                " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ", theHour ORDER BY theHour"

        try {
            cursor = db.rawQuery(theQuery, null)
        } catch (e: SQLiteException) {
            return editedSteps
        }

        // create an arrayList storing the steps by hour for this day
        if (cursor != null && cursor.moveToFirst()) {

            //add row to list
            do {
                editedSteps.add(arrayOf(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2), cursor.getInt(3), cursor.getInt(4)))
            }
            while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return editedSteps
    }



    // update or insert user edited steps into database
    @Throws(SQLiteConstraintException::class)
    fun insertEditedSteps(hours: StepsModel): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase
        val cursor: Cursor?
        val rowID: Int

        // get the edited steps record to be updated (if it exists)
        try {
            cursor = db.rawQuery("SELECT " + DBContract.StepsTable.COLUMN_DATE_ID +
                    " FROM " + DBContract.StepsTable.TABLE_NAME +
                    " WHERE " + DBContract.StepsTable.COLUMN_DATE + " = " + hours.date +
                        " AND " + DBContract.StepsTable.COLUMN_TIME + " = " + hours.hour +
                        " AND " + DBContract.StepsTable.COLUMN_EDIT + " = 1 " +
                    " ORDER BY " + DBContract.ActivityTypeTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)

        } catch (e: SQLiteException) {
            return false
        }

        // Create a new map of values, where column names are the keys
        val values = ContentValues()
        values.put(DBContract.StepsTable.COLUMN_DATE, hours.date)
        values.put(DBContract.StepsTable.COLUMN_TIME, hours.hour)
        values.put(DBContract.StepsTable.COLUMN_STARTSTEPS, hours.startSteps)
        values.put(DBContract.StepsTable.COLUMN_ENDSTEPS, hours.endSteps)
        values.put(DBContract.StepsTable.COLUMN_EDIT, 1)

        if (cursor!!.moveToFirst()) {
            rowID = cursor.getInt(cursor.getColumnIndexOrThrow(DBContract.StepsTable.COLUMN_DATE_ID))

            // delete the record if edited steps = 0 (edit is being reverted)
            if (hours.endSteps == hours.startSteps) {
                deleteEditedSteps(rowID.toString())
            }
            else {
                // Insert the new row, returning the primary key value of the new row
                db.update(DBContract.StepsTable.TABLE_NAME, values, "_id=?", arrayOf(rowID.toString()))
            }
        }
        else {
            // Insert the new row, returning the primary key value of the new row
            db.insert(DBContract.StepsTable.TABLE_NAME, null, values)
        }

        cursor.close()
        db.close()

        return true
    }



    @Throws(SQLiteConstraintException::class)
    fun deleteEditedSteps(stepsId: String): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase
        // Define 'where' part of query.
        val selection = DBContract.StepsTable.COLUMN_DATE_ID + " LIKE ? AND edit = 1"
        // Specify arguments in placeholder order.
        val selectionArgs = arrayOf(stepsId)
        // Issue SQL statement.
        db.delete(DBContract.StepsTable.TABLE_NAME, selection, selectionArgs)

        db.close()

        return true
    }



    // retrieve number of records from Paseo database
    fun readRowCount(): Int {
        var numRows = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + DBContract.StepsTable.TABLE_NAME, null)
        }
        catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return numRows
        }

        if (cursor!!.moveToFirst()) {
            numRows = cursor.getInt(0)
        }
        cursor.close()
        db.close()

        return numRows
    }



    // retrieve most recent steps date in the Paseo database
    fun readLastStepsDate(): Int {
        var date = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT " + DBContract.StepsTable.COLUMN_DATE + " FROM " + DBContract.StepsTable.TABLE_NAME +
                    " WHERE edit IS NOT 1 " +
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return date
        }

        if (cursor!!.moveToFirst()) {
            date = cursor.getInt(cursor.getColumnIndexOrThrow(DBContract.StepsTable.COLUMN_DATE))
        }

        cursor.close()
        db.close()

        return date
    }



    // retrieve most recent steps time in the Paseo database
    fun readLastStepsTime(): Int {
        var time = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT " + DBContract.StepsTable.COLUMN_TIME + " FROM " + DBContract.StepsTable.TABLE_NAME +
                    " WHERE edit IS NOT 1 " +
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return time
        }

        if (cursor!!.moveToFirst()) {
            time = cursor.getInt(cursor.getColumnIndexOrThrow(DBContract.StepsTable.COLUMN_TIME))
        }

        cursor.close()
        db.close()

        return time
    }



    // retrieve most recent steps date in the Paseo database
    fun readLastStartSteps(): Int {
        var startSteps = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT " + DBContract.StepsTable.COLUMN_STARTSTEPS + " FROM " + DBContract.StepsTable.TABLE_NAME +
                    " WHERE edit IS NOT 1 " +
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return startSteps
        }

        if (cursor!!.moveToFirst()) {
            startSteps = cursor.getInt(cursor.getColumnIndexOrThrow(DBContract.StepsTable.COLUMN_STARTSTEPS))
        }

        cursor.close()
        db.close()

        return startSteps
    }



    // retrieve most recent end steps in the Paseo database
    fun readLastEndSteps(): Int {
        var endSteps = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT " + DBContract.StepsTable.COLUMN_ENDSTEPS + " from " + DBContract.StepsTable.TABLE_NAME +
                    " WHERE edit IS NOT 1 " +
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return endSteps
        }

        if (cursor!!.moveToFirst()) {
            endSteps = cursor.getInt(cursor.getColumnIndexOrThrow(DBContract.StepsTable.COLUMN_ENDSTEPS))
        }

        cursor.close()
        db.close()

        return endSteps
    }



    // retrieve the total number of steps taken in timeUnit (day, week,...)
    fun getSteps(timeUnit: String, theDate: Int, theDay: Int = 0, weekStart: Int = 2): Int {
        var theSteps = 0
        val db = writableDatabase
        var cursor: Cursor? = null

        when (timeUnit) {
            "hours" -> {
                try {
                    cursor = db.rawQuery("SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " WHERE " + DBContract.StepsTable.COLUMN_TIME + " = " + theDay +
                            " AND " + DBContract.StepsTable.COLUMN_DATE + " = " + theDate +
                            " GROUP BY " + DBContract.StepsTable.COLUMN_TIME + "", null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return theSteps
                }
            }

            "days" -> {
                try {
                    cursor = db.rawQuery("SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " WHERE " + DBContract.StepsTable.COLUMN_DATE + " = " + theDate +
                            " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + "", null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return theSteps
                }
            }

            "weeks" -> {
                // use a UK calendar so that start of week is Monday (default)
                var calendar = Calendar.getInstance(Locale.UK)
                var dateModifier = "+0 day"

                // use a CANADA calendar so that start of week is Sunday
                //  also need to add one day (in the sql query) as SQLite always considers Monday as the first day
                if (weekStart != 2) {
                    calendar = Calendar.getInstance(Locale.CANADA)
                    dateModifier = "+1 day"
                }
                calendar.time = Date()
                calendar.time = HeaderCommon.getFirstDayInWeek(weekStart)
                calendar.minimalDaysInFirstWeek = 6

                val theWeek = calendar.get(Calendar.WEEK_OF_YEAR)
                val theYear = calendar.get(Calendar.YEAR)

                try {
                    cursor = db.rawQuery("SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(STRFTIME('%W', DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                            " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                            " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\")) AS int) AS week " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " WHERE week = " + theWeek +
                            " AND SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) = '" + theYear + "'" +
                            " GROUP BY week ", null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return theSteps
                }
            }

            "months" -> {
                try {
                    cursor = db.rawQuery("SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) AS INT) AS month " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " WHERE month = SUBSTR(" + theDate + ", 5, 2) " +
                            " AND SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) = '" + substring(theDate.toString(), 0, 4) + "'" +
                            " GROUP BY month ", null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return theSteps
                }
            }
            "years" -> {
                try {
                    cursor = db.rawQuery("SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " WHERE year = SUBSTR(" + theDate + ", 1, 4) " +
                            " GROUP BY year ", null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return theSteps
                }
            }
        }

        if (cursor!!.moveToFirst()) {
            theSteps = cursor.getInt(cursor.getColumnIndexOrThrow("sumSteps"))
        }

        cursor.close()
        db.close()

        return theSteps
    }



    // retrieve the maximum number of steps taken in timeUnit (day, week,...)
    fun getMaxSteps(timeUnit: String, theDate: Int, theDay: Int = 0, weekStart: Int = 2, numRecords: Int = 1, recordsTimePeriod: String = "AllTime"): ArrayList<Pair <Int, String>> {
        val maxSteps = ArrayList<Pair <Int, String>>()
        var theSteps: Int
        var recordDate: String
        val db = writableDatabase
        var theQuery = ""
        var cursor: Cursor? = null
        val minDate: Int
        val maxDate: Int
        val timeLimit: String

        val theMonth = theDate.toString().substring(4,6).toInt()
        val theYear = theDate.toString().substring(0,4).toInt()

        val unitLimit = timeUnit to recordsTimePeriod
        var sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

        when (unitLimit) {
            Pair("hours", "Day"), Pair("days", "Day") -> {
                minDate = theDate
                maxDate = theDate
            }
            // *** this does not produce accurate results.  needs to be changed
            //  to first day of week that includes theDate, first day of week + 6 days
            Pair("weeks", "Day") -> {
                minDate = sdf.format(HeaderCommon.getFirstDayInWeek(weekStart)).toInt()
                maxDate = minDate + 6
            }
            Pair("weeks", "Month") -> {
                sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                minDate = sdf.format(HeaderCommon.getFirstDayInWeek(weekStart, theYear, theMonth, 1)).toInt()
                maxDate = sdf.format(HeaderCommon.getFirstDayInWeek(weekStart, theYear, theMonth, 30)).toInt()
            }
            Pair("weeks", "Year") -> {
                sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                minDate = sdf.format(HeaderCommon.getFirstDayInWeek(weekStart, theYear, 1, 1)).toInt()
                maxDate = sdf.format(HeaderCommon.getFirstDayInWeek(weekStart, theYear, 12, 31)).toInt()
            }
            Pair("hours", "Month"), Pair("days", "Month"), Pair("months", "Day"), Pair("months", "Month") -> {
                minDate = theYear * 10000 + theMonth * 100
                maxDate = minDate + 31
            }
            Pair("hours", "Year"), Pair("days", "Year"), Pair("months", "Year"), Pair("years", "Year"),
                    Pair("years", "Day"), Pair("years", "Month") -> {
                minDate = theYear * 10000
                maxDate = (theYear + 1) * 10000
            }
            else -> {
                minDate = 0
                maxDate = 0
            }
        }

        if (minDate == 0) {
            timeLimit = ""
        }
        else {
            timeLimit = " WHERE date >= $minDate AND date <= $maxDate"
        }

        when (timeUnit) {
            "hours" -> {
                theQuery ="SELECT CAST((sumSteps) AS INTEGER) AS maxSteps, " +
                            DBContract.StepsTable.COLUMN_DATE + ", " +
                            DBContract.StepsTable.COLUMN_TIME +
                            " FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps,  " +
                            DBContract.StepsTable.COLUMN_DATE + ", " +
                            DBContract.StepsTable.COLUMN_TIME + " FROM " +
                            DBContract.StepsTable.TABLE_NAME + timeLimit +
                            " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ", " +
                            DBContract.StepsTable.COLUMN_TIME + ")" +
                            " ORDER BY maxSteps DESC LIMIT " + numRecords
            }
            "days" -> {
                theQuery ="SELECT CAST((sumSteps) AS INTEGER) AS maxSteps, " +
                            DBContract.StepsTable.COLUMN_DATE +
                            " FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps,  " +
                            DBContract.StepsTable.COLUMN_DATE + " FROM " +
                            DBContract.StepsTable.TABLE_NAME + timeLimit +
                            " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ")" +
                            " ORDER BY maxSteps DESC LIMIT " + numRecords
            }

            "weeks" -> {
                // start of week is Monday (default)
                var dateModifier = "+0 day"

                //  also need to add one day (in the sql query) as SQLite always considers Monday as the first day
                if (weekStart != 2) {
                    dateModifier = "+1 day"
                }

                theQuery = "SELECT CAST((sumSteps) AS INTEGER) AS maxSteps, " +
                        DBContract.StepsTable.COLUMN_DATE +
                        " FROM (SELECT " +
                        "strftime('%W', start_of_week) AS week, " +
                        "SUM(endSteps - startSteps) AS sumSteps, " +
                        "strftime('%Y', start_of_week) AS year, " +
                        DBContract.StepsTable.COLUMN_DATE + " " +
                        "FROM " +
                        "(select " +
                        DBContract.StepsTable.COLUMN_ENDSTEPS + " AS endSteps, " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + " AS startSteps, " +
                        DBContract.StepsTable.COLUMN_DATE + ", " +
                        "date(DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\"), 'weekday 0', '-6 days') AS start_of_week " +
                        "FROM hours "  + timeLimit + ") " +
                        "GROUP BY week, year ORDER BY year, week) " +
                        "ORDER BY maxSteps DESC LIMIT " + numRecords

            }
            "months" -> {
                theQuery ="SELECT CAST((sumSteps) AS INTEGER) AS maxSteps, " +
                            DBContract.StepsTable.COLUMN_DATE +
                            " FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) AS INT) AS month, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year, " +
                            DBContract.StepsTable.COLUMN_DATE +
                            " FROM " + DBContract.StepsTable.TABLE_NAME + timeLimit +
                            " GROUP BY month, year )" +
                            " ORDER BY maxSteps DESC LIMIT " + numRecords
            }
            "years" -> {
                theQuery ="SELECT CAST((sumSteps) AS INTEGER) AS maxSteps, " +
                            DBContract.StepsTable.COLUMN_DATE +
                            " FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year, " +
                            DBContract.StepsTable.COLUMN_DATE +
                            " FROM " + DBContract.StepsTable.TABLE_NAME + timeLimit +
                            " GROUP BY year )" +
                            " ORDER BY maxSteps DESC LIMIT " + numRecords
            }
        }

        try {
            cursor = db.rawQuery(theQuery, null)
        }
        catch (e: SQLiteException) {
            cursor?.close()
            db.close()
            return maxSteps
        }

        if (cursor != null && cursor.moveToFirst()) {
            //add row to list
            do {
                theSteps = cursor.getInt(cursor.getColumnIndexOrThrow("maxSteps"))
                recordDate = cursor.getString(cursor.getColumnIndexOrThrow("date"))

                // include the hour of the max steps when retrieving hour records
                if (timeUnit == "hours") {
                    recordDate = recordDate + cursor.getString(cursor.getColumnIndexOrThrow("hour"))
                }
                else if (timeUnit == "weeks") {
                    val weekYear = recordDate.substring(0,4).toInt()
                    val weekMonth = recordDate.substring(4,6).toInt()
                    val weekDay = recordDate.substring(6,8).toInt()

                    recordDate = sdf.format(HeaderCommon.getFirstDayInWeek(weekStart, weekYear, weekMonth, weekDay))
                }

                maxSteps.add(theSteps to recordDate)
            }
            while (cursor.moveToNext())
        }

        cursor?.close()
        db.close()

        return maxSteps
    }



    // retrieve the minimum number of steps taken in timeUnit (day, week,...)
    fun getMinSteps(timeUnit: String, theDate: Int, theDay: Int = 0, weekStart: Int = 2): Int {
        var endSteps = 0
        var theQuery = ""
        val db = writableDatabase
        var cursor: Cursor? = null

        when (timeUnit) {
            "hours" -> {
                return endSteps
            }
            "days" -> {
                theQuery ="SELECT CAST(MIN(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps FROM " +
                            DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ")"
            }
            "weeks" -> {
                // start of week is Monday (default)
                var dateModifier = "+0 day"

                //  need to add one day (in the sql query) as SQLite always considers Monday as the first day
                if (weekStart != 2) {
                    dateModifier = "+1 day"
                }

                theQuery = "SELECT CAST(MIN(sumSteps) AS INTEGER) AS maxSteps, " +
                            DBContract.StepsTable.COLUMN_DATE +
                            " FROM (SELECT " +
                            "strftime('%W', start_of_week) AS week, " +
                            "SUM(endSteps - startSteps) AS sumSteps, " +
                            "strftime('%Y', start_of_week) AS year, " +
                            DBContract.StepsTable.COLUMN_DATE + " " +
                            "FROM " +
                            "(select " +
                            DBContract.StepsTable.COLUMN_ENDSTEPS + " AS endSteps, " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + " AS startSteps, " +
                            DBContract.StepsTable.COLUMN_DATE + ", " +
                            "date(DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                            " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                            " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\"), 'weekday 0', '-6 days') AS start_of_week " +
                            "from hours) " +
                            "GROUP BY week, year ORDER BY year, week) "
            }
            "months" -> {
                theQuery ="SELECT CAST(MIN(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) AS INT) AS month, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY month, year )"
            }
            "years" -> {
                theQuery = "SELECT CAST(MIN(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY year )"
            }
        }

        try {
            cursor = db.rawQuery(theQuery, null)
        }
        catch (e: SQLiteException) {
            cursor?.close()
            db.close()
            return endSteps
        }

        if (cursor!!.moveToFirst()) {
            endSteps = cursor.getInt(cursor.getColumnIndexOrThrow("maxSteps"))
        }

        cursor.close()
        db.close()

        return endSteps
    }



    // retrieve the average number of steps taken in timeUnit (day, week,...)
    fun getAverageSteps(timeUnit: String, theDate: Int, theDay: Int = 0, weekStart: Int = 2): Int {
        var endSteps = 0
        var theQuery = ""
        val db = writableDatabase
        var cursor: Cursor? = null

        when (timeUnit) {
            "hours" -> {
                return endSteps
            }
            "days" -> {
                theQuery ="SELECT CAST(AVG(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps FROM " +
                            DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ")"
            }

            "weeks" -> {
                // start of week is Monday (default)
                var dateModifier = "+0 day"

                //  also need to add one day (in the sql query) as SQLite always considers Monday as the first day
                if (weekStart != 2) {
                    dateModifier = "+1 day"
                }

                theQuery = "SELECT CAST(AVG(sumSteps) AS INTEGER) AS maxSteps, " +
                        DBContract.StepsTable.COLUMN_DATE +
                        " FROM (SELECT " +
                        "strftime('%W', start_of_week) AS week, " +
                        "SUM(endSteps - startSteps) AS sumSteps, " +
                        "strftime('%Y', start_of_week) AS year, " +
                        DBContract.StepsTable.COLUMN_DATE + " " +
                        "FROM " +
                        "(select " +
                        DBContract.StepsTable.COLUMN_ENDSTEPS + " AS endSteps, " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + " AS startSteps, " +
                        DBContract.StepsTable.COLUMN_DATE + ", " +
                        "date(DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\"), 'weekday 0', '-6 days') AS start_of_week " +
                        "from hours) " +
                        "GROUP BY week, year ORDER BY year, week) "
             }
            "months" -> {
                theQuery ="SELECT CAST(AVG(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) AS INT) AS month, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY month, year )"
            }
            "years" -> {
                theQuery ="SELECT CAST(AVG(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY year )"
            }
        }

        try {
            cursor = db.rawQuery(theQuery, null)
        } catch (e: SQLiteException) {
            cursor?.close()
            db.close()
            return endSteps
        }

        if (cursor!!.moveToFirst()) {
            endSteps = cursor.getInt(cursor.getColumnIndexOrThrow("maxSteps"))
        }

        cursor.close()
        db.close()

        return endSteps
    }



    // retrieve the total number of timeUnits when target steps was achieved
    fun getOnTarget(timeUnit: String, target: Int, weekStart: Int = 2): Int {
        var endSteps = 0
        val db = writableDatabase
        var cursor: Cursor? = null

        when (timeUnit) {
            "days" -> {
                try {
                    cursor = db.rawQuery(
                            "SELECT count(date) AS onTarget, sumSteps FROM " +
                                    "(SELECT date, SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                                    DBContract.StepsTable.COLUMN_STARTSTEPS + ") as sumSteps FROM " +
                                    DBContract.StepsTable.TABLE_NAME +
                                    " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ") " +
                                    "WHERE sumSteps > " + target, null)

                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return endSteps
                }
            }

            "weeks" -> {
                // start of week is Monday (default)
                var dateModifier = "+0 day"

                //  also need to add one day (in the sql query) as SQLite always considers Monday as the first day
                if (weekStart != 2) {
                    dateModifier = "+1 day"
                }

                try {
                    cursor = db.rawQuery(
                            "SELECT count(week) AS onTarget, sumSteps FROM " +
                                    "(SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                                    DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                                    " CAST(STRFTIME('%W', DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                                    " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                                    " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\")) AS int) AS week " +
                                    ", CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                                    " FROM " + DBContract.StepsTable.TABLE_NAME +
                                    " GROUP BY week, year )" +
                                    "WHERE sumSteps > " + target, null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return endSteps
                }
            }
            "months" -> {
                try {
                    cursor = db.rawQuery(
                            "SELECT count(month) AS onTarget, sumSteps FROM " +
                                    "(SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                                    DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                                    " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) AS INT) AS month " +
                                    ", CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                                    " FROM " + DBContract.StepsTable.TABLE_NAME +
                                    " GROUP BY month, year )" +
                                    "WHERE sumSteps > " + target, null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return endSteps
                }
            }
            "years" -> {
                try {
                    cursor = db.rawQuery(
                            "SELECT count(year) AS onTarget, sumSteps FROM " +
                                    "(SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                                    DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                                    " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                                    " FROM " + DBContract.StepsTable.TABLE_NAME +
                                    " GROUP BY year )" +
                                    "WHERE sumSteps > " + target, null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return endSteps
                }
            }
        }

        if (cursor!!.moveToFirst()) {
            endSteps = cursor.getInt(cursor.getColumnIndexOrThrow("onTarget"))
        }

        cursor.close()
        db.close()

        return endSteps
    }



    // a bug in version 1.4.3 resulted in many records being created in each hour with the same start and end steps
    //  this resulted in paseo showing no steps for these hours.  The steps were recorded. This function restores the
    //  records to how they should have been created
    fun fixBadRecords(): Int
    {
        var endSteps = 0
        var db = writableDatabase
        var cursor: Cursor? = null

        val theQuery = "Select date, hour, MIN(CAST(startSteps AS INTEGER)), MAX(CAST(endSteps AS INTEGER)), " +
                "max(CAST(endSteps AS INTEGER)) - MIN(CAST(startSteps AS INTEGER)) as sumSteps, count(*) from hours " +
                " WHERE date > 20220101 AND date < 20220131 AND startSteps = endSteps GROUP BY date, hour"
        try {
            cursor = db.rawQuery(theQuery, null)
        } catch (e: SQLiteException) {
            cursor?.close()
            db.close()
            return endSteps
        }

        if (cursor != null && cursor.moveToFirst()) {
            //add row to list
            do {
                if (cursor.getInt(5) > 1) {

                    insertSteps(StepsModel(0, date = cursor.getInt(0), hour = cursor.getInt(1),
                            startSteps = cursor.getInt(2), endSteps = cursor.getInt(3)))
                }
            }
            while (cursor.moveToNext())

            // sometimes the database has been closed by this point
            if (!db.isOpen()) {
                db = writableDatabase
            }

            // Define 'where' part of query.
            val selection = DBContract.StepsTable.COLUMN_STARTSTEPS + " = " +
                    DBContract.StepsTable.COLUMN_ENDSTEPS
            // Specify arguments in placeholder order.
            val selectionArgs = null
            // Issue SQL statement.
            db.delete(DBContract.StepsTable.TABLE_NAME, selection, selectionArgs)

        }

        cursor.close()
        db.close()

        return endSteps
    }



    companion object {
        // If change in database schema, must increment the database version.
        val DATABASE_VERSION = 3
        val DATABASE_NAME = "paseoDB.db"

        private val SQL_CREATE_STEPS_ENTRIES =
                "CREATE TABLE IF NOT EXISTS " + DBContract.StepsTable.TABLE_NAME + " (" +
                        DBContract.StepsTable.COLUMN_DATE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                        DBContract.StepsTable.COLUMN_DATE + " TEXT," +
                        DBContract.StepsTable.COLUMN_TIME + " TEXT," +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + " TEXT," +
                        DBContract.StepsTable.COLUMN_ENDSTEPS + " TEXT," +
                        DBContract.StepsTable.COLUMN_EDIT + " INTEGER)"

        private val SQL_CREATE_ACTIVITIES_ENTRIES =
                "CREATE TABLE IF NOT EXISTS " + DBContract.ActivityTypeTable.TABLE_NAME + " (" +
                        DBContract.ActivityTypeTable.COLUMN_DATE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                        DBContract.ActivityTypeTable.COLUMN_DATE + " TEXT," +
                        DBContract.ActivityTypeTable.COLUMN_STARTTIME + " TEXT," +
                        DBContract.ActivityTypeTable.COLUMN_ENDTIME + " TEXT," +
                        DBContract.ActivityTypeTable.COLUMN_STARTSTEPS + " TEXT," +
                        DBContract.ActivityTypeTable.COLUMN_ENDSTEPS + " TEXT," +
                        DBContract.ActivityTypeTable.COLUMN_ACTIVITY_TYPE + " TEXT" +
                        DBContract.ActivityTypeTable.COLUMN_NOTES + " TEXT)"

    }
}
