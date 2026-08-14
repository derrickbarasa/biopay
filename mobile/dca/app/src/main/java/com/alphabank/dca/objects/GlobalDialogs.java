package com.alphabank.dca.objects;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.util.Base64;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;

import com.alphabank.dca.R;

import java.io.ByteArrayOutputStream;

public class GlobalDialogs {

    public static void success(Context context, String title, String message,
                               DialogInterface.OnClickListener onPositiveClick,DialogInterface.OnClickListener onNegativeClick) {

        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setTitle(title);

        dialog.setIcon(ResourcesCompat.getDrawable(context.getResources(), R.drawable.images,null));
        dialog.setMessage(message);
        dialog.setCancelable(true);
        dialog.setPositiveButton("Ok",onPositiveClick);
        dialog.setNegativeButton("Cancel",onNegativeClick);

        AlertDialog alert = dialog.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
        alert.getWindow().setBackgroundDrawableResource(R.color.teal_700);
    }
    public static void warning(Context context, String title, String message,
                             DialogInterface.OnClickListener onPositiveClick) {

        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setIcon(ResourcesCompat.getDrawable(context.getResources(), R.drawable.images,null));
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setPositiveButton("Ok",onPositiveClick);
        AlertDialog alert = dialog.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
        alert.getWindow().setBackgroundDrawableResource(R.color.darkGreen);
    }
    public static void error(Context context, String title, String message,
                               DialogInterface.OnClickListener onPositiveClick) {

        AlertDialog.Builder dialog = new AlertDialog.Builder(context);
        dialog.setIcon(ResourcesCompat.getDrawable(context.getResources(), R.drawable.images,null));
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setPositiveButton("Ok",onPositiveClick);
        AlertDialog alert = dialog.create();
        alert.setCanceledOnTouchOutside(false);
        alert.show();
        alert.getWindow().setBackgroundDrawableResource(R.color.darkRed);
    }
    public static String getStringImage(Bitmap bitmap) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 40, baos);
        byte[] b = baos.toByteArray();
        String strImage = Base64.encodeToString(b, Base64.DEFAULT);
        return strImage;
    }

}
