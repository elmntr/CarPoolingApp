package com.example.realtaxi;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class CustomerLoginActivity extends AppCompatActivity {

    private EditText mEmail, mPassword;
    private Button mLogin, mRegistration;

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener firebaseAuthListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_login);

        mAuth = FirebaseAuth.getInstance();

        firebaseAuthListener = firebaseAuth -> {
            FirebaseUser user = mAuth.getCurrentUser();
            if (user != null) {
                String userId = user.getUid();
                FirebaseFirestore db = FirebaseFirestore.getInstance();

                // Check role before allowing access to the CustomerMapActivity
                db.collection("users").document(userId)
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            if (snapshot.exists() && "customer".equals(snapshot.getString("role"))) {
                                Intent intent = new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
                                startActivity(intent);
                                finish();
                            } else {
                                Toast.makeText(CustomerLoginActivity.this, "Unauthorized access.", Toast.LENGTH_SHORT).show();
                                mAuth.signOut(); // Log out if the user is not a customer
                            }
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(CustomerLoginActivity.this, "Failed to verify user role.", Toast.LENGTH_SHORT).show();
                        });
            }
        };

        mEmail = findViewById(R.id.email);
        mPassword = findViewById(R.id.password);
        mLogin = findViewById(R.id.btnLogin);
        mRegistration = findViewById(R.id.btnregistration);

        mRegistration.setOnClickListener(view -> {
            String email = mEmail.getText().toString().trim();
            String password = mPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(CustomerLoginActivity.this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(CustomerLoginActivity.this, task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(CustomerLoginActivity.this, "Sign up error.", Toast.LENGTH_SHORT).show();
                        } else {
                            String userId = mAuth.getCurrentUser().getUid();
                            FirebaseFirestore db = FirebaseFirestore.getInstance();

                            // Store the user's role as "customer"
                            Map<String, Object> userFields = new HashMap<>();
                            userFields.put("role", "customer");

                            db.collection("users").document(userId)
                                    .set(userFields)
                                    .addOnSuccessListener(aVoid -> {
                                        Toast.makeText(CustomerLoginActivity.this, "Customer account created successfully.", Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(CustomerLoginActivity.this, CustomerMapActivity.class);
                                        startActivity(intent);
                                        finish();
                                    })
                                    .addOnFailureListener(e -> {
                                        Toast.makeText(CustomerLoginActivity.this, "Firestore error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                    });
                        }
                    });
        });

        mLogin.setOnClickListener(view -> {
            String email = mEmail.getText().toString().trim();
            String password = mPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(CustomerLoginActivity.this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(CustomerLoginActivity.this, task -> {
                        if (!task.isSuccessful()) {
                            Toast.makeText(CustomerLoginActivity.this, "Sign in error.", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAuth.addAuthStateListener(firebaseAuthListener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (firebaseAuthListener != null) {
            mAuth.removeAuthStateListener(firebaseAuthListener);
        }
    }
}
