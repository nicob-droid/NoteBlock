package com.example.noteblock;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private EditText emailEditText, passwordEditText;
    private Button btnSignIn, btnSignUp, btnLogout;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        btnSignIn = findViewById(R.id.btnSignIn);
        btnSignUp = findViewById(R.id.btnSignUp);
        btnLogout = findViewById(R.id.btnLogout);

        btnSignIn.setOnClickListener(v -> signIn());
        btnSignUp.setOnClickListener(v -> signUp());
        btnLogout.setOnClickListener(v -> logOut());

        // Si déjà connecté
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            //goToMainScreen();
            // L'utilisateur est connecté
            btnLogout.setVisibility(View.VISIBLE);
            btnSignIn.setVisibility(View.GONE);
            btnSignUp.setVisibility(View.GONE);
        }
    }

    private void signIn() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError(getString(R.string.email_required));
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordEditText.setError(getString(R.string.password_required));
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, getString(R.string.connect_success), Toast.LENGTH_SHORT).show();
                        goToMainScreen();
                    } else {
                        Toast.makeText(LoginActivity.this, getString(R.string.connect_error) + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void signUp() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();

        if (TextUtils.isEmpty(email)) {
            emailEditText.setError(getString(R.string.email_required));
            return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            passwordEditText.setError(getString(R.string.password_required_minimum));
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, getString(R.string.register_success), Toast.LENGTH_SHORT).show();
                        goToMainScreen();
                    } else {
                        Toast.makeText(LoginActivity.this, getString(R.string.register_error) + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void logOut() {
        auth.signOut();
        if (auth.getCurrentUser() == null) {
            // Déconnexion réussie
            Toast.makeText(LoginActivity.this, getString(R.string.disconnect_success), Toast.LENGTH_SHORT).show();
            // Recharge l'activité
            Intent intent = new Intent(this, LoginActivity.class); // remplace si besoin
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } else {
            // Problème lors de la déconnexion
            Toast.makeText(LoginActivity.this, getString(R.string.disconnect_error), Toast.LENGTH_LONG).show();
        }

    }

    private void goToMainScreen() {
        // Ici tu lances ton activité principale (NotesActivity par exemple)
        Intent intent = new Intent(this, NotesActivity.class);
        startActivity(intent);
        finish();
    }


}
