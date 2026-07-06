package com.example.cosmonote;

import android.os.Bundle;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class NotesListFragment extends Fragment implements NotesAdapter.OnNoteClickListener {
    private static final String ARG_IS_LOCAL = "is_local";
    private static final String ARG_FILTER_MODE = "filter_mode";
    private static final int FILTER_ALL = 0;
    private static final int FILTER_LOCAL = 1;
    private static final int FILTER_SYNCED = 2;
    private int filterMode = FILTER_ALL;
    private NotesAdapter adapter;
    private List<Note> notesList;
    private NoteDatabase db;
    private ItemTouchHelper itemTouchHelper;
    private RecyclerView recyclerView;

    public static NotesListFragment newInstanceAll() {
        NotesListFragment fragment = new NotesListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_FILTER_MODE, FILTER_ALL);
        fragment.setArguments(args);
        return fragment;
    }

    public static NotesListFragment newInstance(boolean isLocal) {
        NotesListFragment fragment = new NotesListFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_IS_LOCAL, isLocal);
        args.putInt(ARG_FILTER_MODE, isLocal ? FILTER_LOCAL : FILTER_SYNCED);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            if (getArguments().containsKey(ARG_FILTER_MODE)) {
                filterMode = getArguments().getInt(ARG_FILTER_MODE, FILTER_ALL);
            } else if (getArguments().containsKey(ARG_IS_LOCAL)) {
                filterMode = getArguments().getBoolean(ARG_IS_LOCAL) ? FILTER_LOCAL : FILTER_SYNCED;
            }
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
        this.recyclerView = recyclerView;

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

        // Filtrer selon le mode courant
        List<Note> filteredNotes;
        if (filterMode == FILTER_LOCAL) {
            filteredNotes = allNotes.stream()
                    .filter(note -> note.getFirebaseDocId() == null || note.getFirebaseDocId().isEmpty() || !userLoggedIn)
                    .collect(Collectors.toList());
        } else if (filterMode == FILTER_SYNCED) {
            filteredNotes = allNotes.stream()
                    .filter(note -> note.getFirebaseDocId() != null && !note.getFirebaseDocId().isEmpty() && userLoggedIn)
                    .collect(Collectors.toList());
        } else {
            filteredNotes = allNotes;
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

        if (adapter != null) {
            Map<String, NoteLockState> lockStates = new HashMap<>();
            if (getActivity() instanceof NotesActivity) {
                lockStates.putAll(((NotesActivity) getActivity()).getActiveNoteLocks());
            }
            adapter.setLockStates(lockStates);
        }
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

    @Override
    public void onResume() {
        super.onResume();
        // Après un retour d'arrière-plan (ex. écran verrouillé/déverrouillé), le rendu
        // des items peut rester figé/altéré alors que le layout est correct.
        // On force la reconstruction des vues pour garantir un rendu propre.
        forceRecyclerRedraw();
    }

    private void forceRecyclerRedraw() {
        if (recyclerView == null || adapter == null) {
            return;
        }
        recyclerView.post(() -> {
            if (recyclerView == null || adapter == null) {
                return;
            }
            // Vider le pool force la recréation complète des ViewHolders (rendu neuf).
            recyclerView.getRecycledViewPool().clear();
            adapter.notifyDataSetChanged();
            recyclerView.invalidateItemDecorations();
        });
    }
}
