package com.example.smartdrainagesystem; // Replace with your package name

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String FIREBASE_URL = "https://smartdrainagesystem-75097-default-rtdb.firebaseio.com/"; // Your Firebase URL

    private TextView tvWaterLevel1, tvWaterLevel2, tvWaterLevel3;
    private LinearLayout chamber1Layout, chamber2Layout, chamber3Layout;
    private TextView tvAlertType, tvBlockageDetails, tvSonar1, tvSonar2, tvMQ8, tvTemperature;
    private TextView tvIRSensor, tvFlameSensor, tvGPSCoordinates, tvTimestamp;
    private Button btnOpenMap, btnRefresh;
    private SwitchMaterial switchManualServo, switchAutoMode;
    private ProgressBar progressBar;

    private DatabaseReference sensorDataRef;
    private DatabaseReference servoControlRef;
    private ValueEventListener sensorDataListener;
    private ValueEventListener servoControlListener;

    private String currentGpsCoordinates = "0,0";
    private Handler uiHandler = new Handler(Looper.getMainLooper());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();

        FirebaseDatabase database = FirebaseDatabase.getInstance(FIREBASE_URL);
        sensorDataRef = database.getReference("sensor_data");
        servoControlRef = database.getReference("servo_control");

        setupListeners();

        btnOpenMap.setOnClickListener(v -> openMap());
        btnRefresh.setOnClickListener(v -> refreshData());

        switchManualServo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) { // Only react to user interaction
                updateServoControl("servo_on", isChecked);
            }
        });

        switchAutoMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) { // Only react to user interaction
                updateServoControl("auto_mode", isChecked);
            }
        });
    }

    private void initializeUI() {
        chamber1Layout = findViewById(R.id.chamber1Layout);
        chamber2Layout = findViewById(R.id.chamber2Layout);
        chamber3Layout = findViewById(R.id.chamber3Layout);
        tvWaterLevel1 = findViewById(R.id.tvWaterLevel1);
        tvWaterLevel2 = findViewById(R.id.tvWaterLevel2);
        tvWaterLevel3 = findViewById(R.id.tvWaterLevel3);

        tvAlertType = findViewById(R.id.tvAlertType);
        tvBlockageDetails = findViewById(R.id.tvBlockageDetails);
        tvSonar1 = findViewById(R.id.tvSonar1);
        tvSonar2 = findViewById(R.id.tvSonar2);
        tvMQ8 = findViewById(R.id.tvMQ8);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvIRSensor = findViewById(R.id.tvIRSensor);
        tvFlameSensor = findViewById(R.id.tvFlameSensor);
        tvGPSCoordinates = findViewById(R.id.tvGPSCoordinates);
        tvTimestamp = findViewById(R.id.tvTimestamp);

        btnOpenMap = findViewById(R.id.btnOpenMap);
        btnRefresh = findViewById(R.id.btnRefresh);
        switchManualServo = findViewById(R.id.switchManualServo);
        switchAutoMode = findViewById(R.id.switchAutoMode);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        progressBar.setVisibility(View.VISIBLE);

        sensorDataListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                progressBar.setVisibility(View.GONE);
                SensorFirebaseData sensorData = dataSnapshot.getValue(SensorFirebaseData.class);
                if (sensorData != null) {
                    updateSensorUI(sensorData);
                } else {
                    Toast.makeText(MainActivity.this, "No sensor data found", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                progressBar.setVisibility(View.GONE);
                Log.w(TAG, "loadSensorData:onCancelled", databaseError.toException());
                Toast.makeText(MainActivity.this, "Failed to load sensor data.", Toast.LENGTH_SHORT).show();
            }
        };

        servoControlListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                ServoControl control = dataSnapshot.getValue(ServoControl.class);
                if (control != null) {
                    // Update switches without triggering their change listeners
                    switchManualServo.setChecked(control.servo_on);
                    switchAutoMode.setChecked(control.auto_mode);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.w(TAG, "loadServoControl:onCancelled", databaseError.toException());
                Toast.makeText(MainActivity.this, "Failed to load servo control.", Toast.LENGTH_SHORT).show();
            }
        };
        attachFirebaseListeners();
    }

    private void attachFirebaseListeners() {
        if (sensorDataRef != null && sensorDataListener != null) {
            sensorDataRef.addValueEventListener(sensorDataListener);
        }
        if (servoControlRef != null && servoControlListener != null) {
            servoControlRef.addValueEventListener(servoControlListener);
        }
    }

    private void detachFirebaseListeners() {
        if (sensorDataRef != null && sensorDataListener != null) {
            sensorDataRef.removeEventListener(sensorDataListener);
        }
        if (servoControlRef != null && servoControlListener != null) {
            servoControlRef.removeEventListener(servoControlListener);
        }
    }


    private void updateSensorUI(SensorFirebaseData sensorData) {
        if (sensorData.alert != null) {
            tvAlertType.setText(String.format("Alert: %s", sensorData.alert));
            if ("Blockage".equals(sensorData.alert) || "Gas".equals(sensorData.alert) || "Fire".equals(sensorData.alert)) {
                tvAlertType.setTextColor(Color.RED);
            } else {
                tvAlertType.setTextColor(Color.BLACK); // Or your default color
            }
        }

        if (sensorData.gps != null) {
            currentGpsCoordinates = sensorData.gps;
            tvGPSCoordinates.setText(String.format("GPS: %s", currentGpsCoordinates));
        }

        if (sensorData.data != null) {
            SensorDetails details = sensorData.data;
            if (details.blockage_type != null) {
                String blockageText = String.format("Blockage Type: %s", details.blockage_type);
                if (details.blocked_chamber != null) {
                    blockageText += String.format(" (Chamber %d)", details.blocked_chamber);
                }
                tvBlockageDetails.setText(blockageText);
            } else {
                tvBlockageDetails.setText("Blockage: None");
            }

            tvSonar1.setText(String.format(Locale.US,"Sonar 1: %.1f cm", details.distance1 != null ? details.distance1 : 0.0));
            tvSonar2.setText(String.format(Locale.US,"Sonar 2: %.1f cm", details.distance2 != null ? details.distance2 : 0.0));
            tvMQ8.setText(String.format(Locale.US,"Gas (MQ8): %.2f V", details.mq8 != null ? details.mq8 : 0.0));
            tvTemperature.setText(String.format(Locale.US,"Temp: %.1f °C", details.temp != null ? details.temp : 0.0));
            tvIRSensor.setText(String.format("IR Sensor: %s", details.ir != null && details.ir == 0 ? "Object Detected" : "Clear"));
            tvFlameSensor.setText(String.format("Flame Sensor: %s", details.flame != null && details.flame == 0 ? "Flame Detected" : "No Flame"));

            // Update Chamber Visuals
            updateChamberVisuals(details.water_levels, "Blockage".equals(sensorData.alert) ? details.blocked_chamber : null);
        }

        if (sensorData.timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
            tvTimestamp.setText(String.format("Last Update: %s", sdf.format(new Date(sensorData.timestamp * 1000L))));
        }
    }

    private void updateChamberVisuals(List<Integer> waterLevels, Integer blockedChamber) {
        LinearLayout[] chamberLayouts = {chamber1Layout, chamber2Layout, chamber3Layout};
        TextView[] waterLevelTextViews = {tvWaterLevel1, tvWaterLevel2, tvWaterLevel3};
        int defaultColor = Color.parseColor("#E0E0E0"); // Light Grey
        int waterColor = Color.parseColor("#ADD8E6");   // Light Blue
        int blockedColor = Color.RED;

        if (waterLevels != null && waterLevels.size() == 3) {
            for (int i = 0; i < 3; i++) {
                boolean hasWater = waterLevels.get(i) == 1;
                waterLevelTextViews[i].setText(hasWater ? "Water Present" : "No Water");

                if (blockedChamber != null && (i + 1) == blockedChamber) {
                    chamberLayouts[i].setBackgroundColor(blockedColor);
                } else {
                    chamberLayouts[i].setBackgroundColor(hasWater ? waterColor : defaultColor);
                }
            }
        } else {
            // Reset if data is incomplete
            for (int i = 0; i < 3; i++) {
                chamberLayouts[i].setBackgroundColor(defaultColor);
                waterLevelTextViews[i].setText("Status");
            }
        }
    }


    private void updateServoControl(String key, boolean value) {
        if (servoControlRef != null) {
            servoControlRef.child(key).setValue(value)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, key + " updated to " + value))
                    .addOnFailureListener(e -> {
                        Toast.makeText(MainActivity.this, "Failed to update " + key, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to update " + key, e);
                        // Revert switch state on failure
                        if (key.equals("servo_on")) switchManualServo.setChecked(!value);
                        if (key.equals("auto_mode")) switchAutoMode.setChecked(!value);
                    });
        }
    }

    private void openMap() {
        if (currentGpsCoordinates != null && !currentGpsCoordinates.isEmpty() && !currentGpsCoordinates.equals("0,0")) {
            try {
                Uri gmmIntentUri = Uri.parse("geo:" + currentGpsCoordinates + "?q=" + currentGpsCoordinates + "(Drainage System Location)");
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                } else {
                    // Fallback to web browser if Google Maps app is not installed
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + currentGpsCoordinates));
                    startActivity(webIntent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error opening map", e);
                Toast.makeText(this, "Could not open map application.", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "GPS coordinates not available.", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshData() {
        // Re-fetch data using a one-time get() call.
        // This is an alternative to relying solely on addValueEventListener if a manual pull is desired.
        progressBar.setVisibility(View.VISIBLE);
        if (sensorDataRef != null) {
            sensorDataRef.get().addOnCompleteListener(task -> {
                progressBar.setVisibility(View.GONE);
                if (task.isSuccessful()) {
                    DataSnapshot dataSnapshot = task.getResult();
                    SensorFirebaseData sensorData = dataSnapshot.getValue(SensorFirebaseData.class);
                    if (sensorData != null) {
                        updateSensorUI(sensorData);
                        Toast.makeText(MainActivity.this, "Data refreshed", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "No sensor data found on refresh", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Log.e(TAG, "Error getting sensor data on refresh.", task.getException());
                    Toast.makeText(MainActivity.this, "Failed to refresh sensor data.", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (servoControlRef != null) {
            servoControlRef.get().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    DataSnapshot dataSnapshot = task.getResult();
                    ServoControl control = dataSnapshot.getValue(ServoControl.class);
                    if (control != null) {
                        switchManualServo.setChecked(control.servo_on);
                        switchAutoMode.setChecked(control.auto_mode);
                    }
                } else {
                    Log.e(TAG, "Error getting servo control data on refresh.", task.getException());
                }
            });
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        attachFirebaseListeners();
    }

    @Override
    protected void onStop() {
        super.onStop();
        detachFirebaseListeners();
    }
}