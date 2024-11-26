package com.example.sensorrangecount;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    // UI 요소 초기화
    private TextView textViewTime; // 현재 시간 표시하는 TextView
    private TextView textViewHeartRate; // 심박수 표시하는 TextView
    private TextView drivingTimeTextView; // 운행 시간 표시하는 TextView
    private Button startButton; // 타이머 시작 버튼
    private Button pauseButton; // 타이머 일시정지 버튼
    private Button stopButton; // 타이머 정지 버튼
    private Button notiButton;

    static String userId = "E001";

    // 심박수 센서 관련 변수
    private Sensor heartRateSensor; // 심박수 센서 객체
    private boolean isHeartRateSensorPresent; // 심박수 센서의 존재 여부를 저장하는 변수

    // 시간 관련 변수
    private long accumulatedTime = 0; // 누적 시간을 저장하는 변수
    private long totalDrivingTime = 0; // 총 운행 시간을 저장하는 변수

    // UI 업데이트를 위한 핸들러
    private Handler handler = new Handler();
    private long startTime; // 타이머 시작 시간
    private boolean isTimerRunning = false; // 타이머 실행 상태를 추적하는 변수

    // 서버 URL (테스트용 URL, 실제 사용 시 변경 필요)
    private String heartUrl = "http://175.197.201.115:9000/heartrate/heartrate"; // 심박수 전송용 서버 URL
    private String drivingUrl = "http://175.197.201.115:9000/heartrate/drivingtime"; // 운행 시간 전송용 서버 URL

    static String baseurl = "http://175.197.201.115:9000/";
    // 진동 서비스
    private Vibrator vibrator; // 진동 서비스 객체

    private SoundPool soundPool;
    private int alarmSoundId;


    // 앱이 실행될 때 호출되는 메서드
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 레이아웃 설정

        // UI 요소 초기화
        textViewTime = findViewById(R.id.time); // 현재 시간 표시 TextView
        textViewHeartRate = findViewById(R.id.HeartRate); // 심박수 표시 TextView
        drivingTimeTextView = findViewById(R.id.drivingTime); // 운행 시간 표시 TextView
        startButton = findViewById(R.id.startButton); // 시작 버튼
        pauseButton = findViewById(R.id.pauseButton); // 일시정지 버튼
        stopButton = findViewById(R.id.stopButton); // 정지 버튼

        // 진동 서비스 초기화
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE); // 진동 서비스 가져오기

        // 심박수 센서 초기화
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE); // 센서 매니저 가져오기
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE); // 심박수 센서 가져오기
        isHeartRateSensorPresent = (heartRateSensor != null); // 심박수 센서의 존재 여부 확인

        // 버튼 클릭 이벤트 설정
        startButton.setOnClickListener(view -> startTimer()); // 시작 버튼 클릭 시 타이머 시작
        pauseButton.setOnClickListener(view -> pauseTimer()); // 일시정지 버튼 클릭 시 타이머 일시정지
        stopButton.setOnClickListener(view -> stopTimer()); // 정지 버튼 클릭 시 타이머 종료
        textViewTime.setOnClickListener(view -> touchTime()); // 시계로 알림 설정 하는거

        // 버튼 가시성 초기화
        pauseButton.setVisibility(View.GONE); // 초기에는 일시정지 버튼 숨김
        stopButton.setVisibility(View.GONE); // 초기에는 정지 버튼 숨김

        // 현재 시간 업데이트 시작
        startUpdatingTime(); // 현재 시간 업데이트 메서드 호출

        // 심박수 서비스 시작
        Intent intent = new Intent(this, HeartRateService.class); // 심박수 서비스 인텐트 생성
        startService(intent); // 서비스 시작

        // 심박수 센서 등록
        if (isHeartRateSensorPresent) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL); // 센서 리스너 등록
        }
        // 서비스 시작
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 포그라운드 서비스는 startForegroundService()로 시작해야 함
            Intent serviceIntent = new Intent(this, HeartRateService.class);
            startForegroundService(serviceIntent);
        } else {
            // Android 7.1 이하에서는 기존 방식대로 startService() 사용
            Intent serviceIntent = new Intent(this, HeartRateService.class);
            startService(serviceIntent);
        }
    }

    private void touchTime() {
        vibrateAndShowNotification();
        sendEmergencyNoti();
    }

    // 현재 시간 업데이트
    private void startUpdatingTime() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()); // 현재 시간 포맷 설정
                textViewTime.setText(currentTime); // 현재 시간 TextView에 설정
                handler.postDelayed(this, 1000); // 1초마다 업데이트
            }
        });
    }

    // 타이머 시작
    private void startTimer() {
        if (!isTimerRunning) { // 타이머가 실행 중이지 않으면
            startTime = System.currentTimeMillis(); // 시작 시간 기록
            isTimerRunning = true; // 타이머 상태 변경
            handler.post(updateTimeRunnable); // 타이머 업데이트 시작
        }
        startButton.setVisibility(View.GONE); // 시작 버튼 숨김
        pauseButton.setVisibility(View.VISIBLE); // 일시정지 버튼 보임
        stopButton.setVisibility(View.VISIBLE); // 정지 버튼 보임

        HeartRateService.isResting = false;

        sendStartNoti();
    }

    // 타이머 일시정지
    private void pauseTimer() {
        if (isTimerRunning) { // 타이머가 실행 중이면
            isTimerRunning = false; // 타이머 상태 변경
            accumulatedTime += System.currentTimeMillis() - startTime; // 누적 시간 계산
            handler.removeCallbacks(updateTimeRunnable); // 타이머 업데이트 중지
            pauseButton.setText("휴식 끝"); // 버튼 텍스트 변경
            HeartRateService.isResting = true;
            startRest();
            sendRestNoti();
        } else { // 타이머가 일시정지 상태일 경우
            startTime = System.currentTimeMillis(); // 시작 시간 갱신
            isTimerRunning = true; // 타이머 상태 변경
            handler.post(updateTimeRunnable); // 타이머 업데이트 시작
            pauseButton.setText("휴식"); // 버튼 텍스트 변경
            HeartRateService.isResting = false;
            stopRest();
            sendEndRestNoti();
        }
    }

    private void startRest() {
        Intent serviceIntent = new Intent(this, HeartRateService.class);
        serviceIntent.putExtra("action", "startRest");
        startService(serviceIntent); // 휴식 시작을 HeartRateService로 전달
    }

    private void stopRest() {
        Intent serviceIntent = new Intent(this, HeartRateService.class);
        serviceIntent.putExtra("action", "endRest");
        startService(serviceIntent); // 휴식 종료를 HeartRateService로 전달
    }

    // 타이머 종료
    private void stopTimer() {
        isTimerRunning = false; // 타이머 상태 변경
        handler.removeCallbacks(updateTimeRunnable); // 타이머 업데이트 중지

        // 누적 시간 계산
        long totalElapsedMillis = System.currentTimeMillis() - startTime + accumulatedTime; // 총 경과 시간
        totalDrivingTime += totalElapsedMillis; // 총 운행 시간에 추가

        // 총 운행 시간을 시, 분, 초로 변환
        int hours = (int) (totalDrivingTime / (1000 * 60 * 60));
        int minutes = (int) (totalDrivingTime / (1000 * 60)) % 60;
        int seconds = (int) (totalDrivingTime / 1000) % 60;

        // 운행 시간 표시
        drivingTimeTextView.setText(String.format("운행시간: %02d:%02d:%02d", hours, minutes, seconds));
        Log.d("TAG___", String.format("운행시간 전송: %02d:%02d:%02d", hours, minutes, seconds));
        sendDrivingTimeToServer(hours, minutes, seconds); // 서버로 운행 시간 전송

        // 종료 후 운행 시간 0으로 초기화
        drivingTimeTextView.setText("운행시간: 00:00:00");

        startButton.setVisibility(View.VISIBLE); // 시작 버튼 보임
        pauseButton.setVisibility(View.GONE); // 일시정지 버튼 숨김
        stopButton.setVisibility(View.GONE); // 정지 버튼 숨김
        pauseButton.setText("휴식"); // 버튼 텍스트 초기화
        // 휴식 중 누적 시간 초기화
        accumulatedTime = 0;
        totalDrivingTime = 0;

        HeartRateService.isResting = true;

        sendEndNoti();
    }

    // 타이머 업데이트를 위한 Runnable
    private Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) { // 타이머가 실행 중이면
                long elapsedMillis = System.currentTimeMillis() - startTime + accumulatedTime; // 경과 시간 계산
                int hours = (int) (elapsedMillis / (1000 * 60 * 60)); // 시 계산
                int minutes = (int) (elapsedMillis / (1000 * 60)) % 60; // 분 계산
                int seconds = (int) (elapsedMillis / 1000) % 60; // 초 계산
                drivingTimeTextView.setText(String.format("운행시간: %02d:%02d:%02d", hours, minutes, seconds)); // 운행 시간 업데이트
                handler.postDelayed(this, 1000); // 1초마다 업데이트
            }
        }
    };
    

    // 센서 데이터 전송
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) { // 심박수 센서일 경우
            int heartRate = (int) event.values[0]; // 심박수 값 가져오기
            textViewHeartRate.setText("심박수: " + heartRate + " bpm"); // 심박수 표시
        }
    }

    // 센서 정확도 변경 시 호출 (필요시 구현)
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // 심박수 센서 수신 중지
    @Override
    protected void onPause() {
        super.onPause();
        if (isHeartRateSensorPresent) {
            ((SensorManager) getSystemService(Context.SENSOR_SERVICE)).unregisterListener(this, heartRateSensor); // 센서 리스너 등록 해제
        }
    }

    // 심박수 센서 수신 재개
    @Override
    protected void onResume() {
        super.onResume();
        if (isHeartRateSensorPresent) {
            ((SensorManager) getSystemService(Context.SENSOR_SERVICE)).registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL); // 센서 리스너 등록
        }
    }

    // 운행 시간 데이터를 서버로 전송
    private void sendDrivingTimeToServer(int hours, int minutes, int seconds) {
        new Thread(() -> {
            try {
                URL url = new URL(drivingUrl); // URL 설정
                HttpURLConnection connection = (HttpURLConnection) url.openConnection(); // 연결 생성
                connection.setRequestMethod("POST"); // 요청 방식 설정
                connection.setRequestProperty("Content-Type", "application/json"); // 헤더 설정
                connection.setDoOutput(true); // 데이터 전송 가능 설정

                // JSON 형식으로 운행 시간 데이터 생성
                String jsonInputString = String.format("{\"drivingTime\": \"%02d:%02d:%02d\"}", hours, minutes, seconds);

                // 데이터 전송
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode(); // 응답 코드 받기
                Log.d("TAG___", "Response Code: " + responseCode); // 로그 출력

                // 응답 코드가 OK가 아닐 경우 에러 로그 출력
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending driving time: " + e.getMessage()); // 예외 발생 시 에러 로그 출력
            }
        }).start(); // 새 스레드에서 실행
    }

    static List<Integer> heartRateList = new ArrayList<>();

    // 평균 계산 메서드
    private double calculateAverage(List<Integer> heartRates) {
        int sum = 0;
        for (int rate : heartRates) {
            sum += rate;
        }
        return (double) sum / heartRates.size();
    }
    // 운행 시작 알림
    public void sendStartNoti() {
        new Thread(() -> {
            try {
                URL url = new URL(baseurl + "noti/start/" + userId); // URL 설정
                HttpURLConnection connection = (HttpURLConnection) url.openConnection(); // 연결 생성
                connection.setRequestMethod("GET"); // 요청 방식 설정
                connection.setRequestProperty("Content-Type", "application/json"); // 헤더 설정

                int responseCode = connection.getResponseCode(); // 응답 코드 받기
                Log.d("TAG___", "Response Code: " + responseCode); // 로그 출력

                // 응답 코드가 OK가 아닐 경우 에러 로그 출력
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending driving time: " + e.getMessage()); // 예외 발생 시 에러 로그 출력
            }
        }).start(); // 새 스레드에서 실행
    };


    // 운행 종료 알림
    public void sendEndNoti() {
        new Thread(() -> {
            try {
                URL url = new URL(baseurl + "noti/end/" + userId); // URL 설정
                HttpURLConnection connection = (HttpURLConnection) url.openConnection(); // 연결 생성
                connection.setRequestMethod("GET"); // 요청 방식 설정
                connection.setRequestProperty("Content-Type", "application/json"); // 헤더 설정

                int responseCode = connection.getResponseCode(); // 응답 코드 받기
                Log.d("TAG___", "Response Code: " + responseCode); // 로그 출력

                // 응답 코드가 OK가 아닐 경우 에러 로그 출력
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending driving time: " + e.getMessage()); // 예외 발생 시 에러 로그 출력
            }
        }).start(); // 새 스레드에서 실행
    }

    // 휴식 알림
    public void sendRestNoti() {
        new Thread(() -> {
            try {
                URL url = new URL(baseurl + "noti/rest/" + userId); // URL 설정
                HttpURLConnection connection = (HttpURLConnection) url.openConnection(); // 연결 생성
                connection.setRequestMethod("GET"); // 요청 방식 설정
                connection.setRequestProperty("Content-Type", "application/json"); // 헤더 설정

                HeartRateService.isResting = true;

                int responseCode = connection.getResponseCode(); // 응답 코드 받기
                Log.d("TAG___", "Response Code: " + responseCode); // 로그 출력

                // 응답 코드가 OK가 아닐 경우 에러 로그 출력
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending driving time: " + e.getMessage()); // 예외 발생 시 에러 로그 출력
            }
        }).start(); // 새 스레드에서 실행
    }

    // 휴식 끝 알림
    public static void sendEndRestNoti() {
        new Thread(() -> {
            try {
                URL url = new URL(baseurl + "noti/endrest/" + userId); // URL 설정
                HttpURLConnection connection = (HttpURLConnection) url.openConnection(); // 연결 생성
                connection.setRequestMethod("GET"); // 요청 방식 설정
                connection.setRequestProperty("Content-Type", "application/json"); // 헤더 설정

                HeartRateService.isResting = false;

                int responseCode = connection.getResponseCode(); // 응답 코드 받기
                Log.d("TAG___", "Response Code: " + responseCode); // 로그 출력

                // 응답 코드가 OK가 아닐 경우 에러 로그 출력
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending driving time: " + e.getMessage()); // 예외 발생 시 에러 로그 출력
            }
        }).start(); // 새 스레드에서 실행
    }

    // 졸음감지 알림
    public static void sendEmergencyNoti() {
        new Thread(() -> {
            try {
                URL url = new URL(baseurl + "noti/emergency/" + userId); // URL 설정
                HttpURLConnection connection = (HttpURLConnection) url.openConnection(); // 연결 생성
                connection.setRequestMethod("GET"); // 요청 방식 설정
                connection.setRequestProperty("Content-Type", "application/json"); // 헤더 설정

                int responseCode = connection.getResponseCode(); // 응답 코드 받기
                Log.d("TAG___", "Response Code: " + responseCode); // 로그 출력

                // 응답 코드가 OK가 아닐 경우 에러 로그 출력
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending driving time: " + e.getMessage()); // 예외 발생 시 에러 로그 출력
            }
        }).start(); // 새 스레드에서 실행
    }
    public void vibrateAndShowNotification() {
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
}
