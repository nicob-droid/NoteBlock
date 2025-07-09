package com.example.noteblock.Settings;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;

import com.example.noteblock.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class SettingsFragment extends PreferenceFragmentCompat implements LoginDialogFragment.LoginSuccessListener {

    private FirebaseAuth auth;

    private Preference registerPref, loginPref, logoutPref;
    private Preference shareIdPref, saveSharingPref, deleteSharingPref;
    private EditTextPreference sharedUserIdPref;
    private PreferenceCategory sharingNotesCategory;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_settings, rootKey);
        auth = FirebaseAuth.getInstance();
        setupPreferences();
    }

    private void setupPreferences() {
        registerPref = findPreference(getString(R.string.key_register));
        loginPref = findPreference(getString(R.string.key_login));
        logoutPref = findPreference(getString(R.string.key_logout));
        sharedUserIdPref = findPreference(getString(R.string.key_shared_user_id));
        shareIdPref = findPreference(getString(R.string.key_share_id));
        saveSharingPref = findPreference(getString(R.string.key_save_sharing));
        deleteSharingPref = findPreference(getString(R.string.key_delete_sharing));
        sharingNotesCategory = findPreference(getString(R.string.sharing_notes));
        ListPreference themePref = findPreference(getString(R.string.key_theme_preference));
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((preference, newValue) -> {
                requireActivity().recreate(); // Applique le nouveau thème immédiatement
                return true;
            });
        }

        FirebaseUser user = auth.getCurrentUser();
        updateUI(user);

        if (registerPref != null) {
            registerPref.setOnPreferenceClickListener(preference -> {
                RegisterDialogFragment dialog = new RegisterDialogFragment();
                dialog.setRegisterDialogListener(email -> {
                    // Optionnel
                });
                dialog.show(getParentFragmentManager(), "register_dialog");
                return true;
            });
        }

        if (loginPref != null) {
            loginPref.setOnPreferenceClickListener(preference -> {
                LoginDialogFragment dialog = new LoginDialogFragment();
                dialog.setLoginSuccessListener(this);
                dialog.show(getParentFragmentManager(), "login_dialog");
                return true;
            });
        }

        if (logoutPref != null) {
            logoutPref.setOnPreferenceClickListener(preference -> {
                auth.signOut();
                Toast.makeText(getContext(), getString(R.string.disconnect_success), Toast.LENGTH_SHORT).show();
                setPreferencesFromResource(R.xml.preferences_settings, null);
                setupPreferences();
                return true;
            });
        }

        if (sharedUserIdPref != null) {
            sharedUserIdPref.setOnPreferenceChangeListener((preference, newValue) -> {
                String newUid = (String) newValue;
                //Toast.makeText(getContext(), "UID partagé mis à jour : " + newUid, Toast.LENGTH_SHORT).show();
                // Afficher le nouvel UID dans le résumé
                sharedUserIdPref.setSummary(getString(R.string.sharing_enabled_with) + newUid);
                return true;
            });
        }

        if (shareIdPref != null) {
            shareIdPref.setOnPreferenceClickListener(preference -> {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.my_current_id) + "\n" + user.getUid());
                startActivity(Intent.createChooser(shareIntent, getString(R.string.share_id_with)));
                return true;
            });
        }

        if ((saveSharingPref != null) && (user != null)) {
            saveSharingPref.setOnPreferenceClickListener(preference -> {
                saveSharedUserId(user.getUid(), sharedUserIdPref.getText());
                return true;
            });
        }

        if (deleteSharingPref != null) {
            deleteSharingPref.setOnPreferenceClickListener(preference -> {
                if (user != null) {
                    String ownerUid = user.getUid();
                    FirebaseFirestore.getInstance()
                            .collection("shared_users")
                            .document(ownerUid)
                            .delete()
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(getContext(), getString(R.string.share_deleted), Toast.LENGTH_SHORT).show();
                                if (sharedUserIdPref != null) {
                                    sharedUserIdPref.setText("");
                                    sharedUserIdPref.setSummary(getString(R.string.uid_to_share_notes_with));
                                }
                                updateSharingPrefState(ownerUid);
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), getString(R.string.delete_failed), Toast.LENGTH_SHORT).show();
                                Log.e("Firestore", "Erreur suppression partage", e);
                            });
                }
                return true;
            });
        }

        //
        initSharedUserId(auth.getCurrentUser());
    }

    @Override
    public void onLoginSuccess(String email) {
        setPreferencesFromResource(R.xml.preferences_settings, null);
        setupPreferences();
        Toast.makeText(getContext(), "Connecté comme : " + email, Toast.LENGTH_SHORT).show();
    }

    private void updateUI(FirebaseUser user) {
        boolean isConnected = (user != null);

        if (logoutPref != null) logoutPref.setVisible(isConnected);
        if (loginPref != null) loginPref.setVisible(!isConnected);
        if (registerPref != null) registerPref.setVisible(!isConnected);
        if (sharingNotesCategory != null) {
            if(isConnected) {
                getPreferenceScreen().addPreference(sharingNotesCategory);
            } else {
                getPreferenceScreen().removePreference(sharingNotesCategory);
            }

        }

    }

    private void initSharedUserId(FirebaseUser user) {
        if (user != null) {
            String ownerUid = user.getUid();
            FirebaseFirestore.getInstance()
                    .collection("shared_users")
                    .document(ownerUid)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String sharedUid = documentSnapshot.getString("sharedUserId");
                            if (sharedUid != null && sharedUserIdPref != null) {
                                sharedUserIdPref.setText(sharedUid);
                                sharedUserIdPref.setSummary(getString(R.string.sharing_enabled_with) + sharedUid);
                            }
                        }
                        // Met à jour l'état des boutons
                        updateSharingPrefState(ownerUid);
                    })
                    .addOnFailureListener(e -> {
                        Log.e("Firestore", "Erreur lors du chargement du sharedUserId", e);

                    });
        }
    }

    private void updateSharingPrefState(String ownerUid) {
        FirebaseFirestore.getInstance()
                .collection("shared_users")
                .document(ownerUid)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean hasSharedUid = documentSnapshot.exists() &&
                            documentSnapshot.getString("sharedUserId") != null &&
                            !documentSnapshot.getString("sharedUserId").isEmpty();

                    if (saveSharingPref != null) {
                        saveSharingPref.setEnabled(!hasSharedUid);
                    }
                    if (deleteSharingPref != null) {
                        deleteSharingPref.setEnabled(hasSharedUid);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("Firestore", "Erreur lors de la mise à jour des préférences de partage", e);
                });
    }

    private void saveSharedUserId(String ownerUserId, String sharedUserId) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Map<String, Object> data = new HashMap<>();
        data.put("ownerId", ownerUserId);
        data.put("sharedUserId", sharedUserId);

        db.collection("shared_users")
                .document(ownerUserId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), getString(R.string.share_saved), Toast.LENGTH_SHORT).show();
                    updateSharingPrefState(ownerUserId);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), getString(R.string.share_save_error), Toast.LENGTH_LONG).show();
                });
    }

}
