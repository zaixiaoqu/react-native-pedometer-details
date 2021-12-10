package com.reactnativepedometerdetails.step


import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.text.TextUtils.substring
import java.util.*



class PaseoDBHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION)
{
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_STEPS_ENTRIES)

        db.execSQL(SQL_CREATE_ACTIVITIES_ENTRIES)
    }



    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database upgrade policy is
        // simply to discard the data and start over
        // db.execSQL(SQL_DELETE_STEPS_ENTRIES)
        //onCreate(db)
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
        val newRowId = db.insert(DBContract.StepsTable.TABLE_NAME, null, values)

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
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            cursor?.close()
            db.close()
            return false
        }

        if (cursor!!.moveToFirst()) {
            rowID = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_DATE_ID))
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



    @Throws(SQLiteConstraintException::class)
    fun deleteSteps(stepsId: String): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase
        // Define 'where' part of query.
        val selection = DBContract.StepsTable.COLUMN_DATE_ID + " LIKE ?"
        // Specify arguments in placeholder order.
        val selectionArgs = arrayOf(stepsId)
        // Issue SQL statement.
        db.delete(DBContract.StepsTable.TABLE_NAME, selection, selectionArgs)

        db.close()

        return true
    }



    // retrieve records from Paseo database for a specific date
    fun readSteps(date: Int): ArrayList<StepsModel> {
        val steps = ArrayList<StepsModel>()
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("select * from " + DBContract.StepsTable.TABLE_NAME + " WHERE " + DBContract.StepsTable.COLUMN_DATE + "='" + date + "'", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return ArrayList()
        }

        var stepsId: Int
        var time: Int
        var startSteps: Int
        var endSteps: Int
        if (cursor!!.moveToFirst()) {
            while (!cursor.isAfterLast) {
                stepsId = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_TIME))
                time = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_TIME))
                startSteps = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_STARTSTEPS))
                endSteps = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_ENDSTEPS))

                steps.add(StepsModel(stepsId, date, time, startSteps, endSteps))
                cursor.moveToNext()
            }
        }

        cursor.close()
        db.close()

        return steps
    }



    // retrieve records from Paseo database for a specific date
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
                theQuery = "SELECT " + DBContract.StepsTable.COLUMN_TIME + ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", " + DBContract.StepsTable.COLUMN_DATE +
                        " FROM " + DBContract.StepsTable.TABLE_NAME +
                        " WHERE " + DBContract.StepsTable.COLUMN_DATE + " = " + date +
                        " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ", " + DBContract.StepsTable.COLUMN_TIME + ""
            }
            "days" -> {
                theQuery = "SELECT " + DBContract.StepsTable.COLUMN_DATE + ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", 0 AS dummy " +
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
                        " CAST(STRFTIME('%W', DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\")) AS int) AS week " +
                        ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", CAST(STRFTIME('%Y', DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                        " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\")) AS int) AS year " +
                        " FROM " + DBContract.StepsTable.TABLE_NAME +
                        " GROUP BY week, year ORDER BY year, week"
            }
            "months" -> {
                theQuery = "SELECT " +
                        " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) AS INT) AS month " +
                        ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                        " FROM " + DBContract.StepsTable.TABLE_NAME +
                        " GROUP BY month, year ORDER BY year, month"
            }
            "years" -> {
                theQuery = "SELECT " +
                        " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                        ", SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps " +
                        ", 0 AS dummy " +
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
                daySteps.add(arrayOf(cursor.getInt(0), cursor.getInt(1), cursor.getInt(2)))
            }
            while (cursor.moveToNext())
            cursor.close()
        }

        cursor.close()
        db.close()

        return daySteps
    }



    // retrieve number of records from Move database
    fun readRowCount(): Int {
        var numRows = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + DBContract.StepsTable.TABLE_NAME, null)
        } catch (e: SQLiteException) {
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



    // retrieve most recent steps date in the Move database
    fun readLastStepsDate(): Int {
        var date = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT " + DBContract.StepsTable.COLUMN_DATE + " FROM " + DBContract.StepsTable.TABLE_NAME +
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return date
        }

        if (cursor!!.moveToFirst()) {
            date = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_DATE))
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
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return time
        }

        if (cursor!!.moveToFirst()) {
            time = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_TIME))
        }

        cursor.close()
        db.close()

        return time
    }



    // retrieve most recent steps date in the Move database
    fun readLastStartSteps(): Int {
        var startSteps = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT " + DBContract.StepsTable.COLUMN_STARTSTEPS + " from " + DBContract.StepsTable.TABLE_NAME +
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return startSteps
        }

        if (cursor!!.moveToFirst()) {
            startSteps = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_STARTSTEPS))
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
                    " ORDER BY " + DBContract.StepsTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return endSteps
        }

        if (cursor!!.moveToFirst()) {
            endSteps = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_ENDSTEPS))
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
                            " WHERE " + DBContract.StepsTable.COLUMN_TIME + " = " + theDate +
                            " AND " + DBContract.StepsTable.COLUMN_DATE + " = " + theDay +
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
                calendar.minimalDaysInFirstWeek = 6

                val theWeek = calendar.get(Calendar.WEEK_OF_YEAR)

                try {
                    cursor = db.rawQuery("SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(STRFTIME('%W', DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                            " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                            " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\")) AS int) AS week " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " WHERE week = " + theWeek +
                            " AND SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) = '" + substring(theDate.toString(), 0, 4) + "'" +
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
            theSteps = cursor.getInt(cursor.getColumnIndex("sumSteps"))
        }

        cursor.close()
        db.close()

        return theSteps
    }


    // retrieve the average number of steps taken in timeUnit (day, week,...)
    fun getAverageSteps(timeUnit: String, theDate: Int, theDay: Int = 0, weekStart: Int = 2): Int {
        var endSteps = 0
        val db = writableDatabase
        var cursor: Cursor? = null

        when (timeUnit) {
            "hours" -> {
                return endSteps
            }
            "days" -> {
                try {
                    cursor = db.rawQuery("SELECT CAST(AVG(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps FROM " +
                            DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY " + DBContract.StepsTable.COLUMN_DATE + ")", null)
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
                    cursor = db.rawQuery("SELECT CAST(AVG(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(STRFTIME('%W', DATE(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) || '-'" +
                            " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) || '-'" +
                            " || SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 7, 2), \"" + dateModifier + "\")) AS int) AS week, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY week, year )", null)

                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return endSteps
                }
            }
            "months" -> {
                try {
                    cursor = db.rawQuery("SELECT CAST(AVG(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 5, 2) AS INT) AS month, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY month, year )", null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return endSteps
                }
            }
            "years" -> {
                try {
                    cursor = db.rawQuery("SELECT CAST(AVG(sumSteps) AS INTEGER) AS maxSteps " +
                            "FROM (SELECT SUM(" + DBContract.StepsTable.COLUMN_ENDSTEPS + " - " +
                            DBContract.StepsTable.COLUMN_STARTSTEPS + ") AS sumSteps, " +
                            " CAST(SUBSTR(" + DBContract.StepsTable.COLUMN_DATE + ", 1, 4) AS INT) AS year " +
                            " FROM " + DBContract.StepsTable.TABLE_NAME +
                            " GROUP BY year )", null)
                } catch (e: SQLiteException) {
                    cursor?.close()
                    db.close()
                    return endSteps
                }
            }
        }

        if (cursor!!.moveToFirst()) {
            endSteps = cursor.getInt(cursor.getColumnIndex("maxSteps"))
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
            endSteps = cursor.getInt(cursor.getColumnIndex("onTarget"))
        }

        cursor.close()
        db.close()

        return endSteps
    }



    fun readAllSteps(): ArrayList<StepsModel> {
        val steps = ArrayList<StepsModel>()
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("select * from " + DBContract.StepsTable.TABLE_NAME, null)
        } catch (e: SQLiteException) {
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return ArrayList()
        }

        var stepsId: Int
        var date: Int
        var time: Int
        var startSteps: Int
        var endSteps: Int
        if (cursor!!.moveToFirst()) {
            while (!cursor.isAfterLast) {
                stepsId = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_DATE_ID))
                date = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_DATE))
                time = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_TIME))
                startSteps = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_STARTSTEPS))
                endSteps = cursor.getInt(cursor.getColumnIndex(DBContract.StepsTable.COLUMN_ENDSTEPS))

                steps.add(StepsModel(stepsId, date, time, startSteps, endSteps))
                cursor.moveToNext()
            }
        }

        cursor.close()
        db.close()

        return steps
    }



    // retrieve a list of recorded activities (running, walking, bicycling)
    fun getActivities(): ArrayList<ActivityModel> {
        val activities = ArrayList<ActivityModel>()
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("select * from " + DBContract.ActivityTypeTable.TABLE_NAME +
                    " ORDER BY " + DBContract.ActivityTypeTable.COLUMN_DATE_ID + " DESC LIMIT 10 " +
                    " OFFSET 0", null)
        }
        catch (e: SQLiteException) {
            db.execSQL(SQL_CREATE_ACTIVITIES_ENTRIES)
            cursor?.close()
            db.close()
            return ArrayList()
        }

        var stepsId: Int
        var date: Int
        var startTime: Int
        var endTime: Int
        var startSteps: Int
        var endSteps: Int
        var activityType: String
        var notes: String
        if (cursor!!.moveToFirst()) {
            while (!cursor.isAfterLast) {
                stepsId = cursor.getInt(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_DATE_ID))
                date = cursor.getInt(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_DATE))
                startTime = cursor.getInt(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_STARTTIME))
                endTime = cursor.getInt(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_ENDTIME))
                startSteps = cursor.getInt(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_STARTSTEPS))
                endSteps = cursor.getInt(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_ENDSTEPS))
                activityType = cursor.getString(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_ACTIVITY_TYPE))
                notes = cursor.getString(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_NOTES))

                activities.add(ActivityModel(stepsId, date, startTime, endTime, startSteps, endSteps, activityType, notes))
                cursor.moveToNext()
            }
        }

        cursor.close()
        db.close()

        return activities
    }



    // retrieve most recent end steps in the Paseo database
    fun readLastActivityEndTime(): Int {
        var endTime = 0
        val db = writableDatabase
        var cursor: Cursor? = null
        try {
            cursor = db.rawQuery("SELECT " + DBContract.ActivityTypeTable.COLUMN_ENDTIME + " from " + DBContract.ActivityTypeTable.TABLE_NAME +
                    " ORDER BY " + DBContract.ActivityTypeTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_STEPS_ENTRIES)
            cursor?.close()
            db.close()
            return endTime
        }

        if (cursor!!.moveToFirst()) {
            endTime = cursor.getInt(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_ENDTIME))
        }

        cursor.close()
        db.close()

        return endTime
    }



    @Throws(SQLiteConstraintException::class)
    fun insertActivity(newActivity: ActivityModel): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase

        // Create a new map of values, where column names are the keys
        val values = ContentValues()
        values.put(DBContract.ActivityTypeTable.COLUMN_DATE, newActivity.date)
        values.put(DBContract.ActivityTypeTable.COLUMN_STARTTIME, newActivity.startTime)
        values.put(DBContract.ActivityTypeTable.COLUMN_ENDTIME, newActivity.endTime)
        values.put(DBContract.ActivityTypeTable.COLUMN_STARTSTEPS, newActivity.startSteps)
        values.put(DBContract.ActivityTypeTable.COLUMN_ENDSTEPS, newActivity.endSteps)
        values.put(DBContract.ActivityTypeTable.COLUMN_ACTIVITY_TYPE, newActivity.activityType)
        values.put(DBContract.ActivityTypeTable.COLUMN_NOTES, newActivity.notes)

        var newRowId= 0L

        try {
            // Insert the new row, returning the primary key value of the new row
            newRowId = db.insertOrThrow(DBContract.ActivityTypeTable.TABLE_NAME, null, values)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_ACTIVITIES_ENTRIES)
            db.close()
            return false
        }

    return true
    }



    @Throws(SQLiteConstraintException::class)
    fun updateActivityEndTime(endTime: Int, endSteps: Int): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase

        var rowID = 0
        val cursor: Cursor?

        // get the most recent detected activity record
        try {
            cursor = db.rawQuery("SELECT " + DBContract.ActivityTypeTable.COLUMN_DATE_ID + " FROM " + DBContract.ActivityTypeTable.TABLE_NAME +
                    " ORDER BY " + DBContract.ActivityTypeTable.COLUMN_DATE_ID + " DESC LIMIT 1", null)
        } catch (e: SQLiteException) {
            return false
        }

        if (cursor!!.moveToFirst()) {
            rowID = cursor.getInt(cursor.getColumnIndex(DBContract.ActivityTypeTable.COLUMN_DATE_ID))
        }

        try {
            db.execSQL("UPDATE " + DBContract.ActivityTypeTable.TABLE_NAME +
                    " SET " + DBContract.ActivityTypeTable.COLUMN_ENDTIME + " = " + endTime +
                    ", " + DBContract.ActivityTypeTable.COLUMN_ENDSTEPS + " = " + endSteps +
                    " WHERE " + DBContract.ActivityTypeTable.COLUMN_DATE_ID + " = " + rowID)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_ACTIVITIES_ENTRIES)
            cursor.close()
            db.close()
            return false
        }

        cursor.close()
        db.close()

        return true
    }



    @Throws(SQLiteConstraintException::class)
    fun updateActivity(date: Int, startTime: Int, activityType: String, notes: String): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase

        val cursor: Cursor? = null

        try {
            db.execSQL("UPDATE " + DBContract.ActivityTypeTable.TABLE_NAME +
                    " SET " + DBContract.ActivityTypeTable.COLUMN_ACTIVITY_TYPE + " = \"" + activityType +
                    "\", " + DBContract.ActivityTypeTable.COLUMN_NOTES + " = \"" + notes +
                    "\" WHERE " + DBContract.ActivityTypeTable.COLUMN_DATE + " = " + date +
                    " AND " + DBContract.ActivityTypeTable.COLUMN_STARTTIME + " = " + startTime)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_ACTIVITIES_ENTRIES)
            cursor?.close()
            db.close()
            return false
        }

        cursor?.close()
        db.close()

        return true
    }



    fun deleteLastActivity(): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase

        try {
            db.execSQL("DELETE FROM " + DBContract.ActivityTypeTable.TABLE_NAME +
                    " WHERE " + DBContract.ActivityTypeTable.COLUMN_DATE_ID +
                    " = (SELECT MAX(" + DBContract.ActivityTypeTable.COLUMN_DATE_ID + ") FROM " +
                    DBContract.ActivityTypeTable.TABLE_NAME)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_ACTIVITIES_ENTRIES)
            db.close()
            return false
        }
        return true
    }



    fun deleteShortActivity(shortTime: Int): Boolean {
        // Gets the data repository in write mode
        val db = writableDatabase

        try {
            db.execSQL("DELETE FROM " + DBContract.ActivityTypeTable.TABLE_NAME +
                    " WHERE " + DBContract.ActivityTypeTable.COLUMN_DATE_ID +
                    " = (SELECT MAX(" + DBContract.ActivityTypeTable.COLUMN_DATE_ID + ") " +
                    " FROM " + DBContract.ActivityTypeTable.TABLE_NAME + ") AND " +
                    DBContract.ActivityTypeTable.COLUMN_ENDTIME + " - " + DBContract.ActivityTypeTable.COLUMN_STARTTIME +
                    " < " + shortTime * 100)
        } catch (e: SQLiteException) {
            // if table not yet present, create it
            db.execSQL(SQL_CREATE_ACTIVITIES_ENTRIES)
            db.close()
            return false
        }
        db.close()
        return true
    }



    companion object {
        // If change in database schema, must increment the database version.
        val DATABASE_VERSION = 2
        val DATABASE_NAME = "paseoDB.db"

        private val SQL_CREATE_STEPS_ENTRIES =
                "CREATE TABLE IF NOT EXISTS " + DBContract.StepsTable.TABLE_NAME + " (" +
                        DBContract.StepsTable.COLUMN_DATE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                        DBContract.StepsTable.COLUMN_DATE + " TEXT," +
                        DBContract.StepsTable.COLUMN_TIME + " TEXT," +
                        DBContract.StepsTable.COLUMN_STARTSTEPS + " TEXT," +
                        DBContract.StepsTable.COLUMN_ENDSTEPS + " TEXT)"

        private val SQL_DELETE_STEPS_ENTRIES = "DROP TABLE IF EXISTS " + DBContract.StepsTable.TABLE_NAME

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

        private val SQL_DELETE_ACTIVITIES_ENTRIES = "DROP TABLE IF EXISTS " + DBContract.ActivityTypeTable.TABLE_NAME
    }
}
