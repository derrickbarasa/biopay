// IDEMIA's Peripheral Management service surface for ID Screen / ID Screen 60 tablets, bound via
// action "idemia.intent.action.CONN_PERIPHERALS_SERVICE_AIDL" against package "com.android.settings".
//
// Transcribed from IDEMIA's "ID Screen SDK Programming Guide v2.0" (Peripheral Management chapter,
// Programming_Guide_260822_161533.pdf). The guide documents each method's usage but does not ship
// the raw .aidl source, so the declaration order below follows the guide's own presentation order
// (Fingerprint sensor, Host USB port, Docking station USB port, NFC reader, USB role). AIDL assigns
// binder transaction IDs by declaration order, so if IDEMIA's real service interface orders these
// differently, a call here would silently invoke the wrong remote method instead of failing loudly.
// Verify on real ID Screen hardware with a set/get round-trip (e.g. setFingerPrintSwitch(true) then
// getFingerPrintSwitch() reads back true) before trusting this beyond the fingerprint/host-USB pair
// MorphoDeviceAdapter actually calls.
package com.idemia.peripherals;

interface PeripheralsPowerInterface {
    boolean setFingerPrintSwitch(boolean flag);
    boolean getFingerPrintSwitch();

    boolean setHostUsbPortSwitch(boolean flag);
    boolean getHostUsbPortSwitch();

    boolean setDockingStationUsbPortSwitch(boolean flag);
    boolean getDockingStationUsbPortSwitch();

    boolean setNfcSwitch(boolean flag);
    boolean getNfcSwitch();

    void setUSBRole(int role);
    int getUSBRole();
}
