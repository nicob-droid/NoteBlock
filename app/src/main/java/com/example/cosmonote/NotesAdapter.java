package com.example.cosmonote;

import com.cosmonote.app.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
        void onColorPickerClick(Note note);
    }

    private final List<Note> notes;
    private final OnNoteClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION; // -1
    private final Map<String, NoteLockState> lockStates = new HashMap<>();

    public NotesAdapter(List<Note> notes, OnNoteClickListener listener) {
        this.notes = notes;
        this.listener = listener;
    }

    public void setNotes(List<Note> newNotes) {
        this.notes.clear();
        this.notes.addAll(newNotes);
        notifyDataSetChanged();
    }

    public void setLockStates(Map<String, NoteLockState> newLockStates) {
        lockStates.clear();
        if (newLockStates != null) {
            lockStates.putAll(newLockStates);
        }
        notifyDataSetChanged();
    }


    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = notes.get(position);
        holder.title.setText(note.getTitle());
        holder.content.setText(note.getContent());
        holder.cardView.setCardBackgroundColor(note.getColor());

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String currentUid = currentUser != null ? currentUser.getUid() : null;
        long now = System.currentTimeMillis();

        NoteLockState lockState = lockStates.get(note.getFirebaseDocId());
        boolean lockedByOther = lockState != null && lockState.isLockedByOtherUser(currentUid, now);

        if (lockedByOther) {
            String byName = lockState.getLockedByName();
            if (byName == null || byName.trim().isEmpty()) {
                byName = holder.itemView.getContext().getString(R.string.someone_label);
            }
            holder.lockStatus.setText(holder.itemView.getContext().getString(R.string.note_locked_by_user, byName));
            holder.lockBadge.setVisibility(View.VISIBLE);
            holder.cardView.setAlpha(0.55f);
        } else {
            holder.lockBadge.setVisibility(View.GONE);
            holder.cardView.setAlpha(1f);
        }

        holder.cardView.setOnClickListener(v -> {
            if (lockedByOther) {
                Toast.makeText(v.getContext(), R.string.note_locked_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            if (listener != null) listener.onNoteClick(note);
            int previousPosition = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previousPosition);
            notifyItemChanged(selectedPosition);
        });

        holder.buttonColorPicker.setOnClickListener(v -> {
            if (listener != null) listener.onColorPickerClick(note);
        });
    }

    @Override
    public int getItemCount() {
        return (notes == null) ? 0 : notes.size();
    }

    public static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView title, content, lockStatus;
        CardView cardView;
        View lockBadge;
        ImageButton buttonColorPicker;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view_note);
            title = itemView.findViewById(R.id.note_title);
            content = itemView.findViewById(R.id.note_content);
            lockBadge = itemView.findViewById(R.id.note_lock_badge);
            lockStatus = itemView.findViewById(R.id.note_lock_status);
            buttonColorPicker = itemView.findViewById(R.id.button_color_picker);
        }
    }

}
