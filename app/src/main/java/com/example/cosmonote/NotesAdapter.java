package com.example.cosmonote;

import com.cosmonote.app.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NotesAdapter extends RecyclerView.Adapter<NotesAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
        void onColorPickerClick(Note note);
    }

    private final List<Note> notes;
    private final OnNoteClickListener listener;
    private int selectedPosition = RecyclerView.NO_POSITION; // -1

    public NotesAdapter(List<Note> notes, OnNoteClickListener listener) {
        this.notes = notes;
        this.listener = listener;
    }

    public void setNotes(List<Note> newNotes) {
        this.notes.clear();
        this.notes.addAll(newNotes);
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
        holder.cardView.setOnClickListener(v -> {
            if (listener != null) listener.onNoteClick(note);
            // Met à jour la position sélectionnée et rafraîchit la liste
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
        TextView title, content;
        CardView cardView;
        ImageButton buttonColorPicker;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.card_view_note);
            title = itemView.findViewById(R.id.note_title);
            content = itemView.findViewById(R.id.note_content);
            buttonColorPicker = itemView.findViewById(R.id.button_color_picker);
        }
    }

}

