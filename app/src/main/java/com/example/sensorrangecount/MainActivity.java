package com.example.sensorrangecount;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {
    // 엑티비티에 id
    private TextView textViewTime; // 현재 시간 표시
    private TextView textViewHeartRate; // 심박수 표시
    private TextView drivingTimeTextView; // 운행 시간 표시
    private Button startButton; // 시작 버튼
    private Button pauseButton; // 일시정지 버튼
    private Button stopButton; // 정지 버튼

    private Sensor heartRateSensor; // 심박수 센서
    private boolean isHeartRateSensorPresent; // 심박수 센서 존재 여부

    private long accumulatedTime = 0; // 누적 시간 저장
    private boolean isPaused = false; // 일시정지 상태 추적
    private long totalDrivingTime = 0; // 전체 운행 시간

    private Handler handler = new Handler(); // UI 업데이트를 위한 핸들러
    private long startTime; // 타이머 시작 시간
    private boolean isTimerRunning = false; // 타이머 상태 추적

    // 자기 url로  바꿔야 합니다 -- 그리고 지금은 테스트 로 넣어 둔 것이라 나중에 바꿔야 합니다.
    private String heartUrl = "http://172.168.30.158:9000/heartrate/heartrate"; // 웹 서버 URL
    private String drivingUrl = "http://172.168.30.158:9000/heartrate/drivingtime"; // 웹 서버 URL

    // 진동 설정
    private Vibrator vibrator;
    private int vibrationCount = 0;

    // 앱 메인 (실행될 때 호출됨)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // 레이아웃 설정

        // UI 요소 초기화
        textViewTime = findViewById(R.id.time);
        textViewHeartRate = findViewById(R.id.HeartRate);
        drivingTimeTextView = findViewById(R.id.drivingTime);
        startButton = findViewById(R.id.startButton);
        pauseButton = findViewById(R.id.pauseButton);
        stopButton = findViewById(R.id.stopButton);
        // 진동 초기화
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null) {
            Log.e("TAG___", "Vibrator is not available");
        }

        // 버튼 클릭 이벤트 설정
        startButton.setOnClickListener(view -> startTimer()); // 시작 버튼 클릭 시 타이머 시작
        pauseButton.setOnClickListener(view -> pauseTimer()); // 일시정지 버튼 클릭 시 타이머 일시정지
        stopButton.setOnClickListener(view -> stopTimer()); // 정지 버튼 클릭 시 타이머 종료

        // 버튼 가시성 초기화
        pauseButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.GONE);

        // 현재 시간 업데이트 시작
        startUpdatingTime();
        setupHeartRateSensor(); // 심박수 센서 설정

    }

    // 현재 시간 업데이트
    private void startUpdatingTime() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                textViewTime.setText(currentTime); // 현재 시간 설정
                handler.postDelayed(this, 1000); // 1초마다 업데이트
            }
        });
    }

    // 타이머 시작
    private void startTimer() {
        if (!isTimerRunning) {
            startTime = System.currentTimeMillis();
            isTimerRunning = true;
            handler.post(updateTimeRunnable); // 타이머 업데이트 시작
        }
        startButton.setVisibility(View.GONE);
        pauseButton.setVisibility(View.VISIBLE);
        stopButton.setVisibility(View.VISIBLE);
    }

    // 타이머 일시정지
    private void pauseTimer() {
        if (isTimerRunning) {
            isTimerRunning = false;
            accumulatedTime += System.currentTimeMillis() - startTime; // 누적 시간 계산
            handler.removeCallbacks(updateTimeRunnable); // 타이머 업데이트 중지
            pauseButton.setText("휴식 끝");
        } else {
            startTime = System.currentTimeMillis();
            isTimerRunning = true;
            handler.post(updateTimeRunnable); // 타이머 업데이트 시작
            pauseButton.setText("휴식");
        }
    }

    // 타이머 종료
    private void stopTimer() {
        isTimerRunning = false;
        handler.removeCallbacks(updateTimeRunnable); // 타이머 업데이트 중지

        // 누적 시간 계산
        long totalElapsedMillis = System.currentTimeMillis() - startTime + accumulatedTime; // 누적 시간
        totalDrivingTime += totalElapsedMillis; // 총 운행 시간에 추가

        // 누적 시간 계산
        int hours = (int) (totalDrivingTime / (1000 * 60 * 60));
        int minutes = (int) (totalDrivingTime / (1000 * 60)) % 60;
        int seconds = (int) (totalDrivingTime / 1000) % 60;
        // 운행 시간 표시
        drivingTimeTextView.setText(String.format("운행시간: %02d:%02d:%02d", hours, minutes, seconds));
        Log.d("TAG___", String.format("운행시간 전송: %02d:%02d:%02d", hours, minutes, seconds));
        sendDrivingTimeToServer(hours, minutes, seconds);

        // 종료되면 시간 0으로 세팅
        drivingTimeTextView.setText("운행시간: 00:00:00");

        startButton.setVisibility(View.VISIBLE);
        pauseButton.setVisibility(View.GONE);
        stopButton.setVisibility(View.GONE);
        pauseButton.setText("휴식"); // 종료 시 버튼 텍스트를 "휴식"으로 변경
        // 휴식 누르면 추가되던 누적 시간 초기화
        accumulatedTime = 0;
        totalDrivingTime = 0;
    }

    // 타이머 업데이트
    private Runnable updateTimeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                long elapsedMillis = System.currentTimeMillis() - startTime + accumulatedTime; // 누적 시간
                int hours = (int) (elapsedMillis / (1000 * 60 * 60));
                int minutes = (int) (elapsedMillis / (1000 * 60)) % 60;
                int seconds = (int) (elapsedMillis / 1000) % 60;
                drivingTimeTextView.setText(String.format("운행시간: %02d:%02d:%02d", hours, minutes, seconds));
                handler.postDelayed(this, 1000); // 1초마다 업데이트
            }
        }
    };

    // 심박수 센서 설정
    private void setupHeartRateSensor() {
        heartRateSensor = ((SensorManager) getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_HEART_RATE);
        isHeartRateSensorPresent = (heartRateSensor != null);

        if (isHeartRateSensorPresent) {
            ((SensorManager) getSystemService(Context.SENSOR_SERVICE)).registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            textViewHeartRate.setText("심박수 측정 불가");
        }
    }

    // 센서 데이터 전송
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_HEART_RATE) {
            int heartRate = (int)event.values[0];
            textViewHeartRate.setText("심박수: " + heartRate + " bpm"); // 심박수 표시
            sendHeartRateToServer(heartRate); // 심박수 데이터를 서버로 전송 - 시작 후 서버에 전송 할 수 있도록 변경
            // 전송 할떄 넣긴 했지만 진동 설정은 나중에 다시 설정 해야 합니다 지금은 테스트로 넣어둔 설정입니다.
            onHeartRateChanged(heartRate);
        }
    }

    // 센서 정확도 변경 시 호출 - 아직 정확한거 모름 필요 없는 함수지만 정확도 높이는 정보 얻으면 추가
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // 심박수 관련 함수 - 심박수 얻어올 것이 없으면 멈추게 하는
    @Override
    protected void onPause() {
        super.onPause();
        if (isHeartRateSensorPresent) {
            ((SensorManager) getSystemService(Context.SENSOR_SERVICE)).unregisterListener(this, heartRateSensor); // 센서 리스너 등록 해제
        }
    }

    // 심박수 관련 함수 - 심박수 수신을 다시 받는 함수
    @Override
    protected void onResume() {
        super.onResume();
        if (isHeartRateSensorPresent) {
            ((SensorManager) getSystemService(Context.SENSOR_SERVICE)).registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL); // 센서 리스너 등록
        }
    }


    // 심박수 센서 데이터 전송
    private void sendHeartRateToServer(int heartRate) {
        new Thread(() -> {
            try {
                URL url = new URL(heartUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String jsonInputString = "{\"heartrate\": " + heartRate + "}";
                Log.d("TAG___", "Sending Heart Rate: " + jsonInputString);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d("TAG___", "Response Code: " + responseCode);

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending heart rate: " + e.getMessage());
            }
        }).start();
    }

    // 운행 시간 데이터 전송
    private void sendDrivingTimeToServer(int hours, int minutes, int seconds) {
        new Thread(() -> {
            try {
                URL url = new URL(drivingUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);

                String jsonInputString = String.format("{\"drivingTime\": \"%02d:%02d:%02d\"}", hours, minutes, seconds);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                Log.d("TAG___", "Response Code: " + responseCode);

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e("TAG___", "Error: " + connection.getResponseMessage());
                }
            } catch (Exception e) {
                Log.e("TAG___", "Error sending driving time: " + e.getMessage());
            }
        }).start();
    }

    // 진동 설정 -- 심박수 몇 이하일 때 울리게
    public void onHeartRateChanged(int heartRate) {
        // 심박수가 숫자 이하일 때
        if (heartRate < 60) {
            if (vibrator != null) {
                vibrate();
            } else {
                Log.e("TAG___", "Vibrator is not initialized");
            }
        }
    }
    // 진동 설정
    private void vibrate() {
        long[] pattern = {0, 500, 100, 500}; // 진동 패턴
        if (vibrator != null) {
            vibrator.vibrate(pattern, -1); // 진동 - 1번 울림
        } else {
            Log.e("TAG___", "Vibrator is not available during vibrate()");
        }
    }

}
