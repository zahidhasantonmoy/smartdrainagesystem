package com.example.smartdrainagesystem; // Replace with your package name

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class ServoControl {
    public boolean servo_on;
    public boolean auto_mode;

    public ServoControl() {
        // Default constructor required for calls to DataSnapshot.getValue(ServoControl.class)
    }

    public ServoControl(boolean servo_on, boolean auto_mode) {
        this.servo_on = servo_on;
        this.auto_mode = auto_mode;
    }
}