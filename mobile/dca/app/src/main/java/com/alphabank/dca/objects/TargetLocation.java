package com.alphabank.dca.objects;

public class TargetLocation {
    String targetLocationId,targetLocationName;

    public TargetLocation(String targetLocationId, String targetLocationName) {
        this.targetLocationId = targetLocationId;
        this.targetLocationName = targetLocationName;
    }

    public String getTargetLocationId() {
        return targetLocationId;
    }

    public void setTargetLocationId(String targetLocationId) {
        this.targetLocationId = targetLocationId;
    }

    public String getTargetLocationName() {
        return targetLocationName;
    }

    public void setTargetLocationName(String targetLocationName) {
        this.targetLocationName = targetLocationName;
    }

    @Override
    public String toString() {
        return targetLocationName;
    }
    @Override
    public boolean equals(Object obj) {
        if(obj instanceof TargetLocation){
            TargetLocation c = (TargetLocation)obj;
            if(c.getTargetLocationName().equals(targetLocationName) && c.getTargetLocationId()==targetLocationId) return true;
        }

        return false;
    }
}
