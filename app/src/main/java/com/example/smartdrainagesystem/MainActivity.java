package com.example.smartdrainagesystem;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private TextView chamber1, chamber2, chamber3, gasMq2, gasMq8, temp, ir, flame, gps, alerts;
    private Button mapButton, manualCut;
    private Switch autoServo;
    private FirebaseFirestore db;
    private ListenerRegistration listener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
        } catch (Exception e) {
            Log.e(TAG, "Error setting content view: " + e.getMessage());
            return;
        }

        // Initialize UI elements
        try {
            chamber1 = findViewById(R.id.chamber1);
            chamber2 = findViewById(R.id.chamber2);
            chamber3 = findViewById(R.id.chamber3);
            gasMq2 = findViewById(R.id.gas_mq2);
            gasMq8 = findViewById(R.id.gas_mq8);
            temp = findViewById(R.id.temp);
            ir = findViewById(R.id.ir);
            flame = findViewById(R.id.flame);
            gps = findViewById(R.id.gps);
            mapButton = findViewById(R.id.map_button);
            autoServo = findViewById(R.id.auto_servo);
            manualCut = findViewById(R.id.manual_cut);
            alerts = findViewById(R.id.alerts);
        } catch (Exception e) {
            Log.e(TAG, "Error initializing UI: " + e.getMessage());
            return;
        }

        // Initialize Firebase
        try {
            db = FirebaseFirestore.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase: " + e.getMessage());
            alerts.setText("Firebase init failed");
            return;
        }

        // Real-time Firestore listener
        try {
            listener = db.collection("drainage").document("latest")
                    .addSnapshotListener((snapshot, e) -> {
                        if (e != null) {
                            Log.e(TAG, "Firestore error: " + e.getMessage());
                            if (alerts != null) alerts.setText("Error: " + e.getMessage());
                            return;
                        }
                        if (snapshot != null && snapshot.exists()) {
                            updateDashboard(snapshot);
                        } else {
                            Log.w(TAG, "Snapshot is null or does not exist");
                            if (alerts != null) alerts.setText("No data available");
                        }
                    });
        } catch (Exception e) {
            Log.e(TAG, "Error setting Firestore listener: " + e.getMessage());
            if (alerts != null) alerts.setText("Firestore listener failed");
        }

        // Map button
        if (mapButton != null) {
            mapButton.setOnClickListener(v -> {
                try {
                    String gpsText = gps != null ? gps.getText().toString() : "";
                    if (!gpsText.equals("Lat: 0.0, Lon: 0.0") && !gpsText.isEmpty()) {
                        String[] coords = gpsText.replace("Lat: ", "").replace("Lon: ", "").split(", ");
                        if (coords.length == 2) {
                            String uri = "geo:" + coords[0] + "," + coords[1] + "?q=" + coords[0] + "," + coords[1];
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
        if (autoServo != null) {
            autoServo.setOnCheckedChangeListener((buttonView, isChecked) -> {
                try {
                    db.collection("drainage").document("control")
                            .set(new ServoControl(isChecked))
                            .addOnFailureListener(e -> Log.e(TAG, "Error updating servo control: " + e.getMessage()));
                } catch (Exception e) {
                    Log.e(TAG, "Error setting auto servo: " + e.getMessage());
                }
            });
        }

        if (manualCut != null) {
            manualCut.setOnClickListener(v -> {
                try {
                    boolean autoState = autoServo != null && autoServo.isChecked();
                    db.collection("drainage").document("control")
                            .set(new ServoControl(autoState, true))
                            .addOnFailureListener(e -> Log.e(TAG, "Error triggering manual cut: " + e.getMessage()));
                } catch (Exception e) {
                    Log.e(TAG, "Error triggering manual cut: " + e.getMessage());
                }
            });
        }
    }

    private void updateDashboard(DocumentSnapshot snapshot) {
        try {
            // Water levels
            Long w1 = snapshot.getLong("data.water_levels[0]");
            Long w2 = snapshot.getLong("data.water_levels[1]");
            Long w3 = snapshot.getLong("data.water_levels[2]");
            if (chamber1 != null) chamber1.setText("Chamber 1: " + (w1 != null && w1 == 1 ? "Full" : "Empty"));
            if (chamber2 != null) chamber2.setText("Chamber 2: " + (w2 != null && w2 == 1 ? "Full" : "Empty"));
            if (chamber3 != null) chamber3.setText("Chamber 3: " + (w3 != null && w3 == 1 ? "Full" : "Empty"));
            if (w1 != null && w2 != null && w3 != null && w1 == 1 && w2 == 1 && w3 == 0) {
                if (chamber3 != null) chamber3.setBackgroundColor(getResources().getColor(R.color.red));
                String type = snapshot.getString("data.type");
                if (alerts != null) alerts.setText("Blockage Detected: " + (type != null ? type : "Unknown"));
            } else {
                if (chamber1 != null) chamber1.setBackgroundColor(getResources().getColor(R.color.blue));
                if (chamber2 != null) chamber2.setBackgroundColor(getResources().getColor(R.color.blue));
                if (chamber3 != null) chamber3.setBackgroundColor(getResources().getColor(R.color.blue));
                if (alerts != null) alerts.setText("No alerts");
            }

            // Sensor data
            Double mq2 = snapshot.getDouble("data.mq2");
            Double mq8 = snapshot.getDouble("data.mq8");
            Double tempVal = snapshot.getDouble("data.temp");
            Long irVal = snapshot.getLong("data.ir");
            Long flameVal = snapshot.getLong("data.flame");
            if (gasMq2 != null) gasMq2.setText("MQ2 Gas: " + (mq2 != null ? mq2 : 0.0) + " V");
            if (gasMq8 != null) gasMq8.setText("MQ8 Gas: " + (mq8 != null ? mq8 : 0.0) + " V");
            if (temp != null) temp.setText("Temperature: " + (tempVal != null ? tempVal : 0.0) + " °C");
            if (ir != null) ir.setText("IR: " + (irVal != null && irVal == 1 ? "Detected" : "Not Detected"));
            if (flame != null) flame.setText("Flame: " + (flameVal != null && flameVal == 1 ? "Detected" : "Not Detected"));

            // GPS
            String gpsCoords = snapshot.getString("gps");
            if (gps != null) {
                gps.setText(gpsCoords != null && !gpsCoords.equals("No GPS lock") ? gpsCoords : "Lat: 0.0, Lon: 0.0");
            }

            // Alerts
            String alertType = snapshot.getString("alert");
            if (alertType != null && !alertType.equals("None")) {
                String type = snapshot.getString("data.type");
                if (alerts != null) {
                    alerts.setText(alertType + ": " + (type != null ? type : "Unknown"));
                    alerts.setBackgroundColor(getResources().getColor(R.color.yellow));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error updating dashboard: " + e.getMessage());
            if (alerts != null) alerts.setText("Error updating data");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listener != null) {
            try {
                listener.remove();
            } catch (Exception e) {
                Log.e(TAG, "Error removing listener: " + e.getMessage());
            }
        }
    }

    public static class ServoControl {
        boolean auto;
        boolean manualCut;

        public ServoControl(boolean auto) {
            this.auto = auto;
            this.manualCut = false;
        }

        public ServoControl(boolean auto, boolean manualCut) {
            this.auto = auto;
            this.manualCut = manualCut;
        }
    }
}