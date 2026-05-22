package com.example.cosmonote.Settings;


import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.cosmonote.app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Objects;

public class LoginDialogFragment extends DialogFragment {

    private FirebaseAuth mAuth;
    private LoginSuccessListener loginSuccessListener;

    public interface LoginSuccessListener {
        void onLoginSuccess(String email);
    }

    public void setLoginSuccessListener(LoginSuccessListener listener) {
        this.loginSuccessListener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        mAuth = FirebaseAuth.getInstance();

        View view = getLayoutInflater().inflate(R.layout.dialog_register, null);
        EditText emailEditText = view.findViewById(R.id.emailEditText);
        EditText passwordEditText = view.findViewById(R.id.passwordEditText);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Connexion Firebase")
                .setView(view)
                .setPositiveButton("Connexion", null)
                .setNegativeButton(getString(R.string.cancel), (dialogInterface, which) -> dismiss())
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = emailEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString();

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
                Toast.makeText(getContext(), getString(R.string.please_fill_all), Toast.LENGTH_SHORT).show();
                return;
            }

            loginWithFirebase(email, password, dialog); // on passe le dialog
        }));

        return dialog;
    }

    private void loginWithFirebase(String email, String password, AlertDialog dialog) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(requireActivity(), task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && loginSuccessListener != null) {
                            loginSuccessListener.onLoginSuccess(user.getEmail());
                        }
                        if (getActivity() != null) {
                            Toast.makeText(getContext(), getString(R.string.connect_success), Toast.LENGTH_SHORT).show();
                        }
                        dialog.dismiss(); // on ferme le dialog seulement ici
                    } else {
                        Toast.makeText(getContext(), getString(R.string.connect_error)  + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
