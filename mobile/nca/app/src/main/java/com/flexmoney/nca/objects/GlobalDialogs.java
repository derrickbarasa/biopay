package com.flexmoney.nca.objects;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;

import com.flexmoney.nca.R;

import java.io.ByteArrayOutputStream;

public class GlobalDialogs {

    public static void success(Context context, String title, String message,
                               DialogInterface.OnClickListener onPositiveClick,DialogInterface.OnClickListener onNegativeClick) {

        AlertDialog.Builder dialog = new AlertDialog.Builder(context,R.style.SuccessAlertStyle);
        dialog.setTitle(title);

        dialog.setIcon(ResourcesCompat.getDrawable(context.getResources(), R.drawable.images,null));
        dialog.setMessage(message);
        dialog.setCancelable(true);
        dialog.setPositiveButton("YES",onPositiveClick);
        dialog.setNegativeButton("NO",onNegativeClick);

        AlertDialog alert = dialog.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
        Button ok = alert.getButton(DialogInterface.BUTTON_POSITIVE);
        ok.setTextColor(Color.WHITE);
        Button cancel = alert.getButton(DialogInterface.BUTTON_NEGATIVE);
        cancel.setTextColor(Color.RED);
    }
    public static void warning(Context context, String title, String message,
                             DialogInterface.OnClickListener onPositiveClick) {

        AlertDialog.Builder dialog = new AlertDialog.Builder(context,R.style.SuccessAlertStyle);
        dialog.setIcon(ResourcesCompat.getDrawable(context.getResources(), R.drawable.images,null));
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setPositiveButton("Ok",onPositiveClick);

        AlertDialog alert = dialog.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
        Button ok = alert.getButton(DialogInterface.BUTTON_POSITIVE);
        ok.setTextColor(Color.WHITE);
    }
    public static void error(Context context, String title, String message,
                               DialogInterface.OnClickListener onPositiveClick) {

        AlertDialog.Builder dialog = new AlertDialog.Builder(context,R.style.ErrorAlertStyle);
        dialog.setIcon(ResourcesCompat.getDrawable(context.getResources(), R.drawable.images,null));
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setPositiveButton("Ok",onPositiveClick);
        AlertDialog alert = dialog.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();

        Button ok = alert.getButton(DialogInterface.BUTTON_POSITIVE);
        ok.setTextColor(Color.WHITE);

    }
    public static String getStringImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 40, baos);
        byte[] b = baos.toByteArray();
        String strImage = Base64.encodeToString(b, Base64.DEFAULT);
        return strImage;
    }

}
