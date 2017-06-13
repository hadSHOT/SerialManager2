package kg.delletenebre.serialmanager2;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;

import java.lang.reflect.Field;

import kg.delletenebre.serialmanager2.commands.Adapter;
import kg.delletenebre.serialmanager2.commands.EditActivity;


public class MainActivity extends AppCompatActivity {

    private RecyclerView mCommandsRecyclerView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }

        try {
            ViewConfiguration config = ViewConfiguration.get(this);
            Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
            if (menuKeyField != null) {
                menuKeyField.setAccessible(true);
                menuKeyField.setBoolean(config, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        mCommandsRecyclerView = (RecyclerView) findViewById(R.id.commands_recycler_view);
        mCommandsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mCommandsRecyclerView.setAdapter(new Adapter());
        mCommandsRecyclerView.setHasFixedSize(true);
        mCommandsRecyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        setupItemTouchHelper();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, EditActivity.class);
                intent.putExtra("CommandIndex", mCommandsRecyclerView.getAdapter().getItemCount());
                intent.putExtra("isNew", true);
                MainActivity.this.startActivity(intent);
            }
        });

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("check_for_updates",
                getResources().getBoolean(R.bool.pref_default_check_for_updates))) {
            new Updater(false).execute(this);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                break;

            case R.id.restart_service:
                Intent communicationServiceIntent = new Intent(this, CommunicationService.class);
                stopService(communicationServiceIntent);
                startService(communicationServiceIntent);
                break;

            case R.id.report_crash:
                sendCrashReport();
                break;

            case R.id.check_updates:
                new Updater(true).execute(this);
                break;

        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ((Adapter)mCommandsRecyclerView.getAdapter()).updateRealmInstance();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }



    private void setupItemTouchHelper() {

        ItemTouchHelper.SimpleCallback simpleItemTouchCallback =
                new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                                                   ItemTouchHelper.LEFT) {

            // we want to cache these and not allocate anything repeatedly in the onChildDraw method
            Drawable background;
            Drawable xMark;
            int xMarkMargin;
            boolean initiated;

            private void init() {
                Context context = MainActivity.this;
                background = new ColorDrawable(ContextCompat.getColor(context, R.color.danger));
                xMark = ContextCompat.getDrawable(context, R.drawable.ic_delete_black_24dp);
                xMark.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
                xMarkMargin = (int) getResources().getDimension(R.dimen.ic_clear_margin);
                initiated = true;
            }

            @Override
            public int getMovementFlags(RecyclerView recyclerView,
                                        RecyclerView.ViewHolder viewHolder) {
                int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                int swipeFlags = ItemTouchHelper.LEFT;
                return makeMovementFlags(dragFlags, swipeFlags);
            }

            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                  RecyclerView.ViewHolder target) {
                int moveFrom = viewHolder.getAdapterPosition();
                int moveTo = target.getAdapterPosition();

                Adapter adapter = (Adapter) mCommandsRecyclerView.getAdapter();

                adapter.updatePositions(moveFrom, moveTo);

                return true;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, int swipeDir) {
                final int itemIndex = viewHolder.getAdapterPosition();
                final Adapter adapter = (Adapter) mCommandsRecyclerView.getAdapter();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(getString(R.string.dialog_title_confirm_delete_command))
                        .setMessage(String.format(getString(R.string.dialog_message_delete_command),
                                adapter.getItemTitle(MainActivity.this, itemIndex)))
                        .setPositiveButton(getString(R.string.dialog_delete_positive),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        adapter.remove(itemIndex);
                                        dialog.dismiss();
                                    }
                                }
                        )
                        .setNeutralButton(getString(R.string.dialog_negative),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Adapter adapter = (Adapter) mCommandsRecyclerView.getAdapter();
                                        adapter.notifyItemChanged(itemIndex);
                                        dialog.cancel();
                                    }
                                }
                        )
                        .create()
                        .show();


            }

            @Override
            public void onChildDraw(Canvas canvas, RecyclerView recyclerView,
                                    RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    View itemView = viewHolder.itemView;

                    // not sure why, but this method get's called for viewholder that are already swiped away
                    if (viewHolder.getAdapterPosition() == -1) {
                        // not interested in those
                        return;
                    }

                    if (!initiated) {
                        init();
                    }

                    // draw red background
                    background.setBounds(itemView.getRight() + (int) dX,
                            itemView.getTop(), itemView.getRight(), itemView.getBottom());
                    background.draw(canvas);

                    // draw x mark
                    int itemHeight = itemView.getBottom() - itemView.getTop();
                    int intrinsicWidth = xMark.getIntrinsicWidth();
                    int intrinsicHeight = xMark.getIntrinsicWidth();

                    int xMarkLeft = itemView.getRight() - xMarkMargin - intrinsicWidth;
                    int xMarkRight = itemView.getRight() - xMarkMargin;
                    int xMarkTop = itemView.getTop() + (itemHeight - intrinsicHeight) / 2;
                    int xMarkBottom = xMarkTop + intrinsicHeight;
                    xMark.setBounds(xMarkLeft, xMarkTop, xMarkRight, xMarkBottom);

                    xMark.draw(canvas);
                }

                super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

        };

        ItemTouchHelper mItemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        mItemTouchHelper.attachToRecyclerView(mCommandsRecyclerView);
    }

    private void sendCrashReport() {
        throw new RuntimeException("User report");
    }

    public void update(final String lastAppVersion) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(String.format(getString(R.string.new_update_available), lastAppVersion))
                        .setCancelable(true)
                        .setPositiveButton(getString(R.string.yes),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Intent intent = new Intent(Intent.ACTION_VIEW);
                                        String apkUrl = "https://raw.githubusercontent.com/delletenebre/SerialManager2/master/apk/SerialManager-" + lastAppVersion + ".apk";
                                        intent.setData(Uri.parse(apkUrl));

                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        dialog.dismiss();
                                    }
                                }
                        )
                        .setNegativeButton(getString(R.string.no),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                }
                        )
                        .setNeutralButton(getString(R.string.ignore_this_version),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                                        SharedPreferences.Editor editor = prefs.edit();
                                        editor.putString(Updater.PREF_NAME_LAST_IGNORED_VERSION, lastAppVersion);
                                        editor.apply();
                                        dialog.cancel();
                                    }
                                }
                        )
                        .create()
                        .show();
            }
        });
    }

    public void showNoUpdatesAvailable() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(getString(R.string.no_updates_available))
                        .setCancelable(true)
                        .setPositiveButton(getString(R.string.awesome),
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.dismiss();
                                    }
                                }
                        )
                        .create()
                        .show();
            }
        });
    }
}
