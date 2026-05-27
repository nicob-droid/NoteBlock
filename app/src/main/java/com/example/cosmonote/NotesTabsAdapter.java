package com.example.cosmonote;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class NotesTabsAdapter extends FragmentStateAdapter {
    private NotesListFragment localNotesFragment;
    private NotesListFragment syncedNotesFragment;
    private boolean showSyncedTab;

    public NotesTabsAdapter(@NonNull FragmentActivity fragmentActivity, boolean showSyncedTab) {
        super(fragmentActivity);
        this.showSyncedTab = showSyncedTab;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == 0) {
            if (localNotesFragment == null) {
                localNotesFragment = NotesListFragment.newInstance(true);
            }
            return localNotesFragment;
        } else {
            if (syncedNotesFragment == null) {
                syncedNotesFragment = NotesListFragment.newInstance(false);
            }
            return syncedNotesFragment;
        }
    }

    @Override
    public int getItemCount() {
        return showSyncedTab ? 2 : 1;
    }

    public void setShowSyncedTab(boolean showSyncedTab) {
        this.showSyncedTab = showSyncedTab;
        notifyDataSetChanged();
    }

    public boolean isShowingSyncedTab() {
        return showSyncedTab;
    }

    public NotesListFragment getLocalNotesFragment() {
        return localNotesFragment;
    }

    public NotesListFragment getSyncedNotesFragment() {
        return syncedNotesFragment;
    }
}

