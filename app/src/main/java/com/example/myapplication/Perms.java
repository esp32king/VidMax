package com.example.myapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Storage permissions that differ by Android version. */
public final class Perms {

    private Perms() {
    }

    public static String[] storage() {
        List<String> l = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {   // 33+
            l.add(Manifest.permission.READ_MEDIA_VIDEO);
            l.add(Manifest.permission.READ_MEDIA_AUDIO);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {   // 29-32
            l.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        } else {                                                       // <=28
            l.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            l.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return l.toArray(new String[0]);
    }

    public static boolean has(Context c, String[] p) {
        for (String s : p) {
            if (ContextCompat.checkSelfPermission(c, s) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }
}
