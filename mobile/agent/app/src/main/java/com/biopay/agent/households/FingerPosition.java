package com.biopay.agent.households;

/**
 * The ten standard tenprint finger positions (ANSI/NIST numbering, the same order used on paper
 * tenprint cards): 1-5 are the right hand's thumb through little finger, 6-10 are the left hand's
 * thumb through little finger. Stored as-is in {@code fingerprints.fingerprint_number} and passed
 * straight through to {@link com.biopay.agent.biometric.BiometricDevice#startCapture}.
 */
public final class FingerPosition {

    public static final int RIGHT_THUMB = 1;
    public static final int RIGHT_INDEX = 2;
    public static final int RIGHT_MIDDLE = 3;
    public static final int RIGHT_RING = 4;
    public static final int RIGHT_LITTLE = 5;
    public static final int LEFT_THUMB = 6;
    public static final int LEFT_INDEX = 7;
    public static final int LEFT_MIDDLE = 8;
    public static final int LEFT_RING = 9;
    public static final int LEFT_LITTLE = 10;

    /** Right hand, thumb to little finger, in on-screen left-to-right order. */
    public static final int[] RIGHT_HAND = {RIGHT_THUMB, RIGHT_INDEX, RIGHT_MIDDLE, RIGHT_RING, RIGHT_LITTLE};
    /** Left hand, thumb to little finger, in on-screen left-to-right order. */
    public static final int[] LEFT_HAND = {LEFT_THUMB, LEFT_INDEX, LEFT_MIDDLE, LEFT_RING, LEFT_LITTLE};

    private FingerPosition() { }

    public static String shortLabel(int position) {
        switch (position) {
            case RIGHT_THUMB: case LEFT_THUMB: return "Th";
            case RIGHT_INDEX: case LEFT_INDEX: return "Ix";
            case RIGHT_MIDDLE: case LEFT_MIDDLE: return "Mid";
            case RIGHT_RING: case LEFT_RING: return "Rg";
            case RIGHT_LITTLE: case LEFT_LITTLE: return "Lt";
            default: return "?";
        }
    }

    public static String fullLabel(int position) {
        switch (position) {
            case RIGHT_THUMB: return "Right thumb";
            case RIGHT_INDEX: return "Right index finger";
            case RIGHT_MIDDLE: return "Right middle finger";
            case RIGHT_RING: return "Right ring finger";
            case RIGHT_LITTLE: return "Right little finger";
            case LEFT_THUMB: return "Left thumb";
            case LEFT_INDEX: return "Left index finger";
            case LEFT_MIDDLE: return "Left middle finger";
            case LEFT_RING: return "Left ring finger";
            case LEFT_LITTLE: return "Left little finger";
            default: return "Finger " + position;
        }
    }
}
