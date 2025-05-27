package com.example.smartdrainagesystem; // Replace with your package name

import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
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

    // UI Elements for Chambers
    private RelativeLayout chamber1Layout, chamber2Layout, chamber3Layout;
    private TextView tvWaterLevel1, tvWaterLevel2, tvWaterLevel3;
    private View waterIndicator1, waterIndicator2, waterIndicator3;

    // UI Elements for Sensor Data
    private TextView tvAlertType, tvBlockageDetails, tvSonar1, tvSonar2, tvMQ8, tvTemperature;
    private TextView tvIRSensor, tvFlameSensor, tvGPSCoordinates, tvTimestamp;
    private ImageView ivAlertIcon; // For the alert icon

    // UI Elements for Controls
    private MaterialButton btnOpenMap, btnRefresh;
    private SwitchMaterial switchManualServo, switchAutoMode;
    private ProgressBar progressBar;

    // Firebase
    private DatabaseReference sensorDataRef;
    private DatabaseReference servoControlRef;
    private ValueEventListener sensorDataListener;
    private ValueEventListener servoControlListener;

    private String currentGpsCoordinates = "0,0";
    // private Handler uiHandler = new Handler(Looper.getMainLooper()); // Not explicitly used now


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Ensure this matches your new XML filename

        // Toolbar Setup
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false); // Using custom title
        }

        initializeUI();

        FirebaseDatabase database = FirebaseDatabase.getInstance(FIREBASE_URL);
        sensorDataRef = database.getReference("sensor_data");
        servoControlRef = database.getReference("servo_control");

        setupListeners();

        btnOpenMap.setOnClickListener(v -> openMap());
        btnRefresh.setOnClickListener(v -> refreshData());

        switchManualServo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                updateServoControl("servo_on", isChecked);
            }
        });

        switchAutoMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                updateServoControl("auto_mode", isChecked);
            }
        });
    }

    private void initializeUI() {
        // Chambers
        chamber1Layout = findViewById(R.id.chamber1Layout);
        chamber2Layout = findViewById(R.id.chamber2Layout);
        chamber3Layout = findViewById(R.id.chamber3Layout);
        tvWaterLevel1 = findViewById(R.id.tvWaterLevel1);
        tvWaterLevel2 = findViewById(R.id.tvWaterLevel2);
        tvWaterLevel3 = findViewById(R.id.tvWaterLevel3);
        waterIndicator1 = findViewById(R.id.waterIndicator1);
        waterIndicator2 = findViewById(R.id.waterIndicator2);
        waterIndicator3 = findViewById(R.id.waterIndicator3);

        // Sensor Data
        tvAlertType = findViewById(R.id.tvAlertType);
        ivAlertIcon = findViewById(R.id.ivAlertIcon);
        tvBlockageDetails = findViewById(R.id.tvBlockageDetails);
        tvSonar1 = findViewById(R.id.tvSonar1);
        tvSonar2 = findViewById(R.id.tvSonar2);
        tvMQ8 = findViewById(R.id.tvMQ8);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvIRSensor = findViewById(R.id.tvIRSensor);
        tvFlameSensor = findViewById(R.id.tvFlameSensor);
        tvGPSCoordinates = findViewById(R.id.tvGPSCoordinates);
        tvTimestamp = findViewById(R.id.tvTimestamp);

        // Controls
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
                    // Update switches without triggering their change listeners if not pressed by user
                    if (!switchManualServo.isPressed()) switchManualServo.setChecked(control.servo_on);
                    if (!switchAutoMode.isPressed()) switchAutoMode.setChecked(control.auto_mode);
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
        // Alert Type
        if (sensorData.alert != null) {
            tvAlertType.setText(String.format("Status: %s", sensorData.alert));
            if ("None".equals(sensorData.alert)) {
                tvAlertType.setTextColor(ContextCompat.getColor(this, R.color.green_ok));
                ivAlertIcon.setColorFilter(ContextCompat.getColor(this, R.color.green_ok));
            } else {
                tvAlertType.setTextColor(ContextCompat.getColor(this, R.color.red_alert));
                ivAlertIcon.setColorFilter(ContextCompat.getColor(this, R.color.red_alert));
            }
        }

        // GPS Coordinates
        if (sensorData.gps != null) {
            currentGpsCoordinates = sensorData.gps;
            tvGPSCoordinates.setText(String.format(Locale.US, "GPS: %s", currentGpsCoordinates));
        }

        // Detailed Sensor Data
        if (sensorData.data != null) {
            SensorDetails details = sensorData.data;

            // Blockage Details
            if (details.blockage_type != null && details.blocked_chamber != null) {
                tvBlockageDetails.setText(String.format(Locale.US, "Blockage: %s (Chamber %d)", details.blockage_type, details.blocked_chamber));
                tvBlockageDetails.setTextColor(ContextCompat.getColor(this, R.color.red_alert));
            } else {
                tvBlockageDetails.setText("Blockage: None");
                tvBlockageDetails.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimary));
            }

            // MQ8 - Methane Gas
            double mq8Voltage = details.mq8 != null ? details.mq8 : 0.0;
            tvMQ8.setText(String.format(Locale.US, "Methane: %s (%.2fV)", mq8Voltage > 1.5 ? "Present" : "Not Present", mq8Voltage));
            tvMQ8.setTextColor(mq8Voltage > 1.5 ? ContextCompat.getColor(this, R.color.red_alert) : ContextCompat.getColor(this, R.color.textColorPrimary));

            // Flame Sensor
            boolean flameDetected = details.flame != null && details.flame == 0;
            tvFlameSensor.setText(String.format("Flame: %s", flameDetected ? "Detected" : "No Flame"));
            tvFlameSensor.setTextColor(flameDetected ? ContextCompat.getColor(this, R.color.red_alert) : ContextCompat.getColor(this, R.color.textColorPrimary));

            // IR Sensor
            boolean irObjectDetected = details.ir != null && details.ir == 0;
            tvIRSensor.setText(String.format("Obstacle (IR): %s", irObjectDetected ? "Detected" : "Clear"));
            tvIRSensor.setTextColor(irObjectDetected ? ContextCompat.getColor(this, R.color.red_alert) : ContextCompat.getColor(this, R.color.textColorPrimary));

            // Sonar 1
            double d1 = details.distance1 != null ? details.distance1 : 999.0;
            tvSonar1.setText(String.format(Locale.US, "Sonar 1: %.1f cm", d1));
            tvSonar1.setTextColor(d1 < 5.0 ? ContextCompat.getColor(this, R.color.red_alert) : ContextCompat.getColor(this, R.color.textColorPrimary));

            // Sonar 2
            double d2 = details.distance2 != null ? details.distance2 : 999.0;
            tvSonar2.setText(String.format(Locale.US, "Sonar 2: %.1f cm", d2));
            tvSonar2.setTextColor(d2 < 5.0 ? ContextCompat.getColor(this, R.color.red_alert) : ContextCompat.getColor(this, R.color.textColorPrimary));

            // Temperature
            tvTemperature.setText(String.format(Locale.US, "Temp: %.1f °C", details.temp != null ? details.temp : 0.0));
            tvTemperature.setTextColor(ContextCompat.getColor(this, R.color.textColorPrimary)); // Reset color if needed

            // Update Chamber Visuals
            updateChamberVisuals(details.water_levels, "Blockage".equals(sensorData.alert) ? details.blocked_chamber : null);
        }

        // Timestamp
        if (sensorData.timestamp > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
            tvTimestamp.setText(String.format("Last Update: %s", sdf.format(new Date(sensorData.timestamp * 1000L))));
        }
    }

    private void updateChamberVisuals(List<Integer> waterLevels, Integer blockedChamber) {
        RelativeLayout[] chamberLayouts = {chamber1Layout, chamber2Layout, chamber3Layout};
        TextView[] waterLevelTextViews = {tvWaterLevel1, tvWaterLevel2, tvWaterLevel3};
        View[] waterIndicators = {waterIndicator1, waterIndicator2, waterIndicator3};

        int defaultBgRes = R.drawable.chamber_background;
        int waterIndicatorBgRes = R.drawable.chamber_water_background;
        int blockedBgRes = R.drawable.chamber_blocked_background;

        if (waterLevels != null && waterLevels.size() == 3) {
            for (int i = 0; i < 3; i++) {
                boolean hasWater = waterLevels.get(i) == 1;
                waterLevelTextViews[i].setText(hasWater ? "Water" : "Empty");

                // Set overall chamber background and text color
                if (blockedChamber != null && (i + 1) == blockedChamber) {
                    chamberLayouts[i].setBackgroundResource(blockedBgRes);
                    waterLevelTextViews[i].setText("BLOCKED");
                    waterLevelTextViews[i].setTextColor(Color.WHITE); // Ensure text is visible on red
                } else {
                    chamberLayouts[i].setBackgroundResource(defaultBgRes);
                    waterLevelTextViews[i].setTextColor(hasWater ? ContextCompat.getColor(this,R.color.white) : ContextCompat.getColor(this, R.color.textColorSecondary));
                }

                // Water Indicator Animation/Height
                final View indicator = waterIndicators[i];
                float targetHeightFraction = hasWater ? 0.6f : 0.05f;
                if (blockedChamber != null && (i + 1) == blockedChamber) {
                    targetHeightFraction = 0.05f; // Minimal water indication if blocked
                }

                int parentHeight = chamberLayouts[i].getHeight(); // Get height of the chamber RelativeLayout
                if (parentHeight == 0) {
                    final int finalI = i; // for use in lambda
                    final float finalTargetHeightFraction = targetHeightFraction;
                    chamberLayouts[i].post(() -> { // Wait for layout
                        int pHeight = chamberLayouts[finalI].getHeight();
                        animateWaterLevel(waterIndicators[finalI], (int) (pHeight * finalTargetHeightFraction));
                    });
                } else {
                    animateWaterLevel(indicator, (int) (parentHeight * targetHeightFraction));
                }
                indicator.setBackgroundResource(waterIndicatorBgRes);
            }
        } else {
            for (int i = 0; i < 3; i++) {
                chamberLayouts[i].setBackgroundResource(defaultBgRes);
                waterLevelTextViews[i].setText("Status");
                waterLevelTextViews[i].setTextColor(ContextCompat.getColor(this, R.color.textColorSecondary));
                animateWaterLevel(waterIndicators[i], (int) (chamberLayouts[i].getHeight() * 0.05f));
            }
        }
    }

    private void animateWaterLevel(View indicatorView, int targetHeight) {
        if (indicatorView == null) return;
        ValueAnimator animator = ValueAnimator.ofInt(indicatorView.getLayoutParams().height, targetHeight);
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams params = indicatorView.getLayoutParams();
            params.height = (Integer) animation.getAnimatedValue();
            indicatorView.setLayoutParams(params);
        });
        animator.setDuration(500);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.start();
    }

    private void updateServoControl(String key, boolean value) {
        if (servoControlRef != null) {
            servoControlRef.child(key).setValue(value)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, key + " updated to " + value))
                    .addOnFailureListener(e -> {
                        Toast.makeText(MainActivity.this, "Failed to update " + key, Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Failed to update " + key, e);
                        // Revert switch state on failure by re-reading from Firebase (or manually)
                        // This avoids immediate revert if user is quick
                        if (key.equals("servo_on") && switchManualServo.isChecked() != value) switchManualServo.setChecked(!value);
                        if (key.equals("auto_mode") && switchAutoMode.isChecked() != value) switchAutoMode.setChecked(!value);
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
        progressBar.setVisibility(View.VISIBLE);
        if (sensorDataRef != null) {
            sensorDataRef.get().addOnCompleteListener(task -> {
                progressBar.setVisibility(View.GONE); // Hide progress bar regardless of success/failure for sensor data
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
                // No separate progress bar for this, happens in background
                if (task.isSuccessful()) {
                    DataSnapshot dataSnapshot = task.getResult();
                    ServoControl control = dataSnapshot.getValue(ServoControl.class);
                    if (control != null) {
                        if (!switchManualServo.isPressed()) switchManualServo.setChecked(control.servo_on);
                        if (!switchAutoMode.isPressed()) switchAutoMode.setChecked(control.auto_mode);
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