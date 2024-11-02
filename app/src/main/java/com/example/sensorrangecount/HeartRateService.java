package com.example.sensorrangecount;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HeartRateService extends Service implements SensorEventListener {
    private SensorManager sensorManager; // 센서 매니저
    private Sensor heartRateSensor; // 심박수 센서
    private PowerManager.WakeLock wakeLock; // Wake Lock

    private static final String CHANNEL_ID = "HeartRateServiceChannel"; // 알림 채널 ID
    private String heartUrl = "http://172.168.10.88:9000/heartrate/heartrate"; // 웹 서버 URL

    // 서비스가 생성될 때 호출
    @Override
    public void onCreate() {
        super.onCreate();
        setupHeartRateSensor(); // 심박수 센서 설정
        createNotificationChannel(); // 알림 채널 생성
        acquireWakeLock(); // Wake Lock 획득
    }

    // 서비스가 시작될 때 호출
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, createNotification()); // 포그라운드 서비스 시작
        return START_STICKY; // 서비스가 강제로 종료되더라도 다시 시작
    }

    // 서비스가 종료될 때 호출
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (heartRateSensor != null) {
            sensorManager.unregisterListener(this, heartRateSensor); // 센서 리스너 해제
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release(); // Wake Lock 해제
        }
    }

    // 센서 데이터 변경 시 호출
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int heartRate = (int) event.values[0];
            Log.d("TAG___", "심박수: " + heartRate);

            //시간
            long currentTimeMillis = System.currentTimeMillis();
            String heartRateLogTime = formatDate(currentTimeMillis);
            Log.d("TAG___", "시간: " + heartRateLogTime);

            // 서버로 전송
            sendHeartRateToServer(heartRate, heartRateLogTime);

            // 심박수 데이터를 브로드캐스트로 전송 - "이부분 필요한지 확인 필요"
            Intent intent = new Intent("HEART_RATE_UPDATE");
            intent.putExtra("heartRate", heartRate);
            intent.putExtra("heartRateLogTime", heartRateLogTime);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        }
    }

    // 센서 정확도 변경 시 호출
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // 심박수 센서 설정
    private void setupHeartRateSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE); // 심박수 센서 초기화
        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL); // 센서 리스너 등록
        } else {
            Log.e("TAG___", "Heart Rate Sensor not available");
        }
    }

    // 알림 생성 메서드
    private Notification createNotification() {
        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Heart Rate Monitoring")
                .setContentText("Monitoring your heart rate...")
                .setPriority(Notification.PRIORITY_HIGH); // 중요도 설정
        return builder.build();
    }

    // 알림 채널 생성
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

    // Wake Lock 획득
    private void acquireWakeLock() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::HeartRateWakelock");
        if (wakeLock != null) {
            wakeLock.acquire(); // Wake Lock 획득
        }
    }

    // 심박수 서버 전송 메서드
    private void sendHeartRateToServer(int heartRate, String heartRateLogTime) {
        new Thread(() -> {
            try {
                URL url = new URL(heartUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String jsonInputString = "{\"heartrate\": " + heartRate + ", \"heartratelogtime\": \"" + heartRateLogTime + "\"}";
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
    
    // 시간 보내기 설정 (포맷)
    private String formatDate(long timeInMillis) {
        Date date = new Date(timeInMillis);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault());
        return sdf.format(date);
    }
    
    
    // 바인딩을 위한 메서드
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
