package com.example.noteblock;

import androidx.recyclerview.widget.DiffUtil;

import java.util.List;

public class NotesDiffCallback extends DiffUtil.Callback {

    private final List<Note> oldList;
    private final List<Note> newList;

    public NotesDiffCallback(List<Note> oldList, List<Note> newList) {
        this.oldList = oldList;
        this.newList = newList;
    }

    @Override
    public int getOldListSize() {
        return oldList == null ? 0 : oldList.size();
    }

    @Override
    public int getNewListSize() {
        return newList == null ? 0 : newList.size();
    }

    @Override
    public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
        // Compare les ID de notes
        return oldList.get(oldItemPosition).getId() == newList.get(newItemPosition).getId();
    }

    @Override
    public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
        Note oldNote = oldList.get(oldItemPosition);
        Note newNote = newList.get(newItemPosition);
        return oldNote.getTitle().equals(newNote.getTitle()) &&
                oldNote.getContent().equals(newNote.getContent());
    }
}