package com.example.smartdrainagesystem;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
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
    private TextView chamber1, chamber2, chamber3, sonar1, sonar2, gasMq8, temp, ir, flame, gps, alerts;
    private Button mapButton;
    private Switch servoSwitch, autoSwitch;
    private DatabaseReference sensorRef, servoRef;

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
            servoSwitch = findViewById(R.id.servo_switch);
            autoSwitch = findViewById(R.id.auto_switch);
            alerts = findViewById(R.id.alerts);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI: " + e.getMessage());
            if (alerts != null) alerts.setText("UI init failed");
            return;
        }

        // Initialize Firebase
        try {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            sensorRef = database.getReference("sensor_data");
            servoRef = database.getReference("servo_control");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: " + e.getMessage());
            if (alerts != null) alerts.setText("Firebase init failed");
            return;
        }

        // Real-time sensor data listener
        sensorRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                try {
                    if (snapshot.exists()) {
                        updateDashboard(snapshot);
                    } else {
                        Log.w(TAG, "No sensor data available");
                        if (alerts != null) alerts.setText("No data available");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error updating dashboard: " + e.getMessage());
                    if (alerts != null) alerts.setText("Error updating data");
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Firebase error: " + error.getMessage());
                if (alerts != null) alerts.setText("Error: " + error.getMessage());
            }
        });

        // Map button
        if (mapButton != null) {
            mapButton.setOnClickListener(v -> {
                try {
                    String gpsText = gps != null ? gps.getText().toString() : "";
                    if (!gpsText.equals("Lat: 0.0, Lon: 0.0") && !gpsText.isEmpty()) {
                        String[] coords = gpsText.replace("Lat: ", "").replace("Lon: ", "").split(",");
                        if (coords.length == 2) {
                            String uri = "geo:" + coords[0].trim() + "," + coords[1].trim() + "?q=" + coords[0].trim() + "," + coords[1].trim();
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                            intent.setPackage("com.google.android.apps.maps");
                            startActivity(intent);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error opening Google Maps: " + e.getMessage());
                    if (alerts != null) alerts.setText("Error opening Maps");
                }
            });
        }

        // Servo controls
        if (servoSwitch != null) {
            servoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    servoRef.child("servo_on").setValue(isChecked);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting servo state: " + e.getMessage());
                }
            });
        }

        if (autoSwitch != null) {
            autoSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    servoRef.child("auto_mode").setValue(isChecked);
                } catch (Exception e) {
                    Log.e(TAG, "Error setting auto mode: " + e.getMessage());
                }
            });
        }
    }

    private void updateDashboard(DataSnapshot snapshot) {
        // Get latest sensor data
        DataSnapshot latest = null;
        for (DataSnapshot child : snapshot.getChildren()) {
            latest = child;
        }
        if (latest == null) return;

        // Water levels
        Long w1 = latest.child("data/water_levels/0").getValue(Long.class);
        Long w2 = latest.child("data/water_levels/1").getValue(Long.class);
        Long w3 = latest.child("data/water_levels/2").getValue(Long.class);
        if (chamber1 != null) chamber1.setText("Chamber 1: " + (w1 != null && w1 == 1 ? "Full" : "Empty"));
        if (chamber2 != null) chamber2.setText("Chamber 2: " + (w2 != null && w2 == 1 ? "Full" : "Empty"));
        if (chamber3 != null) chamber3.setText("Chamber 3: " + (w3 != null && w3 == 1 ? "Full" : "Empty"));

        // Blockage detection
        boolean blockage = (w1 != null && w2 != null && w3 != null) &&
                ((w1 == 1 && w2 == 1 && w3 == 0) ||
                        (w1 == 1 && w3 == 1 && w2 == 0) ||
                        (w2 == 1 && w3 == 1 && w1 == 0));
        if (blockage) {
            if (chamber1 != null && w1 == 0) chamber1.setBackground(new ColorDrawable(getResources().getColor(R.color.red)));
            else if (chamber1 != null) chamber1.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));
            if (chamber2 != null && w2 == 0) chamber2.setBackground(new ColorDrawable(getResources().getColor(R.color.red)));
            else if (chamber2 != null) chamber2.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));
            if (chamber3 != null && w3 == 0) chamber3.setBackground(new ColorDrawable(getResources().getColor(R.color.red)));
            else if (chamber3 != null) chamber3.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));
            String type = latest.child("data/blockage_type").getValue(String.class);
            if (alerts != null) alerts.setText("Blockage Detected: " + (type != null ? type : "Unknown"));
        } else {
            if (chamber1 != null) chamber1.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));
            if (chamber2 != null) chamber2.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));
            if (chamber3 != null) chamber3.setBackground(new ColorDrawable(getResources().getColor(R.color.blue)));
            String alertType = latest.child("alert").getValue(String.class);
            if (alerts != null) {
                if (alertType != null && !alertType.equals("None")) {
                    alerts.setText(alertType);
                    alerts.setBackground(new ColorDrawable(getResources().getColor(R.color.yellow)));
                } else {
                    alerts.setText("No alerts");
                    alerts.setBackground(new ColorDrawable(getResources().getColor(R.color.transparent)));
                }
            }
        }

        // Sensor data
        Double dist1 = latest.child("data/distance1").getValue(Double.class);
        Double dist2 = latest.child("data/distance2").getValue(Double.class);
        Double mq8 = latest.child("data/mq8").getValue(Double.class);
        Double tempVal = latest.child("data/temp").getValue(Double.class);
        Long irVal = latest.child("data/ir").getValue(Long.class);
        Long flameVal = latest.child("data/flame").getValue(Long.class);
        if (sonar1 != null) sonar1.setText("Sonar 1: " + (dist1 != null ? String.format("%.1f", dist1) : "0.0") + " cm");
        if (sonar2 != null) sonar2.setText("Sonar 2: " + (dist2 != null ? String.format("%.1f", dist2) : "0.0") + " cm");
        if (gasMq8 != null) gasMq8.setText("MQ8 Gas: " + (mq8 != null ? String.format("%.2f", mq8) : "0.00") + " V");
        if (temp != null) temp.setText("Temperature: " + (tempVal != null ? String.format("%.1f", tempVal) : "0.0") + " °C");
        if (ir != null) ir.setText("IR: " + (irVal != null && irVal == 1 ? "Detected" : "Not Detected"));
        if (flame != null) flame.setText("Flame: " + (flameVal != null && flameVal == 1 ? "Detected" : "Not Detected"));

        // GPS
        String gpsCoords = latest.child("gps").getValue(String.class);
        if (gps != null) {
            gps.setText(gpsCoords != null && !gpsCoords.equals("No GPS lock") ? "Lat: " + gpsCoords : "Lat: 0.0, Lon: 0.0");
        }
    }
}