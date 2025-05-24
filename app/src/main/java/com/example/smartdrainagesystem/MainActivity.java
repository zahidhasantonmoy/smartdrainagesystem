package com.example.smartdrainagesystem;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final long AUTO_REFRESH_INTERVAL = 30 * 60 * 1000; // 30 minutes in milliseconds
    private TextView chamber1, chamber2, chamber3, sonar1, sonar2, gasMq8, temp, ir, flame, gps, alerts;
    private Button mapButton, refreshButton;
    private Switch servoSwitch, autoSwitch;
    private DatabaseReference sensorRef, servoRef;
    private Handler refreshHandler;
    private Runnable refreshRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI elements
        try {
            chamber1 = findViewById(R.id.chamber1);
            chamber2 = findViewById(R.id.chamber2);
            chamber3 = findViewById(R.id.chamber3);
            sonar1 = findViewById(R.id.sonar1);
            sonar2 = findViewById(R.id.sonar2);
            gasMq8 = findViewById(R.id.gas_mq8);
            temp = findViewById(R.id.temp);
            ir = findViewById(R.id.ir);
            flame = findViewById(R.id.flame);
            gps = findViewById(R.id.gps);
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

        // Setup refresh handler for auto-refresh
        refreshHandler = new Handler(Looper.getMainLooper());
        refreshRunnable = new Runnable() {
            @Override
            public void run() {
                fetchLatestSensorData();
                refreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
                Log.d(TAG, "Auto-refresh triggered");
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

        // Map button
        if (mapButton != null) {
            mapButton.setOnClickListener(v -> {
                try {
                    String gpsText = gps != null ? gps.getText().toString() : "";
                    Log.d(TAG, "Map button clicked, GPS text: " + gpsText);
                    if (!gpsText.equals("Lat: 0.0, Lon: 0.0") && !gpsText.isEmpty() && !gpsText.contains("No GPS lock")) {
                        String[] coords = gpsText.replace("Lat: ", "").split(",");
                        if (coords.length == 2) {
                            String uri = "geo:" + coords[0].trim() + "," + coords[1].trim() + "?q=" + coords[0].trim() + "," + coords[1].trim();
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                            intent.setPackage("com.google.android.apps.maps");
                            startActivity(intent);
                            Log.d(TAG, "Opening Google Maps with URI: " + uri);
                        } else {
                            Log.w(TAG, "Invalid GPS coordinates format");
                            if (alerts != null) alerts.setText("Invalid GPS coordinates");
                        }
                    } else {
                        Log.w(TAG, "No valid GPS data for map");
                        if (alerts != null) alerts.setText("No GPS data available");
                    }
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

        // Initial data fetch
        fetchLatestSensorData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (refreshHandler != null && refreshRunnable != null) {
            refreshHandler.removeCallbacks(refreshRunnable);
            Log.d(TAG, "Auto-refresh stopped");
        }
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
                            break; // Process only the latest entry
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

        // Water levels
        try {
            Long w1 = snapshot.child("data/water_levels/0").getValue(Long.class);
            Long w2 = snapshot.child("data/water_levels/1").getValue(Long.class);
            Long w3 = snapshot.child("data/water_levels/2").getValue(Long.class);
            Log.d(TAG, "Water levels: w1=" + w1 + ", w2=" + w2 + ", w3=" + w3);
            if (chamber1 != null) chamber1.setText("Chamber 1: " + (w1 != null && w1 == 1 ? "Full" : "Empty"));
            if (chamber2 != null) chamber2.setText("Chamber 2: " + (w2 != null && w2 == 1 ? "Full" : "Empty"));
            if (chamber3 != null) chamber3.setText("Chamber 3: " + (w3 != null && w3 == 1 ? "Full" : "Empty"));

            // Blockage detection
            boolean blockage = (w1 != null && w2 != null && w3 != null) &&
                    ((w1 == 1 && w2 == 1 && w3 == 0) ||
                            (w1 == 1 && w3 == 1 && w2 == 0) ||
                            (w2 == 1 && w3 == 1 && w1 == 0));
            Long blockedChamber = snapshot.child("data/blocked_chamber").getValue(Long.class);
            Log.d(TAG, "Blockage detected: " + blockage + ", blockedChamber: " + blockedChamber);

            // Reset backgrounds
            if (chamber1 != null) chamber1.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));
            if (chamber2 != null) chamber2.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));
            if (chamber3 != null) chamber3.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));

            if (blockage && blockedChamber != null) {
                if (blockedChamber == 1 && chamber1 != null) {
                    chamber1.setBackground(new ColorDrawable(getResources().getColor(R.color.red)));
                } else if (blockedChamber == 2 && chamber2 != null) {
                    chamber2.setBackground(new ColorDrawable(getResources().getColor(R.color.red)));
                } else if (blockedChamber == 3 && chamber3 != null) {
                    chamber3.setBackground(new ColorDrawable(getResources().getColor(R.color.red)));
                }
                String type = snapshot.child("data/blockage_type").getValue(String.class);
                if (alerts != null) {
                    alerts.setText("Blockage Detected: " + (type != null ? type : "Unknown"));
                    alerts.setBackground(new ColorDrawable(getResources().getColor(R.color.yellow)));
                }
                // Auto-enable servo
                if (servoRef != null) {
                    servoRef.child("servo_on").setValue(true);
                    servoRef.child("auto_mode").setValue(true);
                }
                Log.d(TAG, "Blockage alert set: " + (type != null ? type : "Unknown"));
            } else {
                String alertType = snapshot.child("alert").getValue(String.class);
                if (alerts != null) {
                    if (alertType != null && !alertType.equals("None")) {
                        alerts.setText(alertType);
                        alerts.setBackground(new ColorDrawable(getResources().getColor(R.color.yellow)));
                    } else {
                        alerts.setText("No alerts");
                        alerts.setBackground(new ColorDrawable(getResources().getColor(R.color.transparent)));
                    }
                }
                Log.d(TAG, "Alert set: " + (alertType != null ? alertType : "None"));
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
        } catch (Exception e) {
            Log.e(TAG, "Error processing sensor data: " + e.getMessage(), e);
        }

        // GPS
        try {
            String gpsCoords = snapshot.child("gps").getValue(String.class);
            if (gps != null) {
                gps.setText(gpsCoords != null && !gpsCoords.equals("No GPS lock") ? "Lat: " + gpsCoords : "Lat: 0.0, Lon: 0.0");
                Log.d(TAG, "GPS set to: " + (gpsCoords != null ? gpsCoords : "No GPS lock"));
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing GPS data: " + e.getMessage(), e);
        }
    }
}