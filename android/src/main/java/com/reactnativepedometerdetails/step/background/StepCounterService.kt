package com.reactnativepedometerdetails.step.background

import android.app.*
import android.content.*
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.annotation.Nullable
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.reactnativepedometerdetails.step.ExtCommon.PaseoDBHelper
import com.reactnativepedometerdetails.step.ExtCommon.StepsModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow


class StepCounterService : Service(), SensorEventListener, TextToSpeech.OnInitListener {

//    private val SERVICEID = 1001

    private val FOREGROUND_ID = 1991;
    private val CHANNEL_ID = "step_counter";
    private val CHANNEL_NAME = "StepCounter"
    var running = false
    var hasAccelerometer = true
    var hasStepCounter = true
    var sensorManager: SensorManager? = null
    var startSteps = 0
    var currentSteps = 0
    var endSteps = 0
    private var targetSteps = 10000 // default values for target steps (overridden later from shared preferences)
    var latestDay = 0
    var latestHour = 0

    private var lastAccelData: FloatArray? = floatArrayOf(0f, 0f, 0f)

    lateinit var paseoDBHelper : PaseoDBHelper

    private var tts: TextToSpeech? = null
    private var ttsAvailable = false


    internal var mBinder: IBinder = LocalBinder()

    // set up things for resetting steps (to zero (most of the time) at midnight
    var myPendingIntent: PendingIntent? = null
    var midnightAlarmManager: AlarmManager? = null
    var myBroadcastReceiver: BroadcastReceiver? = null



    inner class LocalBinder : Binder() {
        val serverInstance: StepCounterService
            get() = this@StepCounterService
    }



    @Nullable
    override fun onBind(intent: Intent): IBinder? {
        return mBinder
    }



    override fun onCreate() {
        super.onCreate()
        startForeground()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        running = true
        val stepsSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (stepsSensor == null) {
/*            Toast.makeText(this, "No Step Counter Sensor - dropping down to Accelerometer !", Toast.LENGTH_SHORT).show()
            hasStepCounter = false
            if (accelSensor == null) {
                Toast.makeText(this, "No Accelerometer Sensor !", Toast.LENGTH_SHORT).show()
                hasAccelerometer = false
            } else {
                sensorManager?.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_NORMAL)
            }
 */       } else {
            sensorManager?.registerListener(this, stepsSensor, SensorManager.SENSOR_DELAY_UI, SensorManager.SENSOR_DELAY_UI)
        }

        // point to the Paseo database that stores all the daily steps data
        paseoDBHelper = PaseoDBHelper(this)

        try {
            tts = TextToSpeech(this, this)
        }
        catch (e: Exception) {
            tts = null
        }

        // set the user's target:
        val paseoPrefs = this.getSharedPreferences("ca.chancehorizon.paseo_preferences", 0)
        targetSteps = paseoPrefs!!.getFloat("prefDailyStepsTarget", 10000F).toInt()

        // set up time to reset steps - immediately (10 senconds) after midnight
        val midnightAlarmCalendar: Calendar = Calendar.getInstance()
        midnightAlarmCalendar.set(Calendar.HOUR_OF_DAY, 0)
        midnightAlarmCalendar.set(Calendar.MINUTE, 0)
        midnightAlarmCalendar.set(Calendar.SECOND, 10)

        val midnightAlarmTime = midnightAlarmCalendar.getTimeInMillis()

        // set up the alarm to reset steps after midnight
        registerMyAlarmBroadcast()

        // set alarm to repeat every day (as in every 24 hours, which should be every day immediately after midnight)
        midnightAlarmManager?.setRepeating(AlarmManager.RTC, midnightAlarmTime, AlarmManager.INTERVAL_DAY, myPendingIntent)
    }



    override fun onInit(status: Int) {

        val paseoPrefs = this.getSharedPreferences("ca.chancehorizon.paseo_preferences", 0)

        // set up the text to speech voice
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported")
            }

