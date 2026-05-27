package com.example.cosmonote;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cosmonote.app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NotesListFragment extends Fragment implements NotesAdapter.OnNoteClickListener {
    private static final String ARG_IS_LOCAL = "is_local";
    private static final String TAG = "NotesListFragment";
    private boolean isLocalNotes;
    private NotesAdapter adapter;
    private List<Note> notesList;
    private NoteDatabase db;
    private ItemTouchHelper itemTouchHelper;

    public static NotesListFragment newInstance(boolean isLocal) {
        NotesListFragment fragment = new NotesListFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_LOCAL, isLocal);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isLocalNotes = getArguments().getBoolean(ARG_IS_LOCAL);
        }
        db = new NoteDatabase(requireContext());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notes_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.notes_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        notesList = new ArrayList<>();
        adapter = new NotesAdapter(notesList, this);
        recyclerView.setAdapter(adapter);

        // Charger les notes filtrées (locales ou synchronisées)
        loadAndDisplayNotes();

        // Ajouter ItemTouchHelper pour drag & drop
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                Collections.swap(notesList, fromPosition, toPosition);
                adapter.notifyItemMoved(fromPosition, toPosition);
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                updateItemPositionsInDatabase();
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // No swipe
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }
        };
        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(recyclerView);
    }

    private void loadAndDisplayNotes() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        boolean userLoggedIn = user != null;

        // Charger toutes les notes depuis la base locale
        List<Note> allNotes = db.getAllNotes();

        // Filtrer selon le type d'onglet
        List<Note> filteredNotes;
        if (isLocalNotes) {
            // Notes locales = pas de firebaseDocId ou utilisateur pas connecté
            filteredNotes = allNotes.stream()
                    .filter(note -> note.getFirebaseDocId() == null || note.getFirebaseDocId().isEmpty() || !userLoggedIn)
                    .collect(Collectors.toList());
        } else {
            // Notes synchronisées = firebaseDocId existe ET utilisateur connecté
            filteredNotes = allNotes.stream()
                    .filter(note -> note.getFirebaseDocId() != null && !note.getFirebaseDocId().isEmpty() && userLoggedIn)
                    .collect(Collectors.toList());
        }

        // Calculer les différences avec DiffUtil
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return notesList.size();
            }

            @Override
            public int getNewListSize() {
                return filteredNotes.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return notesList.get(oldItemPosition).getId() == filteredNotes.get(newItemPosition).getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                return notesList.get(oldItemPosition).equals(filteredNotes.get(newItemPosition));
            }
        });

        notesList.clear();
        notesList.addAll(filteredNotes);
        diffResult.dispatchUpdatesTo(adapter);
    }

    private void updateItemPositionsInDatabase() {
        db.updateAllNotePositions(notesList);
    }

    @Override
    public void onNoteClick(Note note) {
        // Déléguer à l'activité parent
        if (getActivity() instanceof NotesActivity) {
            ((NotesActivity) getActivity()).onNoteClick(note);
        }
    }

    @Override
    public void onColorPickerClick(Note note) {
        // Déléguer à l'activité parent
        if (getActivity() instanceof NotesActivity) {
            ((NotesActivity) getActivity()).onColorPickerClick(note);
        }
    }

    public void refreshNotes() {
        if (isAdded()) {
            loadAndDisplayNotes();
        }
    }
}

