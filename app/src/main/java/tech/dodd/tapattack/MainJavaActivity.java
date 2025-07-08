package tech.dodd.tapattack;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.AuthenticationResult;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Objects;

import tech.dodd.tapattack.databinding.ActivityMainBinding;

public class MainJavaActivity extends AppCompatActivity {
    private static final String TAG = "TapAttack"; // tag for debug logging
    private int clickScore = 0; // The game score
    private int numPlays = 0; // Number of times the game is played
    private boolean playing = false; // Whether the game is being played or not
    private Boolean isAuthenticated = false;
    private int day1night2 = 1;
    public static AchievementsClient mAchievementsClient; // Client Achievement Variable
    public static LeaderboardsClient mLeaderboardsClient; // Client Leaderboard Variable
    ActivityMainBinding activityMainBinding; // MainJavaActivity dataBinding
    ActivityResultLauncher<Intent> signInActivityResultLauncher;
    SwitchMaterial dayNightSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityMainBinding = ActivityMainBinding.inflate(getLayoutInflater());
        View view = activityMainBinding.getRoot();
        setContentView(view);

        GPGSSignIn();

        signInActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                });

        activityMainBinding.signinButton.setOnClickListener(v -> GPGSSignIn());
        activityMainBinding.mainButton.setOnClickListener(v -> Game());
        activityMainBinding.achievementsButton.setOnClickListener(v -> Achievements());
        activityMainBinding.leaderboardButton.setOnClickListener(v -> Leaderboard());
        mAchievementsClient = PlayGames.getAchievementsClient(this);
        mLeaderboardsClient = PlayGames.getLeaderboardsClient(this);
    }

    private void GPGSSignIn() {
        //GPGS Login / Check Logged In
        GamesSignInClient gamesSignInClient = PlayGames.getGamesSignInClient(this);
        gamesSignInClient.signIn().addOnCompleteListener(isAuthenticatedTask -> {
            AuthenticationResult authenticationResult = isAuthenticatedTask.getResult();
            if (isAuthenticatedTask.isSuccessful() && authenticationResult.isAuthenticated()) {
                // Enable Play Games Services integration
                isAuthenticated = true;
                // Set the greeting appropriately on main menu
                PlayGames.getPlayersClient(this).getCurrentPlayer().addOnCompleteListener(mTask -> activityMainBinding.nameTextView.setText(mTask.getResult().getDisplayName()));
                activityMainBinding.signinLayout.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        //Practice Day/Night Themes
        MenuItem item = menu.findItem(R.id.daynightSwitch);
        dayNightSwitch = Objects.requireNonNull(item.getActionView()).findViewById(R.id.daynightSwitch);

        if (day1night2 == 2) {
            dayNightSwitch.setChecked(true);
        }

        dayNightSwitch.setOnClickListener(view -> {
            if (day1night2 == 1) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else if (day1night2 == 2) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    public void Achievements(){
        if (isAuthenticated) {
            mAchievementsClient.getAchievementsIntent()
                    .addOnSuccessListener(intent -> signInActivityResultLauncher.launch(intent))
                    .addOnFailureListener(e -> handleException(e, getString(R.string.achievements_exception)));
        } else {
            Toast.makeText(this, getString(R.string.notconnectedtext), Toast.LENGTH_LONG).show();
        }
    }

    public void Leaderboard(){
        //If the Leaderboards button is pressed and the user is signed in show Leaderboards else Toast
        if (isAuthenticated) {
            mLeaderboardsClient.getAllLeaderboardsIntent()
                    .addOnSuccessListener(intent -> signInActivityResultLauncher.launch(intent))
                    .addOnFailureListener(e -> handleException(e, getString(R.string.leaderboards_exception)));
        } else {
            Toast.makeText(this, getString(R.string.notconnectedtext), Toast.LENGTH_LONG).show();
        }
    }

    private void Game() {
        if (!playing) {
            // The first click
            playing = true;
            clickScore = 0;
            activityMainBinding.mainButton.setText(R.string.keep_clicking);

            // Initialize CountDownTimer to 30 seconds
            new CountDownTimer(30000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    //As each second ticks down during the game the textView is updated.
                    String timeViewText = getResources().getString(R.string.timeviewstring, (millisUntilFinished / 1000) + 1);
                    activityMainBinding.timeTextView.setText(timeViewText);
                }

                @Override
                public void onFinish() {
                    //When the game is finished - the time has run out.

                    //Resets the game
                    playing = false;
                    activityMainBinding.mainButton.setText(R.string.start_text);
                    activityMainBinding.timeTextView.setText(R.string.game_over_text);

                    numPlays++; //Updates the number of times the game is played.
                    checkForAchievements(clickScore);
                }
            }.start();  // Start the timer
        } else {
            // Subsequent clicks
            clickScore++;
            String clickScoreText = getResources().getString(R.string.clickscorestring, clickScore);
            activityMainBinding.scoreTextView.setText(clickScoreText);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        int nightModeFlags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_YES:
                day1night2 = 2;
                break;
            case Configuration.UI_MODE_NIGHT_NO:
                day1night2 = 1;
                break;
        }
        Log.d(TAG, "onResume()");
    }

    // Check for achievements and unlock the appropriate ones
    private void checkForAchievements(int score) {
        // Check if each condition is met; if so, unlock the corresponding achievement.
        if (score >= 200) {
            handleAchievement(isAuthenticated, getString(R.string.achievement_tapnado), getString(R.string.achievement_tapnado_toast_text));
        } else if (score >= 150) {
            handleAchievement(isAuthenticated, getString(R.string.achievement_hailacious), getString(R.string.achievement_hailacious_toast_text));
        } else if (score >= 100) {
            handleAchievement(isAuthenticated, getString(R.string.achievement_lightning), getString(R.string.achievement_lightning_toast_text));
        } else if (score >= 50) {
            handleAchievement(isAuthenticated, getString(R.string.achievement_breezy), getString(R.string.achievement_breezy_toast_text));
        }
        if (numPlays > 0) {
            mAchievementsClient.increment(getString(R.string.achievement_play_a_whole_lot), numPlays);
            mAchievementsClient.increment(getString(R.string.achievement_play_a_lot), numPlays);
        }
        //Change this number to only submit scores higher than x
        if (clickScore >= 30) {
            mLeaderboardsClient.submitScore(getString(R.string.leaderboard_most_taps_attacked), clickScore);
        }
    }

    private void handleAchievement(boolean isAuthenticated, String achievementId, String toastTextResId) {
        if (isAuthenticated) {
            mAchievementsClient.unlock(achievementId);
        } else {
            Toast.makeText(this, toastTextResId, Toast.LENGTH_SHORT).show();
        }
    }

    private void handleException(Exception e, String details) {
        int status = 0;

        if (e instanceof ApiException apiException) {
            status = apiException.getStatusCode();
        }

        String message = getString(R.string.status_exception_error, details, status, e);

        new AlertDialog.Builder(MainJavaActivity.this)
                .setMessage(message)
                .setNeutralButton(android.R.string.ok, null)
                .show();
    }
}