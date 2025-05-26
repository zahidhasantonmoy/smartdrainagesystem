package com.example.smartdrainagesystem;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final long AUTO_REFRESH_INTERVAL = 2000; // 2 seconds
    private static final long SERVO_ON_DURATION = 10 * 1000; // 10 seconds
    private static final long SERVO_OFF_DURATION = 10 * 60 * 1000; // 10 minutes
    private static final double DEFAULT_LAT = 23.811855;
    private static final double DEFAULT_LON = 90.357140;

    private CardView chamber1Card, chamber2Card, chamber3Card;
    private ProgressBar chamber1Progress, chamber2Progress, chamber3Progress;
    private TextView chamber1Status, chamber2Status, chamber3Status;
    private TextView sonar1, sonar2, gasMq8, temp, ir, flame, gps, proximityAlert, alerts;
    private Button mapButton, refreshButton;
    private Switch servoSwitch, autoSwitch;
    private DatabaseReference sensorRef, servoRef;
    private Handler refreshHandler, servoHandler;
    private Runnable refreshRunnable, servoOffRunnable;
    private boolean isServoAutoRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        try {
            chamber1Card = findViewById(R.id.chamber1_card);
            chamber2Card = findViewById(R.id.chamber2_card);
            chamber3Card = findViewById(R.id.chamber3_card);
            chamber1Progress = findViewById(R.id.chamber1_progress);
            chamber2Progress = findViewById(R.id.chamber2_progress);
            chamber3Progress = findViewById(R.id.chamber3_progress);
            chamber1Status = findViewById(R.id.chamber1_status);
            chamber2Status = findViewById(R.id.chamber2_status);
            chamber3Status = findViewById(R.id.chamber3_status);
            sonar1 = findViewById(R.id.sonar1);
            sonar2 = findViewById(R.id.sonar2);
            gasMq8 = findViewById(R.id.gas_mq8);
            temp = findViewById(R.id.temp);
            ir = findViewById(R.id.ir);
            flame = findViewById(R.id.flame);
            gps = findViewById(R.id.gps);
            proximityAlert = findViewById(R.id.proximity_alert);
            mapButton = findViewById(R.id.map_button);
            refreshButton = findViewById(R.id.refresh_button);
            servoSwitch = findViewById(R.id.servo_switch);
            autoSwitch = findViewById(R.id.auto_switch);
            alerts = findViewById(R.id.alerts);
            Log.d(TAG, "UI elements initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI: " + e.getMessage(), e);
            if (alerts != null) alerts.setText("UI initialization failed");
            return;
        }

        // Initialize Firebase
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            sensorRef = database.getReference("sensor_data");
            servoRef = database.getReference("servo_control");
            Log.d(TAG, "Firebase initialized with sensorRef: " + sensorRef.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: " + e.getMessage(), e);
            if (alerts != null) alerts.setText("Firebase initialization failed");
            return;
        }

        // Initialize handlers
        refreshHandler = new Handler(Looper.getMainLooper());
        servoHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                fetchLatestSensorData();
                refreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
                Log.d(TAG, "Auto-refresh triggered");
            }
        };
        servoOffRunnable = () -> {
            if (servoRef != null && isServoAutoRunning) {
                servoRef.child("servo_on").setValue(false);
                isServoAutoRunning = false;
                Log.d(TAG, "Servo turned off after 10 seconds");
            }
        };
        refreshHandler.postDelayed(refreshRunnable, AUTO_REFRESH_INTERVAL);

        // Refresh button
        if (refreshButton != null) {
            refreshButton.setOnClickListener(v -> {
                fetchLatestSensorData();
                Log.d(TAG, "Refresh button clicked");
            });
        }

        // Map button
        if (mapButton != null) {
            mapButton.setOnClickListener(v -> {
                try {
                    String gpsText = gps != null ? gps.getText().toString() : "";
                    Log.d(TAG, "Map button clicked, GPS text: " + gpsText);
                    double lat = DEFAULT_LAT;
                    double lon = DEFAULT_LON;
                    if (!gpsText.contains("No GPS lock")) {
                        String[] coords = gpsText.replace("Lat: ", "").split(", Lon: ");
                        if (coords.length == 2) {
                            lat = Double.parseDouble(coords[0]);
                            lon = Double.parseDouble(coords[1]);
                        }
                    }
                    String uri = "geo:" + lat + "," + lon + "?q=" + lat + "," + lon;
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.setPackage("com.google.android.apps.maps");
                    startActivity(intent);
                    Log.d(TAG, "Opening Google Maps with URI: " + uri);
                } catch (Exception e) {
                    Log.e(TAG, "Error opening Google Maps: " + e.getMessage(), e);
                    if (alerts != null) alerts.setText("Error opening Maps");
                }
            });
        }

        // Servo controls
        if (servoSwitch != null) {
            servoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    servoRef.child("servo_on").setValue(isChecked);
                    Log.d(TAG, "Servo switch set to: " + isChecked);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting servo state: " + e.getMessage(), e);
                    if (alerts != null) alerts.setText("Error updating servo");
                }
            });
        }

        if (autoSwitch != null) {
            autoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    servoRef.child("auto_mode").setValue(isChecked);
                    Log.d(TAG, "Auto mode switch set to: " + isChecked);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting auto mode: " + e.getMessage(), e);
                    if (alerts != null) alerts.setText("Error updating auto mode");
                }
            });
        }

        // Listen for servo control changes
        servoRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    Boolean servoOn = snapshot.child("servo_on").getValue(Boolean.class);
                    Boolean autoMode = snapshot.child("auto_mode").getValue(Boolean.class);
                    Log.d(TAG, "Servo control update: servo_on=" + servoOn + ", auto_mode=" + autoMode);
                    if (servoSwitch != null && servoOn != null) {
                        servoSwitch.setChecked(servoOn);
                    }
                    if (autoSwitch != null && autoMode != null) {
                        autoSwitch.setChecked(autoMode);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating servo controls: " + e.getMessage(), e);
                    if (alerts != null) alerts.setText("Error updating servo controls");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Servo control listener cancelled: " + error.getMessage());
                if (alerts != null) alerts.setText("Servo control error");
            }
        });

        // Real-time sensor data listener
        sensorRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    if (snapshot.exists()) {
                        Log.d(TAG, "Firebase snapshot received: " + snapshot.getValue().toString());
                        DataSnapshot latest = null;
                        for (DataSnapshot child : snapshot.getChildren()) {
                            latest = child;
                        }
                        if (latest != null) {
                            updateDashboard(latest);
                        } else {
                            Log.w(TAG, "No valid sensor data entries found");
                            if (alerts != null) alerts.setText("No valid sensor data");
                        }
                    } else {
                        Log.w(TAG, "No sensor data available in snapshot");
                        if (alerts != null) alerts.setText("No sensor data available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing Firebase data: " + e.getMessage(), e);
                    if (alerts != null) alerts.setText("Error updating sensor data");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase listener cancelled: " + error.getMessage());
                if (alerts != null) alerts.setText("Firebase error: " + error.getMessage());
            }
        });

        // Initial data fetch
        fetchLatestSensorData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
        }
        if (servoHandler != null && servoOffRunnable != null) {
            servoHandler.removeCallbacks(servoOffRunnable);
        }
        Log.d(TAG, "Handlers stopped");
    }

    private void fetchLatestSensorData() {
        if (sensorRef == null) {
            Log.e(TAG, "sensorRef is null, cannot fetch data");
            if (alerts != null) alerts.setText("Firebase not initialized");
            return;
        }
        sensorRef.orderByChild("timestamp").limitToLast(1).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    if (snapshot.exists()) {
                        Log.d(TAG, "Manual refresh snapshot: " + snapshot.getValue().toString());
                        for (DataSnapshot child : snapshot.getChildren()) {
                            updateDashboard(child);
                            break;
                        }
                    } else {
                        Log.w(TAG, "No sensor data available on refresh");
                        if (alerts != null) alerts.setText("No sensor data available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error during manual refresh: " + e.getMessage(), e);
                    if (alerts != null) alerts.setText("Error refreshing data");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Manual refresh cancelled: " + error.getMessage());
                if (alerts != null) alerts.setText("Refresh error: " + error.getMessage());
            }
        });
    }

    private void updateDashboard(DataSnapshot snapshot) {
        Log.d(TAG, "Updating dashboard with snapshot: " + snapshot.getValue().toString());

        // Water levels and blockage
        try {
            Long w1 = snapshot.child("data/water_levels/0").getValue(Long.class);
            Long w2 = snapshot.child("data/water_levels/1").getValue(Long.class);
            Long w3 = snapshot.child("data/water_levels/2").getValue(Long.class);
            Log.d(TAG, "Water levels: w1=" + w1 + ", w2=" + w2 + ", w3=" + w3);

            // Update chambers
            if (chamber1Progress != null) chamber1Progress.setProgress(w1 != null && w1 == 1 ? 100 : 0);
            if (chamber2Progress != null) chamber2Progress.setProgress(w2 != null && w2 == 1 ? 100 : 0);
            if (chamber3Progress != null) chamber3Progress.setProgress(w3 != null && w3 == 1 ? 100 : 0);
            if (chamber1Status != null) chamber1Status.setText(w1 != null && w1 == 1 ? "Not OK" : "OK");
            if (chamber2Status != null) chamber2Status.setText(w2 != null && w2 == 1 ? "Not OK" : "OK");
            if (chamber3Status != null) chamber3Status.setText(w3 != null && w3 == 1 ? "Not OK" : "OK");

            // Blockage detection
            boolean blockage = (w1 != null && w2 != null && w3 != null) &&
                    ((w1 == 1 && w2 == 1 && w3 == 0) ||
                            (w1 == 1 && w3 == 1 && w2 == 0) ||
                            (w2 == 1 && w3 == 1 && w1 == 0));
            Long blockedChamber = snapshot.child("data/blocked_chamber").getValue(Long.class);
            Log.d(TAG, "Blockage detected: " + blockage + ", blockedChamber: " + blockedChamber);

            // Reset chamber colors
            if (chamber1Card != null) chamber1Card.setCardBackgroundColor(getResources().getColor(R.color.blue));
            if (chamber2Card != null) chamber2Card.setCardBackgroundColor(getResources().getColor(R.color.blue));
            if (chamber3Card != null) chamber3Card.setCardBackgroundColor(getResources().getColor(R.color.blue));

            // Set alerts and servo for blockage
            String alertText = "No alerts";
            boolean hasAlert = false;
            if (blockage && blockedChamber != null) {
                if (blockedChamber == 1 && chamber1Card != null) {
                    chamber1Card.setCardBackgroundColor(getResources().getColor(R.color.red));
                } else if (blockedChamber == 2 && chamber2Card != null) {
                    chamber2Card.setCardBackgroundColor(getResources().getColor(R.color.red));
                } else if (blockedChamber == 3 && chamber3Card != null) {
                    chamber3Card.setCardBackgroundColor(getResources().getColor(R.color.red));
                }
                String type = snapshot.child("data/blockage_type").getValue(String.class);
                alertText = "Blockage Detected: " + (type != null ? type : "Unknown");
                hasAlert = true;
                // Auto servo control
                if (autoSwitch != null && autoSwitch.isChecked() && !isServoAutoRunning) {
                    servoRef.child("servo_on").setValue(true);
                    isServoAutoRunning = true;
                    servoHandler.postDelayed(servoOffRunnable, SERVO_ON_DURATION);
                    Log.d(TAG, "Servo turned on for 10 seconds due to blockage");
                }
                Log.d(TAG, "Blockage alert set: " + alertText);
            } else {
                String alertType = snapshot.child("alert").getValue(String.class);
                if (alertType != null && !alertType.equals("None")) {
                    alertText = alertType;
                    hasAlert = true;
                }
                Log.d(TAG, "Alert set: " + alertText);
            }

            // Update alerts
            if (alerts != null) {
                alerts.setText(alertText);
                alerts.setBackground(new ColorDrawable(getResources().getColor(hasAlert ? R.color.red : R.color.transparent)));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing water levels or blockage: " + e.getMessage(), e);
        }

        // Sensor data
        try {
            Double dist1 = snapshot.child("data/distance1").getValue(Double.class);
            Double dist2 = snapshot.child("data/distance2").getValue(Double.class);
            Double mq8 = snapshot.child("data/mq8").getValue(Double.class);
            Double tempVal = snapshot.child("data/temp").getValue(Double.class);
            Long irVal = snapshot.child("data/ir").getValue(Long.class);
            Long flameVal = snapshot.child("data/flame").getValue(Long.class);
            Log.d(TAG, "Sensor data: dist1=" + dist1 + ", dist2=" + dist2 + ", mq8=" + mq8 + ", temp=" + tempVal + ", ir=" + irVal + ", flame=" + flameVal);

            if (sonar1 != null) sonar1.setText("Sonar 1: " + (dist1 != null ? String.format("%.1f", dist1) : "0.0") + " cm");
            if (sonar2 != null) sonar2.setText("Sonar 2: " + (dist2 != null ? String.format("%.1f", dist2) : "0.0") + " cm");
            if (gasMq8 != null) gasMq8.setText("MQ8 Gas: " + (mq8 != null ? String.format("%.2f", mq8) : "0.00") + " V");
            if (temp != null) temp.setText("Temperature: " + (tempVal != null ? String.format("%.1f", tempVal) : "0.0") + " °C");
            if (ir != null) ir.setText("IR: " + (irVal != null && irVal == 1 ? "Detected" : "Not Detected"));
            if (flame != null) flame.setText("Flame: " + (flameVal != null && flameVal == 1 ? "Detected" : "Not Detected"));

            // Proximity alert
            boolean proximity = (dist1 != null && dist1 < 5.0) || (dist2 != null && dist2 < 5.0);
            if (proximityAlert != null) {
                proximityAlert.setText(proximity ? "Proximity Alert: Object < 5 cm" : "Proximity: Safe");
                if (proximity && !alerts.getText().toString().contains("Proximity")) {
                    String currentAlert = alerts.getText().toString();
                    alerts.setText(currentAlert.equals("No alerts") ? "Proximity Alert" : currentAlert + "; Proximity Alert");
                    alerts.setBackground(new ColorDrawable(getResources().getColor(R.color.red)));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing sensor data: " + e.getMessage(), e);
        }

        // GPS
        try {
            String gpsCoords = snapshot.child("gps").getValue(String.class);
            if (gps != null) {
                if (gpsCoords != null && !gpsCoords.equals("No GPS lock")) {
                    gps.setText("Lat: " + gpsCoords);
                } else {
                    gps.setText(String.format("Lat: %.6f, Lon: %.6f", DEFAULT_LAT, DEFAULT_LON));
                }
                Log.d(TAG, "GPS set to: " + gps.getText());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing GPS data: " + e.getMessage(), e);
        }
    }
}