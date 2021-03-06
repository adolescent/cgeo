package cgeo.geocaching;

import cgeo.geocaching.activity.ActivityMixin;
import cgeo.geocaching.utils.RunnableWithArgument;

import org.apache.commons.lang3.StringUtils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.view.View;
import android.widget.EditText;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class StoredList {
    public static final int TEMPORARY_LIST_ID = 0;
    public static final int STANDARD_LIST_ID = 1;
    public static final int ALL_LIST_ID = 2;

    public final int id;
    public final String title;
    private final int count; // this value is only valid as long as the list is not changed by other database operations

    public StoredList(int id, String title, int count) {
        this.id = id;
        this.title = title;
        this.count = count;
    }

    public String getTitleAndCount() {
        return title + " [" + count + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof StoredList)) {
            return false;
        }
        return id == ((StoredList) obj).id;
    }

    public static class UserInterface {
        private final Activity activity;
        private final cgeoapplication app;
        private final Resources res;

        public UserInterface(final Activity activity) {
            this.activity = activity;
            app = cgeoapplication.getInstance();
            res = app.getResources();
        }

        public void promptForListSelection(final int titleId, final RunnableWithArgument<Integer> runAfterwards) {
            promptForListSelection(titleId, runAfterwards, false, -1);
        }

        public void promptForListSelection(final int titleId, final RunnableWithArgument<Integer> runAfterwards, final boolean onlyConcreteLists, final int exceptListId) {
            promptForListSelection(titleId, runAfterwards, onlyConcreteLists, exceptListId, StringUtils.EMPTY);
        }

        public void promptForListSelection(final int titleId, final RunnableWithArgument<Integer> runAfterwards, final boolean onlyConcreteLists, final int exceptListId, final String newListName) {
            final List<StoredList> lists = getSortedLists();

            if (lists == null) {
                return;
            }

            if (exceptListId > StoredList.TEMPORARY_LIST_ID) {
                StoredList exceptList = cgData.getList(exceptListId);
                if (exceptList != null) {
                    lists.remove(exceptList);
                }
            }

            final List<CharSequence> listsTitle = new ArrayList<CharSequence>();
            for (StoredList list : lists) {
                listsTitle.add(list.getTitleAndCount());
            }
            if (!onlyConcreteLists) {
                listsTitle.add("<" + res.getString(R.string.list_menu_all_lists) + ">");
            }
            listsTitle.add("<" + res.getString(R.string.list_menu_create) + ">");

            final CharSequence[] items = new CharSequence[listsTitle.size()];

            AlertDialog.Builder builder = new AlertDialog.Builder(activity);
            builder.setTitle(res.getString(titleId));
            builder.setItems(listsTitle.toArray(items), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int itemId) {
                    if (itemId == lists.size() && !onlyConcreteLists) {
                        // all lists
                        runAfterwards.run(StoredList.ALL_LIST_ID);
                    } else if (itemId >= lists.size()) {
                        // create new list on the fly
                        promptForListCreation(runAfterwards, newListName);
                    }
                    else {
                        if (runAfterwards != null) {
                            runAfterwards.run(lists.get(itemId).id);
                        }
                    }
                }
            });
            builder.create().show();
        }

        private static List<StoredList> getSortedLists() {
            final Collator collator = Collator.getInstance();
            final List<StoredList> lists = cgData.getLists();
            Collections.sort(lists, new Comparator<StoredList>() {

                @Override
                public int compare(StoredList lhs, StoredList rhs) {
                    // have the standard list at the top
                    if (lhs.id == STANDARD_LIST_ID) {
                        return -1;
                    }
                    if (rhs.id == STANDARD_LIST_ID) {
                        return 1;
                    }
                    // otherwise sort alphabetical
                    return collator.compare(lhs.getTitle(), rhs.getTitle());
                }
            });
            return lists;
        }

        public void promptForListCreation(final RunnableWithArgument<Integer> runAfterwards, String newListName) {
            handleListNameInput(newListName, R.string.list_dialog_create_title, R.string.list_dialog_create, new RunnableWithArgument<String>() {

                @Override
                public void run(final String listName) {
                    final int newId = cgData.createList(listName);

                    if (newId >= cgData.customListIdOffset) {
                        ActivityMixin.showToast(activity, res.getString(R.string.list_dialog_create_ok));
                        if (runAfterwards != null) {
                            runAfterwards.run(newId);
                        }
                    } else {
                        ActivityMixin.showToast(activity, res.getString(R.string.list_dialog_create_err));
                    }
                }
            });
        }

        private void handleListNameInput(final String defaultValue, int dialogTitle, int buttonTitle, final RunnableWithArgument<String> runnable) {
            final AlertDialog.Builder alert = new AlertDialog.Builder(activity);
            final View view = activity.getLayoutInflater().inflate(R.layout.list_create_dialog, null);
            final EditText input = (EditText) view.findViewById(R.id.text);
            input.setText(defaultValue);

            alert.setTitle(dialogTitle);
            alert.setView(view);
            alert.setPositiveButton(buttonTitle, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    // remove whitespaces added by autocompletion of Android keyboard
                    String listName = StringUtils.trim(input.getText().toString());
                    if (StringUtils.isNotBlank(listName)) {
                        runnable.run(listName);
                    }
                }
            });
            alert.setNegativeButton(res.getString(R.string.list_dialog_cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.dismiss();
                }
            });

            alert.show();
        }

        public void promptForListRename(final int listId, final Runnable runAfterRename) {
            final StoredList list = cgData.getList(listId);
            handleListNameInput(list.title, R.string.list_dialog_rename_title, R.string.list_dialog_rename, new RunnableWithArgument<String>() {

                @Override
                public void run(final String listName) {
                    cgData.renameList(listId, listName);
                    if (runAfterRename != null) {
                        runAfterRename.run();
                    }
                }
            });
        }
    }

    /**
     * Get the list title. This method is not public by intention to make clients use the {@link UserInterface} class.
     *
     * @return
     */
    protected String getTitle() {
        return title;
    }

    /**
     * Return the given list, if it is a concrete list. Return the default list otherwise.
     */
    public static int getConcreteList(int listId) {
        if (listId == ALL_LIST_ID || listId == TEMPORARY_LIST_ID) {
            return STANDARD_LIST_ID;
        }
        return listId;
    }
}
