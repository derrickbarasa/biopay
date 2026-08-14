package com.alphabank.dca.payments;

import static com.morpho.morphosmart.sdk.FalseAcceptanceRate.MORPHO_FAR_5;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.alphabank.dca.CbmProcessObserver;
import com.alphabank.dca.R;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.objects.GlobalDialogs;
import com.alphabank.dca.objects.SessionManager;
import com.idemia.peripherals.PeripheralsPowerInterface;
import com.morpho.android.usb.USBManager;
import com.morpho.morphosmart.sdk.CallbackMask;
import com.morpho.morphosmart.sdk.Coder;
import com.morpho.morphosmart.sdk.CustomInteger;
import com.morpho.morphosmart.sdk.DetectionMode;
import com.morpho.morphosmart.sdk.ErrorCodes;
import com.morpho.morphosmart.sdk.MatchingStrategy;
import com.morpho.morphosmart.sdk.MorphoDevice;
import com.morpho.morphosmart.sdk.ResultMatching;
import com.morpho.morphosmart.sdk.Template;
import com.morpho.morphosmart.sdk.TemplateList;
import com.morpho.morphosmart.sdk.TemplateType;
import static com.morpho.morphosmart.sdk.Coder.MORPHO_MSO_V9_CODER;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PaymentVerificationActivity extends AppCompatActivity {
    private String TAG = "PaymentVerificationActivity";

    private ImageView ivPaymentVerification;
    private Button btnPaymentVerification;
    private TextView tvPaymentVerification;
    GlobalApplication globalApplication;
    SessionManager sessionManager;
    List<String> listFingerPrints;
    public String templateUsed;

    //fpsensor
    private View rootView;
    private MorphoDevice morphoDevice;
    CbmProcessObserver processObserver;

    private boolean verifying = false;
    private boolean deviceIsSet = false;

    private PeripheralsPowerInterface mPeripheralsInterface;
    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mPeripheralsInterface = PeripheralsPowerInterface.Stub.asInterface(service);
            Log.d(TAG, "aidl connect succes");

            if (!getFingerprintSensorState()) {
                android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(rootView.getContext()).create();
                alertDialog.setCancelable(false);
                alertDialog.setTitle(R.string.app_name);
                alertDialog.setMessage(getString(R.string.noAccessToDevice));
                alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Ok", (DialogInterface.OnClickListener) null);
                alertDialog.show();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mPeripheralsInterface = null;
            Log.d(TAG, "aidl disconnected");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_payment_verification);

        rootView = findViewById(android.R.id.content).getRootView();
        sessionManager = new SessionManager(getApplicationContext());
        globalApplication = GlobalApplication.getInstance();
        listFingerPrints = new ArrayList<String>();

        ivPaymentVerification = findViewById(R.id.ivPaymentVerification);
        tvPaymentVerification = findViewById(R.id.tvPaymentVerification);

        deviceIsSet = false;

        // Initialize the Observer object for the capture's callback
        processObserver = new CbmProcessObserver(rootView, PaymentVerificationActivity.this);

        btnPaymentVerification = findViewById(R.id.btnPaymentVerification);
        btnPaymentVerification.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!verifying) {

                    verifying = true;
                    rootView.setKeepScreenOn(true);

                    getBeneficiaryFingerprints();
                } else {
                    morphoDevice = closeMorphoDevice(morphoDevice);
                    verifying = false;
                    deviceIsSet = false;
                    //Toast.makeText(getApplicationContext(), "Please wait until the end of process",Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Close the USB device
    public MorphoDevice closeMorphoDevice(MorphoDevice md) {
        if (md != null) {
            Log.d(TAG, "closeMorphoDevice");
            try {
                md.cancelLiveAcquisition();
                md.closeDevice();
                md = null;

            } catch (Exception e) {
                Log.e(TAG, "closeMorphoDevice : " + e.getMessage());
            }
        }
        return md;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        unbindService(serviceConn);

        morphoDevice = closeMorphoDevice(morphoDevice);
        deviceIsSet = false;
        startActivity(new Intent(PaymentVerificationActivity.this, PaymentHouseholdsActivity.class));
    }

    @SuppressLint("Range")
    private void getBeneficiaryFingerprints() {
        listFingerPrints.clear();
        String where = BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID + "= ?";
        String whereArgs[] = {globalApplication.getAlternateNumber()};

        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, where, whereArgs, null);
            if (cursor != null) {
                if (cursor.getCount() != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            String fingerprintByte = cursor.getString(cursor.getColumnIndexOrThrow(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT));
                            listFingerPrints.add(fingerprintByte);
                        } while (cursor.moveToNext());
                    }
                    verifyFingerprint();
                } else {
                    verifying = false;
                    rootView.setKeepScreenOn(false);
                    GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                            "No fingerprints found. Please select another beneficiary",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(PaymentVerificationActivity.this, PaymentBeneficiariesActivity.class));
                                }
                            });
                }
            } else {
                verifying = false;
                rootView.setKeepScreenOn(false);
                GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                        "No fingerprints found. Please select another beneficiary",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startActivity(new Intent(PaymentVerificationActivity.this, PaymentBeneficiariesActivity.class));
                            }
                        });
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void verifyFingerprint() {
        byte[] retByte = null;

        if (listFingerPrints.isEmpty()) {
            GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                    "Fingerprints Verification failed. All stored fingerprints checked. This might not be the actual beneficiary. Choose an alternate!!",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            startActivity(new Intent(PaymentVerificationActivity.this, PaymentBeneficiariesActivity.class));
                        }
                    });

        } else {
            retByte = (listFingerPrints.get(0).trim() == null) ? null : Base64.decode(listFingerPrints.get(0).trim(), Base64.NO_WRAP);
            templateUsed = null;
            templateUsed = listFingerPrints.get(0).trim();
            listFingerPrints.remove(0);
            if (retByte != null) {
                tvPaymentVerification.setText("Place your finger on the sensor");
                Toast.makeText(PaymentVerificationActivity.this, "Place your finger on the sensor", Toast.LENGTH_SHORT).show();
                morphoDeviceVerify(retByte);
            } else {
                GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                        "Fingerprints Verification failed. All stored fingerprints checked. This might not be the actual beneficiary. Choose an alternate!!",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                startActivity(new Intent(PaymentVerificationActivity.this, PaymentBeneficiariesActivity.class));
                            }
                        });
            }
        }
    }

    // Initialize and open USB devise
    public MorphoDevice initMorphoDevice() {
        int ret = 0;
        Log.d(TAG, "initMorphoDevice");

        // On Morphotablet, 3rd parameter (enableWakeLock) must always be true
        USBManager.getInstance().initialize(this, "com.morpho.morphosample.USB_ACTION", true);
        MorphoDevice md = new MorphoDevice();
        CustomInteger nbUsbDevice = new CustomInteger();
        ret = md.initUsbDevicesNameEnum(nbUsbDevice);
        if (ret == ErrorCodes.MORPHO_OK) {
            if (nbUsbDevice.getValueOf() != 1) {

                this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        android.app.AlertDialog alertDialog = new android.app.AlertDialog.Builder(rootView.getContext()).create();
                        alertDialog.setCancelable(false);
                        alertDialog.setTitle(R.string.app_name);
                        alertDialog.setMessage(getString(R.string.noAccessToDevice));
                        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                finish();
                            }
                        });
                        alertDialog.show();
                    }
                });

            } else {
                String sensorName = md.getUsbDeviceName(0); // We use first CBM found
                ret = md.openUsbDevice(sensorName, 0);
                if (ret != ErrorCodes.MORPHO_OK) {
                    this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showToastMessage("Error opening USB device", Toast.LENGTH_SHORT);
                        }
                    });

                    finish();
                }
            }
        } else {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    showToastMessage("Error initializing USB device", Toast.LENGTH_SHORT);
                }
            });
            finish();
        }

        return md;
    }

    /**************************** VERIFY **********************************/
    public void morphoDeviceVerify(byte[] buffer) {

        if (morphoDevice == null) {
            morphoDevice = initMorphoDevice();
            deviceIsSet = true;
        }

        /********* VERIFY THREAD *************/
        Thread commandThread = new Thread(new Runnable() {
            @Override
            public void run() {

                String match = "";
                int ret = 0;
                int timeOut = 30;
                int far = MORPHO_FAR_5;
                Coder coder = MORPHO_MSO_V9_CODER;
                int detectModeChoice = DetectionMode.MORPHO_ENROLL_DETECT_MODE.getValue()
                        | DetectionMode.MORPHO_FORCE_FINGER_ON_TOP_DETECT_MODE.getValue();
                int matchingStrategy = MatchingStrategy.MORPHO_STANDARD_MATCHING_STRATEGY.getValue();
                final TemplateList templateList = new TemplateList();

                int callbackCmd = CallbackMask.MORPHO_CALLBACK_IMAGE_CMD.getValue()
                        | CallbackMask.MORPHO_CALLBACK_COMMAND_CMD.getValue();
                final ResultMatching resultMatching = new ResultMatching();

                // Read the template file provided by the user
                if (buffer != null) {
                    Template template = new Template();
                    template.setData(buffer);
                    template.setTemplateType(TemplateType.MORPHO_PK_ISO_FMR_2011);
                    templateList.putTemplate(template);

                    /********* VERIFY *************/
                    ret = morphoDevice.verify(timeOut, far, coder, detectModeChoice, matchingStrategy,
                            templateList, callbackCmd, processObserver, resultMatching);

                    Log.d(TAG, "morphoDeviceVerify ret = " + ret);
                    if (ret != ErrorCodes.MORPHO_OK) {
                        String err = "";
                        if (ret == ErrorCodes.MORPHOERR_TIMEOUT) {
                            err = "Verify process failed : timeout";
                        } else if (ret == ErrorCodes.MORPHOERR_CMDE_ABORTED) {
                            err = "Verify process aborted";
                        } else if (ret == ErrorCodes.MORPHOERR_UNAVAILABLE) {
                            err = "Device is not available";
                        } else if (ret == ErrorCodes.MORPHOERR_INVALID_FINGER || ret == ErrorCodes.MORPHOERR_NO_HIT) {
                            err = "Authentication or Identification failed";
                            match = "Template doesn't match";
                        } else {
                            err = "Error code is " + ret;
                        }
                        final String finalErr = err;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showToastMessage(finalErr, Toast.LENGTH_SHORT);
                            }
                        });

                    } else {
                        if (resultMatching != null) {
                            //it matched. Verification is a success
                            match = "Matching score: " + resultMatching.getMatchingScore();
                        }
                    }
                } else {
                    // Wrong file/template
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showToastMessage("Incorrect template", Toast.LENGTH_SHORT);
                            Log.d(TAG, "morphoDeviceVerify : incorrect template");
                        }
                    });

                }

                final String finalMatch = match;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        verifying = false;
                        rootView.setKeepScreenOn(false);
                        tvPaymentVerification.setText(finalMatch);

                        if (finalMatch.equals("Template doesn't match")) {
                            verifyFingerprint();
                        } else if (finalMatch.contains("Matching score: ")) {
                            updatePayments();
                        } else {
                            verifyFingerprint();
                        }

                    }
                });
            }
        });
        commandThread.start();
    }

    private void updatePayments() {
        if (globalApplication.getHouseholdNumber() != null) {

            ContentValues valuePayment = new ContentValues();
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_STATUS, "1");
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SYNC, "0");
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_BENEFICIARY_TYPE, globalApplication.getLevel());
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_MATCHED_FP, templateUsed);
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_HOUSEHOLD_NUMBER, globalApplication.getHouseholdNumber());
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAID_TO, globalApplication.getAlternateNumber());
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_UUID, UUID.randomUUID().toString());
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_PAYMENT_CENTRE, globalApplication.getPaymentCentre());
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_LATITUDE, globalApplication.getLatitude());
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_LONGITUDE, globalApplication.getLongitude());
            valuePayment.put(BiometricsContract.PaymentsEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

            Uri newUri = getContentResolver().insert(BiometricsContract.PaymentsEntry.CONTENT_URI, valuePayment);

                GlobalDialogs.warning(PaymentVerificationActivity.this, "Payment Verification",
                        "Payment SUCCESSFULLY saved, please choose another household!",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                startActivity(new Intent(PaymentVerificationActivity.this, PaymentHouseholdsActivity.class));
                            }
                        });

        } else {
            GlobalDialogs.error(PaymentVerificationActivity.this, "Payment Verification",
                    "System could not find the Payment Batch (cycle). DO NOT PAY the beneficiary. Please direct the beneficiary to the relevant authorities!",
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                            startActivity(new Intent(PaymentVerificationActivity.this, PaymentHouseholdsActivity.class));
                        }
                    });
        }
    }

    private void showToastMessage(String msg, int length) {
        Toast toast = Toast.makeText(getApplication(), msg, length);
        toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 180);
        toast.show();
    }

    public boolean getFingerprintSensorState() {
        boolean ret = false;
        int usbRole = -1;

        try {
            if (mPeripheralsInterface != null) {
                ret = mPeripheralsInterface.getFingerPrintSwitch();
                if (!ret) {
                    return false;
                }

                usbRole = mPeripheralsInterface.getUSBRole();
                if (usbRole == 2) { // DEVICE mode: PC connection only
                    return false;
                } else if (usbRole == 1) { // HOST mode: Peripherals only
                    return true;
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        // Here, fingerprint sensor should be powered on, and USB role set to AUTO
        // Check if tablet is plugged to the computer
        if (!isDevicePluggedToPc()) {
            return true;
        }

        return false;
    }

    private boolean isDevicePluggedToPc() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = getApplicationContext().registerReceiver(null, ifilter);

        // Are we charging / charged?
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);

        if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
            // How are we charging?
            int chargePlug = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            if (chargePlug == BatteryManager.BATTERY_PLUGGED_USB) {
                Log.d(TAG, "USB plugged");
                return true;
            }
            if (chargePlug == BatteryManager.BATTERY_PLUGGED_AC) {
                Log.d(TAG, "Powered by 3.5mm connector");
                return false;
            }
        }

        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        unbindService(serviceConn);

        morphoDevice = closeMorphoDevice(morphoDevice);
        deviceIsSet = false;
    }

    @Override
    protected void onResume() {
        if (!bindService(getAidlIntent(), serviceConn, Service.BIND_AUTO_CREATE)) {
            Log.e(TAG, "System couldn't find the service");
            Toast.makeText(getApplicationContext(), "System couldn't find peripherals service", Toast.LENGTH_SHORT).show();
        }
        super.onResume();
    }

    private Intent getAidlIntent() {
        Intent aidlIntent = new Intent();
        aidlIntent.setAction("idemia.intent.action.CONN_PERIPHERALS_SERVICE_AIDL");
        aidlIntent.setPackage("com.android.settings");
        return aidlIntent;
    }
}