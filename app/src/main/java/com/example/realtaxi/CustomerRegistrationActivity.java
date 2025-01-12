package com.example.realtaxi;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CustomerRegistrationActivity extends AppCompatActivity {
    private EditText mEmail, mPassword, mFullName, mPhoneNumber, mAddress;
    private Button mRegisterButton;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_registration);

        mAuth = FirebaseAuth.getInstance();

        // Initialize fields
        mEmail = findViewById(R.id.email);
        mPassword = findViewById(R.id.password);
        mFullName = findViewById(R.id.fullName);
        mPhoneNumber = findViewById(R.id.phoneNumber);
        mAddress = findViewById(R.id.address);
        mRegisterButton = findViewById(R.id.btnRegister);

        mRegisterButton.setOnClickListener(view -> registerCustomer());
    }

    private void registerCustomer() {
        String email = mEmail.getText().toString().trim();
        String password = mPassword.getText().toString().trim();
        String fullName = mFullName.getText().toString().trim();
        String phoneNumber = mPhoneNumber.getText().toString().trim();
        String address = mAddress.getText().toString().trim();

        // Validate fields
        if (email.isEmpty() || password.isEmpty() || fullName.isEmpty() || phoneNumber.isEmpty() || address.isEmpty()) {
            Toast.makeText(CustomerRegistrationActivity.this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Register user in Firebase Authentication
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(CustomerRegistrationActivity.this, "Sign up error.", Toast.LENGTH_SHORT).show();
                    } else {
                        String userId = mAuth.getCurrentUser().getUid();
                        FirebaseFirestore db = FirebaseFirestore.getInstance();

                        // Prepare user data
                        Map<String, Object> userFields = new HashMap<>();
                        userFields.put("role", "customer");
                        userFields.put("CSName", fullName);
                        userFields.put("phoneNumber", phoneNumber);
                        userFields.put("address", address);

                        // Save user data in Firestore
                        db.collection("users").document(userId)
                                .set(userFields)
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(CustomerRegistrationActivity.this, "Customer account created successfully.", Toast.LENGTH_SHORT).show();
                                    Intent intent = new Intent(CustomerRegistrationActivity.this, CustomerMapActivity.class);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> Toast.makeText(CustomerRegistrationActivity.this, "Firestore error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                });
    }
}