            ttsAvailable = true
        } else {
            Log.e("TTS", "Initialization failed")
            ttsAvailable = false
        }

        // update shared preferences to not show first run dialog again
        val edit: SharedPreferences.Editor = paseoPrefs!!.edit()
        edit.putBoolean("prefTTSAvailable", ttsAvailable)
        edit.apply()

        // make sure that Paseo's widget and notification have up to date steps shown
        updatePaseoWidget()
        updatePaseoNotification()
    }



    // set up the alarm to reset steps after midnight (shown in widget and notification)
    //
    // this is done so that the number of steps shown on a new day when no steps have yet been taken (sensed)
    //  is reset to zero, rather than showing yesterday's step total (the number of steps shown is only updated when steps are sensed)
    private fun registerMyAlarmBroadcast() {

        //This is the call back function(BroadcastReceiver) which will be call when your
        //alarm time will reached.
        myBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updatePaseoWidget()
                updatePaseoNotification()
            }
        }

        registerReceiver(myBroadcastReceiver, IntentFilter("ca.chancehorizon.paseo"))
        myPendingIntent = PendingIntent.getBroadcast(this.getApplicationContext(), 0, Intent("ca.chancehorizon.paseo"), PendingIntent.FLAG_IMMUTABLE)
        midnightAlarmManager = this.getSystemService(ALARM_SERVICE) as AlarmManager
    }

    private fun UnregisterAlarmBroadcast() {
        midnightAlarmManager?.cancel(myPendingIntent)
        baseContext.unregisterReceiver(myBroadcastReceiver)
    }



    override fun onDestroy() {

        // turn off step counter service
        stopForeground(true)

        val paseoPrefs = this.getSharedPreferences("ca.chancehorizon.paseo_preferences", 0)

        val restartService = paseoPrefs!!.getBoolean("prefRestartService", true)

        // turn off auto start service
        if (restartService) {
            val broadcastIntent = Intent()
            broadcastIntent.action = "restartservice"
            broadcastIntent.setClass(this, Restarter::class.java)
            this.sendBroadcast(broadcastIntent)
        }

        // Shutdown TTS
        if (tts != null) {
            tts!!.stop()
            tts!!.shutdown()
        }

        sensorManager?.unregisterListener(this)

        UnregisterAlarmBroadcast()

        super.onDestroy()
    }



    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }



    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun startForeground() {
        val channelId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    createNotificationChannel()
                } else {
                    ""
                }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
        val notification = notificationBuilder.setOngoing(true)
                .setContentTitle(getAppName())
                .setSmallIcon(applicationInfo.icon)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()
        startForeground(FOREGROUND_ID, notification)
    }

    private fun getAppName(): String {
        val stringId = applicationInfo.labelRes
        return if (stringId == 0) applicationInfo.nonLocalizedLabel.toString() else this.getString(stringId)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(): String{
        val channelId = CHANNEL_ID
        val channelName = CHANNEL_NAME
        val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
        chan.lightColor = Color.BLUE
        chan.importance = NotificationManager.IMPORTANCE_NONE
        chan.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        val service = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        service.createNotificationChannel(chan)
        return channelId
    }

    // needed for step counting (even though it is empty
    override fun onAccuracyChanged(p0: Sensor?, p1: Int)
    {
    }



    // this function is triggered whenever there is an event on the device's step counter sensor
    //  (steps have been detected)
    override fun onSensorChanged(event: SensorEvent)
    {
        if (running)
        {
            var dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault()) // looks like "19891225"
            val today = dateFormat.format(Date()).toInt()
            dateFormat = SimpleDateFormat("HH", Locale.getDefault())
            val currentHour = dateFormat.format(Date()).toInt()

            if (hasStepCounter) {
                // read the step count value from the devices step counter sensor
                currentSteps = event.values[0].toInt()
            }

            // *** experimental code for using accelerometer to detect steps on devices that do not hav
            //  a step counter sensor (currently unused as Paseo will not install on such a device -
            //   based on settings in manifest)
            else if (hasAccelerometer && event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                lastAccelData = lowPassFilter(event.values, lastAccelData)
                val accelData = AccelVector(lastAccelData!!)

                if (accelData.accelVector > 12.5f) {
                    if (LASTDETECTION == NOSTEPDETECTED) {
                        currentSteps = paseoDBHelper.readLastEndSteps() + 1
                    }
                    LASTDETECTION = STEPDETECTED
                } else {
                    LASTDETECTION = NOSTEPDETECTED
                }
            }
            // ***


            // get the latest step information from the database
            if (paseoDBHelper.readRowCount() > 0) {
                latestDay = paseoDBHelper.readLastStepsDate()
                latestHour = paseoDBHelper.readLastStepsTime()
                startSteps = paseoDBHelper.readLastStartSteps()
                endSteps = paseoDBHelper.readLastEndSteps()
            }

            // hour is one more than last hour recorded -> add new hour record to database
            if(today == latestDay && currentHour == latestHour + 1 && currentSteps >= startSteps) {
                addSteps(today, currentHour, endSteps, currentSteps)
            }
            // add a new hour record (may be for current day or for a new day)
            //  also add a new record if the current steps is less than the most recent start steps (happens when phone has been rebooted)
            else if (today != latestDay || currentHour != latestHour || currentSteps < startSteps) {
                addSteps(today, currentHour, currentSteps, currentSteps)
            }
            else {
                //  set endSteps to current steps (update the end steps for current hour)
                addSteps(today, currentHour, 0, currentSteps, true)
            }

            // retrieve today's step total
            val theSteps = paseoDBHelper.getDaysSteps(today)

            updatePaseoWidget()

            updatePaseoNotification()

            // check if the user has a mini goal running and update all of the values needed
            checkMiniGoal()

            // send message to application activity so that it can react to new steps being sensed
            val local = Intent()

            local.action = "ca.chancehorizon.paseo.action"
            local.putExtra("data", theSteps)
            this.sendBroadcast(local)
        }
    }



    // update the number of steps shown in Paseo's widget
    private fun updatePaseoWidget() {

    }



    // update the number of steps shown in Paseo's notification
    private fun updatePaseoNotification() {
    }



    // *** used for detecting steps with an accelerometer sensor (on devices that do not have a step sensor)
    private fun lowPassFilter(input: FloatArray?, prev: FloatArray?): FloatArray? {
        val alpha = 0.1f
        if (input == null || prev == null) {
            return null
        }
        for (i in input.indices) {
            prev[i] = prev[i] + alpha * (input[i] - prev[i])
        }
        return prev
    }
    // ***



    // update mini goal fragement and speak alerts when goal or step interval achieved
    fun checkMiniGoal() {

        val paseoPrefs = this.getSharedPreferences("ca.chancehorizon.paseo_preferences", 0)

        val isGoalActive = paseoPrefs!!.getBoolean("prefMiniGoalActive", false)
        val useDaySteps = paseoPrefs.getBoolean("prefDaySetpsGoal", false)
        val continueAnnouncing = paseoPrefs.getBoolean("prefContinueAnnounce", false)

        // continue to announce mini goal progress if the goal is active (not yet achieved)
        //  or the user has chosen to continue announcements beyond the goal being met
        if (isGoalActive || continueAnnouncing) {

            // get the mini goal steps amount
            val miniGoalSteps = paseoPrefs.getInt("prefMiniGoalSteps", 20)

            // get the mini goal interval for text to speech announcements
            val miniGoalAlertInterval = paseoPrefs.getInt("prefMiniGoalAlertInterval", 0)

            // get the number of steps at which the next announcement will be spoken
            var miniGoalNextAlert = paseoPrefs.getInt("prefMiniGoalNextAlert", 0)

            // load the number of steps in this day at which the mini goal was started
            val miniGoalStartSteps = paseoPrefs.getInt("prefMiniGoalStartSteps", 0)

            val stepCount : Int

            // load the current number on the devices step counter sensor
            val miniGoalEndSteps = paseoDBHelper.readLastEndSteps()

            // display goal step count
            // default to the steps starting at zero (or use the day's set count, if user has set that option)
            if (!useDaySteps) {
                stepCount = miniGoalEndSteps - miniGoalStartSteps
            }
            // or get the current day's steps
            else {
                stepCount = paseoDBHelper.getDaysSteps(SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date()).toInt())

                // start the alert steps at the beginning number of steps for the current day
                if (miniGoalNextAlert < stepCount - miniGoalAlertInterval && miniGoalAlertInterval > 0) {
                    miniGoalNextAlert = ((stepCount + miniGoalAlertInterval)/miniGoalAlertInterval - 1) * miniGoalAlertInterval
                }
            }

            // check if mini goal has been achieved and congratulate the user if it has
            if (stepCount >= miniGoalSteps && isGoalActive) {
                // update shared preferences to flag that there is no longer a mini goal running
                val edit: SharedPreferences.Editor = paseoPrefs.edit()
                edit.putBoolean("prefMiniGoalActive", false)
                edit.apply()

                speakOut("Congratulations on $miniGoalSteps steps!")

                // even though the goal has been achieved, update the next alert steps when the user
                //  has chosen to continue announcements beyond the goal
                if (continueAnnouncing) {
                    miniGoalNextAlert = miniGoalNextAlert + miniGoalAlertInterval
                    edit.putInt("prefMiniGoalNextAlert", miniGoalNextAlert)
                    edit.apply()
                }
            }

            // mini goal not yet achieved (or user has chosen announcing to continue), announce mini goal progress at user selected interval
            else if ((stepCount >= miniGoalNextAlert) && miniGoalAlertInterval > 0) {
                // update shared preferences to save the next alert steps
                val edit: SharedPreferences.Editor = paseoPrefs.edit()

                speakOut("$miniGoalNextAlert steps!")

                // set the next step count for an announcement
                miniGoalNextAlert = miniGoalNextAlert + miniGoalAlertInterval
                edit.putInt("prefMiniGoalNextAlert", miniGoalNextAlert)
                edit.apply()
            }
        }
    }



    // insert or update a steps record in the Move database
    fun addSteps(date: Int = 0, time: Int = 0, startSteps: Int = 0, endSteps: Int = 0, update: Boolean = false)
    {
        // update the endsteps for the current hour
        if (update)
        {
            var result = paseoDBHelper.updateEndSteps(
                    StepsModel(0, date = date, hour = time,
                            startSteps = startSteps, endSteps = endSteps)
            )
        }
        else
        {
            var result = paseoDBHelper.insertSteps(StepsModel(0, date = date, hour = time,
                    startSteps = startSteps, endSteps = endSteps))
        }

        latestDay = date
    }


    // use text to speech to "speak" some text
    fun speakOut(theText : String) {

        val paseoPrefs = this.getSharedPreferences("ca.chancehorizon.paseo_preferences", 0)

        val ttsPitch = paseoPrefs!!.getFloat("prefVoicePitch", 100F)
        val ttsRate = paseoPrefs.getFloat("prefVoiceRate", 100F)

        // set the voice to use to speak with
        val ttsVoice = paseoPrefs.getString("prefVoiceLanguage", "en_US - en-US-language")
        val ttsLocale1 = ttsVoice!!.substring(0, 2)
        val ttsLocale2 = ttsVoice.substring(3)
        val voiceobj = Voice(ttsVoice, Locale(ttsLocale1, ttsLocale2), 1, 1, false, null)
        tts?.voice = voiceobj

        tts?.setPitch(ttsPitch / 100)
        tts?.setSpeechRate(ttsRate / 100)

        var ttsResult = tts?.speak(theText, TextToSpeech.QUEUE_FLUSH, null, "")

        if(ttsResult == -1) {
            tts = TextToSpeech(this, this)
            ttsResult = tts?.speak(theText, TextToSpeech.QUEUE_FLUSH, null, "")
        }
    }

    companion object {
        private const val STEPDETECTED = 1
        private const val NOSTEPDETECTED = 0
        private var LASTDETECTION = NOSTEPDETECTED
    }
}



// *** used to detect steps with accelermeter sensor (on devices that do not have a step sensor)
//  get the full acceleration vector from the accelaration on each axis direction
class AccelVector(accelEvent: FloatArray) {
    var accelX: Float
    var accelY: Float
    var accelZ: Float
    var accelVector: Double

    // calculate the full acceleration vector (kind of like calculating the hypotenuse in 3 dimensions)
    init {
        accelX = accelEvent[0]
        accelY = accelEvent[1]
        accelZ = accelEvent[2]
        accelVector = Math.sqrt((accelX.pow(2) + accelY.pow(2) + accelZ.pow(2)).toDouble())
    }
}
// ***