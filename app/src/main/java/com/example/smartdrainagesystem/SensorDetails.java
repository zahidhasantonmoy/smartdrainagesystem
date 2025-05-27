package com.example.smartdrainagesystem; // Replace with your package name

import com.google.firebase.database.IgnoreExtraProperties;
import java.util.List;

@IgnoreExtraProperties
public class SensorDetails {
    public String blockage_type;
    public Integer blocked_chamber;
    public List<Integer> water_levels;
    public Double distance1;
    public Double distance2;
    public Double mq8;
    public Double temp;
    public Integer ir;
    public Integer flame;

    public SensorDetails() {
        // Default constructor required for calls to DataSnapshot.getValue(SensorDetails.class)
    }

    // You can add getters if needed, or access fields directly
}