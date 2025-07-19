package com.example.safenow;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String PREFS_NAME = "SafeNowPrefs";
    private static final String KEY_EMERGENCY_NUMBERS = "emergency_numbers";

    private FusedLocationProviderClient fusedLocationClient;
    private EditText emergencyNumbersInput;
    private LinearLayout numberEntryLayout;

    // Comma-separated emergency numbers loaded from SharedPreferences
    private String emergencyNumbers = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        Button emergencyButton = findViewById(R.id.emergencyButton);
        Button saveNumbersButton = findViewById(R.id.saveNumbersButton);
        emergencyNumbersInput = findViewById(R.id.emergencyNumbersInput);
        numberEntryLayout = findViewById(R.id.numberEntryLayout);

        // Load saved emergency numbers from SharedPreferences
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        emergencyNumbers = prefs.getString(KEY_EMERGENCY_NUMBERS, "");

        // If numbers are already saved, hide the number entry layout
        if (!emergencyNumbers.isEmpty()) {
            numberEntryLayout.setVisibility(View.GONE);
        } else {
            numberEntryLayout.setVisibility(View.VISIBLE);
        }

        // Button click listener for saving the emergency numbers
        saveNumbersButton.setOnClickListener(view -> {
            String inputNumbers = emergencyNumbersInput.getText().toString().trim();
            if (!inputNumbers.isEmpty()) {
                emergencyNumbers = inputNumbers;
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putString(KEY_EMERGENCY_NUMBERS, emergencyNumbers);
                editor.apply();
                Toast.makeText(MainActivity.this, "Emergency numbers saved.", Toast.LENGTH_SHORT).show();
                numberEntryLayout.setVisibility(View.GONE);
            } else {
                Toast.makeText(MainActivity.this, "Please enter valid numbers.", Toast.LENGTH_SHORT).show();
            }
        });

        // Button click listener for sending the emergency alert
        emergencyButton.setOnClickListener(v -> {
            if (emergencyNumbers.isEmpty()) {
                Toast.makeText(MainActivity.this, "Please save emergency contact numbers first.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (checkPermissions()) {
                sendEmergencyMessage();
            } else {
                requestPermissions();
            }
        });
    }

    // Check if SMS, ACCESS_FINE_LOCATION, and ACCESS_COARSE_LOCATION permissions are granted
    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    // Request necessary permissions
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Ensure all permissions are granted
            if (grantResults.length > 2 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[2] == PackageManager.PERMISSION_GRANTED) {
                sendEmergencyMessage();
            } else {
                Toast.makeText(this, "Permissions required to send emergency SMS", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Send the emergency SMS after checking for location permissions
    private void sendEmergencyMessage() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permissions not granted", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                String message;
                if (location != null) {
                    message = "I am in emergency! My location: https://maps.google.com/?q="
                            + location.getLatitude() + "," + location.getLongitude();
                } else {
                    message = "I am in emergency! Unable to get location.";
                }
                // Split the comma-separated numbers and send SMS to each
                String[] numbers = emergencyNumbers.split(",");
                for (String number : numbers) {
                    String trimmedNumber = number.trim();
                    if (!trimmedNumber.isEmpty()) {
                        sendSMS(trimmedNumber, message);
                    }
                }
            });
        } catch (SecurityException e) {
            Toast.makeText(MainActivity.this, "Location permission error", Toast.LENGTH_LONG).show();
            Log.e("SafeNow", "Location permission error", e);
        }
    }

    // Send SMS after checking for SMS permission
    private void sendSMS(String phoneNumber, String message) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNumber, null, message, null, null);
            Toast.makeText(this, "Emergency SMS sent to " + phoneNumber, Toast.LENGTH_LONG).show();
        } catch (SecurityException se) {
            Toast.makeText(this, "Security Exception: Unable to send SMS to " + phoneNumber, Toast.LENGTH_LONG).show();
            Log.e("SafeNow", "Security Exception while sending SMS", se);
        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SMS to " + phoneNumber, Toast.LENGTH_LONG).show();
            Log.e("SafeNow", "SMS sending failed", e);
        }
    }
}
