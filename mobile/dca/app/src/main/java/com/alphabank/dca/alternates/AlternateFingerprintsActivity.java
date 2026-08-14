package com.alphabank.dca.alternates;

import android.app.Service;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.alphabank.dca.CbmProcessObserver;
import com.alphabank.dca.R;
import com.alphabank.dca.data.BiometricsContract;
import com.alphabank.dca.home.MainActivity;
import com.alphabank.dca.households.PersonalInfoActivity;
import com.alphabank.dca.objects.GlobalApplication;
import com.alphabank.dca.objects.SessionManager;
import com.idemia.peripherals.PeripheralsPowerInterface;
import com.morpho.android.usb.USBManager;
import com.morpho.morphosmart.sdk.CallbackMask;
import com.morpho.morphosmart.sdk.Coder;
import com.morpho.morphosmart.sdk.CompressionAlgorithm;
import com.morpho.morphosmart.sdk.CustomInteger;
import com.morpho.morphosmart.sdk.DetectionMode;
import com.morpho.morphosmart.sdk.EnrollmentType;
import com.morpho.morphosmart.sdk.ErrorCodes;
import com.morpho.morphosmart.sdk.LatentDetection;
import com.morpho.morphosmart.sdk.MorphoDevice;
import com.morpho.morphosmart.sdk.Template;
import com.morpho.morphosmart.sdk.TemplateFVPType;
import com.morpho.morphosmart.sdk.TemplateList;
import com.morpho.morphosmart.sdk.TemplateType;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.UUID;

public class AlternateFingerprintsActivity extends AppCompatActivity {
    private String TAG = "AlternateFingerprintsActivity";
    Button btnAlternateRightThumb, btnAlternateRightIndex, btnAlternateRightMiddle, btnAlternateRightRing,
            btnAlternateRightLittle, btnAlternateLeftThumb, btnAlternateLeftIndex, btnAlternateLeftMiddle,
            btnAlternateLeftRing, btnAlternateLeftLittle, btnAlternateSave;

    ImageView ivAlternateFingerprint;
    GlobalApplication globalApplication;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    int fingerCode;
    SessionManager sessionManager;

    //fpSensor
    private View rootView;
    private MorphoDevice morphoDevice;
    CbmProcessObserver processObserver;
    private boolean capturing = false;
    private boolean deviceIsSet = false;

    private PeripheralsPowerInterface mPeripheralsInterface;
    private ServiceConnection serviceConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mPeripheralsInterface = PeripheralsPowerInterface.Stub.asInterface(service);
            Log.d(TAG, "aidl connect success");

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
        setContentView(R.layout.activity_alternate_fingerprints);

        sessionManager = new SessionManager(getApplicationContext());
        globalApplication = GlobalApplication.getInstance();

        rootView = findViewById(android.R.id.content).getRootView();
        ivAlternateFingerprint = findViewById(R.id.ivAlternateFingerprint);

        btnAlternateRightThumb = findViewById(R.id.btnAlternateRightThumb);
        btnAlternateRightIndex = findViewById(R.id.btnAlternateRightIndex);
        btnAlternateRightMiddle = findViewById(R.id.btnAlternateRightMiddle);
        btnAlternateRightRing = findViewById(R.id.btnAlternateRightRing);
        btnAlternateRightLittle = findViewById(R.id.btnAlternateRightLittle);
        btnAlternateLeftThumb = findViewById(R.id.btnAlternateLeftThumb);
        btnAlternateLeftIndex = findViewById(R.id.btnAlternateLeftIndex);
        btnAlternateLeftMiddle = findViewById(R.id.btnAlternateLeftMiddle);
        btnAlternateLeftRing = findViewById(R.id.btnAlternateLeftRing);
        btnAlternateLeftLittle = findViewById(R.id.btnAlternateLeftLittle);
        btnAlternateSave = findViewById(R.id.btnAlternateSave);


        capturing = false;
        deviceIsSet = false;

        // Initialize the Observer object for the capture's callback
        processObserver = new CbmProcessObserver(rootView, AlternateFingerprintsActivity.this);

        populateFingerprints();

        btnAlternateSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int enrolledTemplates = populateFingerprints();
                AlertDialog.Builder dialog = new AlertDialog.Builder(AlternateFingerprintsActivity.this);
                dialog.setTitle("Fingerprints Enrollment");
                dialog.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.images, null));
                dialog.setMessage("Fingerprints enrolled are " + enrolledTemplates + ". Do you want to proceed");

                dialog.setPositiveButton("Register Another Alternate", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        globalApplication.setAlternateName(null);
                        globalApplication.setAlternateNumber(null);
                        startActivity(new Intent(AlternateFingerprintsActivity.this, AlternatesActivity.class));
                    }
                });
                dialog.setNeutralButton("Home", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        globalApplication.setAlternateName(null);
                        globalApplication.setAlternateNumber(null);

                        globalApplication.setHouseholdNumber(null);
                        globalApplication.setHouseholdName(null);
                        startActivity(new Intent(AlternateFingerprintsActivity.this, MainActivity.class));
                    }
                });
                dialog.setNegativeButton("Household Registration", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        globalApplication.setAlternateName(null);
                        globalApplication.setAlternateNumber(null);

                        globalApplication.setHouseholdNumber(null);
                        globalApplication.setHouseholdName(null);
                        startActivity(new Intent(AlternateFingerprintsActivity.this, PersonalInfoActivity.class));

                    }
                });

                AlertDialog alert = dialog.create();
                alert.setCanceledOnTouchOutside(false);
                alert.show();
            }
        });
    }

    public int populateFingerprints() {
        Log.e("Alt Fp No.", globalApplication.getAlternateNumber());
        Cursor cursor = null;
        int count = 0;
        String whereAlternate = BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID + "=?";
        String whereAlternateArgs[] = {globalApplication.getAlternateNumber()};
        try {
            cursor = getContentResolver().query(BiometricsContract.FingerprintEntry.CONTENT_URI, null, whereAlternate, whereAlternateArgs, null);
            if (cursor != null) {
                count = cursor.getCount();
                if (count != 0) {
                    if (cursor.moveToFirst()) {
                        do {
                            int fNo = cursor.getInt(cursor.getColumnIndexOrThrow(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER));
                            updateUI(fNo);
                        } while (cursor.moveToNext());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return count;
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

    /**************************** CAPTURE *********************************/
    public void morphoDeviceCapture() {

        if (morphoDevice == null) {
            morphoDevice = initMorphoDevice();
            deviceIsSet = true;
        }

        /********* CAPTURE THREAD *************/
        Thread commandThread = new Thread(new Runnable() {
            @Override
            public void run() {

                int ret = 0;
                int timeout = 30;
                final int acquisitionThreshold = 0;
                int advancedSecurityLevelsRequired = 0;
                int fingerNumber = 1;
                TemplateType templateType = TemplateType.MORPHO_PK_ISO_FMR_2011;
                TemplateFVPType templateFVPType = TemplateFVPType.MORPHO_NO_PK_FVP;
                int maxSizeTemplate = 512;
                EnrollmentType enrollType = EnrollmentType.ONE_ACQUISITIONS;
                LatentDetection latentDetection = LatentDetection.LATENT_DETECT_ENABLE;
                Coder coderChoice = Coder.MORPHO_DEFAULT_CODER;
                int detectModeChoice = DetectionMode.MORPHO_ENROLL_DETECT_MODE.getValue()
                        | DetectionMode.MORPHO_FORCE_FINGER_ON_TOP_DETECT_MODE.getValue();//18;
                TemplateList templateList = new TemplateList();

                // Define the messages sent through the callback
                int callbackCmd = CallbackMask.MORPHO_CALLBACK_COMMAND_CMD.getValue()
                        | CallbackMask.MORPHO_CALLBACK_IMAGE_CMD.getValue()
                        | CallbackMask.MORPHO_CALLBACK_CODEQUALITY.getValue()
                        | CallbackMask.MORPHO_CALLBACK_DETECTQUALITY.getValue();

                /********* CAPTURE *************/
                ret = morphoDevice.capture(timeout, acquisitionThreshold, advancedSecurityLevelsRequired,
                        fingerNumber, templateType, templateFVPType, maxSizeTemplate, enrollType,
                        latentDetection, coderChoice, detectModeChoice,
                        CompressionAlgorithm.MORPHO_NO_COMPRESS, 0, templateList,
                        callbackCmd, processObserver);

                Log.d(TAG, "morphoDeviceCapture ret = " + ret);
                if (ret != ErrorCodes.MORPHO_OK) {
                    String err = "";
                    if (ret == ErrorCodes.MORPHOERR_TIMEOUT) {
                        err = "Capture failed : timeout";
                    } else if (ret == ErrorCodes.MORPHOERR_CMDE_ABORTED) {
                        err = "Capture aborted";
                    } else if (ret == ErrorCodes.MORPHOERR_UNAVAILABLE) {
                        err = "Device is not available";
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
                    // Here fingerNumber = 1, so we will get only one template
                    int nbTemplate = templateList.getNbTemplate();
                    Log.d(TAG, "morphoDeviceCapture nbTemplate = " + nbTemplate);

                    String msg = "";

                    if (nbTemplate == 1) {

                        msg += "Template successfully captured!\n\nKindly save the template and capture the next fingerprint\n";

                        final String alertMessage = msg;
                        final Template t = templateList.getTemplate(0);

                        // Dialog window to save the template
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(rootView.getContext());
                                builder.setTitle(R.string.app_name);
                                builder.setCancelable(false);
                                builder.setMessage(alertMessage);
                                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        try {

                                            ContentValues value = new ContentValues();
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_SUPERVISOR_ID, sessionManager.getUserId());
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_TYPE, "1");
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_BENEFICIARY_ID, globalApplication.getAlternateNumber());
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT_NUMBER, fingerCode);
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UUID, UUID.randomUUID().toString().trim());
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_FINGERPRINT, Base64.encodeToString(t.getData(), Base64.NO_WRAP));
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_STATUS, "0");
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_CREATED_AT, globalApplication.todaysDate());
                                            value.put(BiometricsContract.FingerprintEntry.COLUMN_NAME_UPDATED_AT, globalApplication.todaysDate());

                                            getContentResolver().insert(BiometricsContract.FingerprintEntry.CONTENT_URI, value);
                                            updateUI(fingerCode);

                                        } catch (Exception e) {
                                            Log.e(TAG, "Exception : " + e.getMessage());
                                        }
                                    }
                                });
                                builder.show();
                            }
                        });

                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        capturing = false;
                        rootView.setKeepScreenOn(false);
                    }
                });
            }
        });
        commandThread.start();
    }

    public void btnAlternateEnrolFinger(View view) {

        int enrolId = view.getId();
       // switch (enrolId) {
            if (enrolId == R.id.btnAlternateRightThumb) {
                fingerCode = 1;
            } else if (enrolId ==R.id.btnAlternateRightIndex) {
                fingerCode = 2;
                // break;
            } else if (enrolId ==R.id.btnAlternateRightMiddle) {
                fingerCode = 3;
                // break;
            } else if (enrolId ==R.id.btnAlternateRightRing) {
                fingerCode = 4;
                // break;
            } else if (enrolId ==R.id.btnAlternateRightLittle) {
                fingerCode = 5;
                // break;
            } else if (enrolId ==R.id.btnAlternateLeftThumb) {
                fingerCode = 6;
                //break;
            } else if (enrolId ==R.id.btnAlternateLeftIndex) {
                fingerCode = 7;
                //break;
            } else if (enrolId ==R.id.btnAlternateLeftMiddle) {
                fingerCode = 8;
                //break;
            } else if (enrolId ==R.id.btnAlternateLeftRing) {
                fingerCode = 9;
                //break;
            } else if (enrolId ==R.id.btnAlternateLeftLittle) {
                fingerCode = 10;
                //break;
            } else {
                //default:
                //break;
            }
            if (!capturing) {
                capturing = true;
                rootView.setKeepScreenOn(true);

                morphoDeviceCapture();
            } else if (capturing && deviceIsSet) {
                rootView.setKeepScreenOn(false);
                morphoDevice = closeMorphoDevice(morphoDevice);
                capturing = false;
                deviceIsSet = false;
            } else {
                showToastMessage("Device is being initialized, please try again", Toast.LENGTH_SHORT);
            }
        }


        private void updateUI ( int fingerNumber){
            switch (fingerNumber) {
                case 1:
                    btnAlternateRightThumb.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateRightThumb.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 2:
                    btnAlternateRightIndex.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateRightIndex.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 3:
                    btnAlternateRightMiddle.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateRightMiddle.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 4:
                    btnAlternateRightRing.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateRightRing.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 5:
                    btnAlternateRightLittle.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateRightLittle.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 6:
                    btnAlternateLeftThumb.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateLeftThumb.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 7:
                    btnAlternateLeftIndex.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateLeftIndex.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 8:
                    btnAlternateLeftMiddle.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateLeftMiddle.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 9:
                    btnAlternateLeftRing.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateLeftRing.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                case 10:
                    btnAlternateLeftLittle.setCompoundDrawablesWithIntrinsicBounds(null,
                            getResources().getDrawable(R.drawable.finger_success), null, null);
                    btnAlternateLeftLittle.setTextColor(ContextCompat.getColor(AlternateFingerprintsActivity.this, R.color.black));
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onBackPressed () {
            super.onBackPressed();
            startActivity(new Intent(AlternateFingerprintsActivity.this, AlternatesActivity.class));
        }

        private void showToastMessage (String msg,int length){
            Toast toast = Toast.makeText(getApplication(), msg, length);
            toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 180);
            toast.show();
        }

        public boolean getFingerprintSensorState () {
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

        private boolean isDevicePluggedToPc () {
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
        protected void onPause () {
            super.onPause();
            unbindService(serviceConn);

            morphoDevice = closeMorphoDevice(morphoDevice);
            deviceIsSet = false;
        }

        @Override
        protected void onResume () {
            if (!bindService(getAidlIntent(), serviceConn, Service.BIND_AUTO_CREATE)) {
                Log.e(TAG, "System couldn't find the service");
                Toast.makeText(getApplicationContext(), "System couldn't find peripherals service", Toast.LENGTH_SHORT).show();
            }
            super.onResume();
        }

        private Intent getAidlIntent () {
            Intent aidlIntent = new Intent();
            aidlIntent.setAction("idemia.intent.action.CONN_PERIPHERALS_SERVICE_AIDL");
            aidlIntent.setPackage("com.android.settings");
            return aidlIntent;
        }
    }