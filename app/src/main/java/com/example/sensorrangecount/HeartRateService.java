package com.example.sensorrangecount;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HeartRateService extends Service implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private PowerManager.WakeLock wakeLock;
    private Vibrator vibrator;
    private List<Integer> heartRateList = MainActivity.heartRateList;
    public static boolean isResting = true;
    private static final String CHANNEL_ID = "HeartRateServiceChannel";
    private static final String HEART_URL = "http://175.197.201.115:9000/heartrate/heartrate";


    @Override
    public void onCreate() {
        super.onCreate();
        setupHeartRateSensor();
        createNotificationChannel();
        acquireWakeLock();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification());
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (heartRateSensor != null) {
            sensorManager.unregisterListener(this, heartRateSensor);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int heartRate = (int) event.values[0];
            Log.d("TAG___", "심박수: " + heartRate);

            // 시간
            long currentTimeMillis = System.currentTimeMillis();
            String heartRateLogTime = formatDate(currentTimeMillis);
            Log.d("TAG___", "시간: " + heartRateLogTime);

            if (heartRate != 0) {
                sendHeartRateToServer(heartRate, heartRateLogTime);

                if (heartRateList.size() < 60) {
                    heartRateList.add(heartRate);
                }

                double average = calculateAverage(heartRateList);
                Log.d("TAG___", "평균 심박수 : " + average);
                double threshold = average * 0.93;

                if (!isResting && heartRate < threshold) {
                    vibrateAndShowNotification();
                    MainActivity.sendEmergencyNoti();
                    Log.d("알람","알람울림");
                }

                // 심박수 데이터를 브로드캐스트로 전송
                Intent intent = new Intent("HEART_RATE_UPDATE");
                intent.putExtra("heartRate", heartRate);
                intent.putExtra("heartRateLogTime", heartRateLogTime);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void setupHeartRateSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            Log.e("TAG___", "Heart Rate Sensor not available");
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Heart Rate Monitoring")
                .setContentText("Monitoring your heart rate...")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSmallIcon(R.drawable.ic_launcher_foreground);
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Heart Rate Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::HeartRateWakelock");
        if (wakeLock != null) {
            wakeLock.acquire();
        }
    }

    private void sendHeartRateToServer(int heartRate, String heartRateLogTime) {
        new Thread(() -> {
            try {
                URL url = new URL(HEART_URL);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String jsonInputString = "{\"userId\": \"" + MainActivity.userId + "\", \"heartrate\": " + heartRate + ", \"heartratelogtime\": \"" + heartRateLogTime + "\"}";
                Log.d("TAG___", "Sending Heart Rate: " + jsonInputString);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d("TAG___", "Response Code: " + responseCode);
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error sending heart rate: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending heart rate: " + e.getMessage());
            }
        }).start();
    }

    private String formatDate(long timeInMillis) {
        Date date = new Date(timeInMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        return sdf.format(date);
    }

    private double calculateAverage(List<Integer> heartRates) {
        int sum = 0;
        for (int rate : heartRates) {
            sum += rate;
        }
        return (double) sum / heartRates.size();
    }

    private void vibrateAndShowNotification() {
        // 진동 패턴 설정 (0ms 대기 후 500ms 진동, 100ms 휴지, 500ms 진동)
        long[] vibrationPattern = {0, 500, 100, 500};
        // 진동 시작
        if (vibrator != null) {
            vibrator.vibrate(vibrationPattern, -1);
        } else {
            Log.e("TAG___", "Vibrator is not available during vibrate()");
        }
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 알림 채널을 생성하면서 소리 설정을 포함
            NotificationChannel channel = new NotificationChannel(
                    "CHANNEL_ID", "My Channel", NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Heart Rate Monitoring Alerts");
            channel.enableVibration(true);  // 진동 활성화
            channel.setVibrationPattern(vibrationPattern);  // 진동 패턴 설정
            // 알림 소리 설정
            channel.setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    Notification.AUDIO_ATTRIBUTES_DEFAULT);  // 소리 설정
            notificationManager.createNotificationChannel(channel);
        }
        // 알림 설정
        Notification notification = new NotificationCompat.Builder(this, "CHANNEL_ID")
                .setContentTitle("경고")
                .setContentText("졸음이 감지되었습니다!")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true)  // 알림 클릭 시 자동 삭제
                .setVibrate(vibrationPattern)  // 진동 패턴 설정
                .setPriority(NotificationCompat.PRIORITY_HIGH)  // 알림 우선순위 설정 (소리와 진동이 모두 울리도록)
                .build();

        // 알림 발송
        notificationManager.notify(1, notification);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
