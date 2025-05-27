package com.example.smartdrainagesystem; // Replace with your package name

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class SensorFirebaseData {
    public SensorDetails data;
    public String gps;
    public String alert;
    public long timestamp;

    public SensorFirebaseData() {
        // Default constructor required for calls to DataSnapshot.getValue(SensorFirebaseData.class)
    }
}