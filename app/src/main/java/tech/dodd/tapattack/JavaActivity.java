package tech.dodd.tapattack;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.CountDownTimer;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.databinding.DataBindingUtil;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.games.AchievementsClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesClient;
import com.google.android.gms.games.LeaderboardsClient;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.tasks.Task;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Objects;

import tech.dodd.tapattack.databinding.ActivityMainBinding;

public class JavaActivity extends AppCompatActivity {
    private static final String TAG = "TapAttack"; // tag for debug logging
    private int clickScore = 0; // The game score
    private boolean playing = false; // Whether the game is being played or not
    private int day1night2 = 1;
    ActivityMainBinding activityMainBinding; // JavaActivity dataBinding
    private GoogleSignInClient mGoogleSignInClient; // Client used to sign in with Google APIs
    private AchievementsClient mAchievementsClient; // Client Achievement Variable
    private LeaderboardsClient mLeaderboardsClient; // Client Leaderboard Variable
    private PlayersClient mPlayersClient; // Client Player Variable
    private final AccomplishmentsOutbox mOutbox = new AccomplishmentsOutbox(); // achievements and scores we're pending to push to the cloud (waiting for the user to sign in, for instance)
    ActivityResultLauncher<Intent> signInActivityResultLauncher;
    SwitchMaterial dayNightSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        activityMainBinding.setHandlers(this);

        signInActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        try {
                            GoogleSignInAccount account = task.getResult(ApiException.class);
                            onConnected(account);
                        } catch (ApiException apiException) {
                            onDisconnected();
                        }
                    } else if (!(isSignedIn())) {
                        Toast.makeText(this, "Account Not Selected or Signed Out", Toast.LENGTH_SHORT).show();
                    }
                });

        //Used when the Google SignIn button is pressed.
        activityMainBinding.signinButton.setOnClickListener(view -> startSignInIntent());

        // Create the client used to sign in to Google services.
        mGoogleSignInClient = GoogleSignIn.getClient(this, new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN).build());
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        //Practice Day/Night Themes
        MenuItem item = menu.findItem(R.id.daynightSwitch);
        dayNightSwitch = item.getActionView().findViewById(R.id.daynightSwitch);

        if(day1night2 == 2){
            dayNightSwitch.setChecked(true);
        }

        dayNightSwitch.setOnClickListener(view -> {
            if(day1night2 == 1){
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            }else if (day1night2 == 2){
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    public void doClick(View v) {
        int id = v.getId();
        if (id == R.id.achievementsButton) {
            //If the Achievements button is pressed and the user is signed in show Achievements else Toast
            if (isSignedIn()) {
                mAchievementsClient.getAchievementsIntent()
                        .addOnSuccessListener(intent -> signInActivityResultLauncher.launch(intent))
                        .addOnFailureListener(e -> handleException(e, getString(R.string.achievements_exception)));
            } else {
                Toast.makeText(this, getString(R.string.notconnectedtext), Toast.LENGTH_LONG).show();
            }
        } else if (id == R.id.leaderboardButton) {
            //If the Leaderboards button is pressed and the user is signed in show Leaderboards else Toast
            if (isSignedIn()) {
                mLeaderboardsClient.getAllLeaderboardsIntent()
                        .addOnSuccessListener(intent -> signInActivityResultLauncher.launch(intent))
                        .addOnFailureListener(e -> handleException(e, getString(R.string.leaderboards_exception)));
            } else {
                Toast.makeText(this, getString(R.string.notconnectedtext), Toast.LENGTH_LONG).show();
            }
        } else if (id == R.id.signoutButton) {
            signOut();
        } else if (id == R.id.mainButton) {
            Game();
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

                    //Updates the number of times the game is played.
                    mOutbox.mNumPlays++;
                    // update leaderboards
                    updateLeaderboards(clickScore);
                }
            }.start();  // Start the timer
        } else {
            // Subsequent clicks
            clickScore++;
            String clickScoreText = getResources().getString(R.string.clickscorestring, clickScore);
            activityMainBinding.scoreTextView.setText(clickScoreText);
            // check for achievements
            checkForAchievements(clickScore);
        }
    }

    private void startSignInIntent() {
        signInActivityResultLauncher.launch(mGoogleSignInClient.getSignInIntent());
    }

    private boolean isSignedIn() {
        return GoogleSignIn.getLastSignedInAccount(this) != null;
    }

    private void signInSilently() {
        Log.d(TAG, "signInSilently()");

        mGoogleSignInClient.silentSignIn().addOnCompleteListener(this,
                task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "signInSilently(): success");
                        onConnected(task.getResult());
                    } else {
                        Log.d(TAG, "signInSilently(): failure", task.getException());
                        onDisconnected();
                    }
                });
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

        // Since the state of the signed in user can change when the activity is not active
        // it is recommended to try and sign in silently from when the app resumes.
        signInSilently();
    }

    private void handleException(Exception e, String details) {
        int status = 0;

        if (e instanceof ApiException) {
            ApiException apiException = (ApiException) e;
            status = apiException.getStatusCode();
        }

        String message = getString(R.string.status_exception_error, details, status, e);

        new AlertDialog.Builder(JavaActivity.this)
                .setMessage(message)
                .setNeutralButton(android.R.string.ok, null)
                .show();
    }

    // Check for achievements and unlock the appropriate ones @param clickScore
    private void checkForAchievements(int score) {
        // Check if each condition is met; if so, unlock the corresponding achievement.
        if (score == 200) {
            mOutbox.mTapnadoAchievement = true;
            achievementToast(getString(R.string.achievement_tapnado_toast_text));
            pushAccomplishments();
        }
        if (score == 150) {
            mOutbox.mHailaciousAchievement = true;
            achievementToast(getString(R.string.achievement_hailacious_toast_text));
            pushAccomplishments();
        }
        if (score == 100) {
            mOutbox.mLightningAchievement = true;
            achievementToast(getString(R.string.achievement_lightning_toast_text));
            pushAccomplishments();
        }
        if (score == 50) {
            mOutbox.mBreezyAchievement = true;
            achievementToast(getString(R.string.achievement_breezy_toast_text));
            pushAccomplishments();
        }
    }

    private void achievementToast(String achievement) {
        // Only show toast if not signed in. If signed in, the standard Google Play
        // toasts will appear, so we don't need to show our own.
        if (!isSignedIn()) {
            Toast.makeText(this, getString(R.string.achievement) + ": " + achievement,
                    Toast.LENGTH_LONG).show();
        }
    }

    //Update leaderboards with the user's score.
    private void updateLeaderboards(int score) {
        if (mOutbox.mClickScore < score) {
            mOutbox.mClickScore = score;
            pushAccomplishments();
        }
    }

    private void pushAccomplishments() {
        if (!isSignedIn()) {
            // can't push to the cloud, try again later
            return;
        }
        if (mOutbox.mTapnadoAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_tapnado));
            mOutbox.mTapnadoAchievement = false;
        }
        if (mOutbox.mHailaciousAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_hailacious));
            mOutbox.mHailaciousAchievement = false;
        }
        if (mOutbox.mLightningAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_lightning));
            mOutbox.mLightningAchievement = false;
        }
        if (mOutbox.mBreezyAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_breezy));
            mOutbox.mBreezyAchievement = false;
        }
        if (mOutbox.mNumPlays > 0) {
            mAchievementsClient.increment(getString(R.string.achievement_play_a_whole_lot),
                    mOutbox.mNumPlays);
            mAchievementsClient.increment(getString(R.string.achievement_play_a_lot),
                    mOutbox.mNumPlays);
            mOutbox.mNumPlays = 0;
        }
        //Change this number to only submit scores higher than x
        if (mOutbox.mClickScore >= 0) {
            mLeaderboardsClient.submitScore(getString(R.string.leaderboard_most_taps_attacked),
                    mOutbox.mClickScore);
            mOutbox.mClickScore = -1;
        }
    }

    private void onConnected(GoogleSignInAccount googleSignInAccount) {
        Log.d(TAG, "onConnected(): connected to Google APIs");

        mAchievementsClient = Games.getAchievementsClient(this, googleSignInAccount);
        mLeaderboardsClient = Games.getLeaderboardsClient(this, googleSignInAccount);
        mPlayersClient = Games.getPlayersClient(this, googleSignInAccount);

        // Hide sign-in button and Show sign-out button on main menu
        activityMainBinding.signinLayout.setVisibility(View.GONE);
        activityMainBinding.signoutLayout.setVisibility(View.VISIBLE);

        // Set the greeting appropriately on main menu
        mPlayersClient.getCurrentPlayer().addOnCompleteListener(task -> {
            String displayName;
            if (task.isSuccessful()) {
                displayName = Objects.requireNonNull(task.getResult()).getDisplayName();
            } else {
                Exception e = task.getException();
                handleException(e, getString(R.string.players_exception));
                displayName = "???";
            }
            String nameStringText = getResources().getString(R.string.namestring, displayName);
            activityMainBinding.nameTextView.setText(nameStringText);
        });


        // if we have accomplishments to push, push them
        if (!mOutbox.isEmpty()) {
            pushAccomplishments();
            Toast.makeText(this, getString(R.string.your_progress_will_be_uploaded),
                    Toast.LENGTH_LONG).show();
        }

        //Added this to show Google Play Games Achievement Notifications - If statement because it can be null if user signs out.
        if (isSignedIn()) {
            GamesClient gamesClient = Games.getGamesClient(JavaActivity.this, Objects.requireNonNull(GoogleSignIn.getLastSignedInAccount(this)));
            gamesClient.setViewForPopups(findViewById(R.id.gps_popup));
        }
    }

    private void signOut() {
        Log.d(TAG, "signOut()");

        if (!isSignedIn()) {
            Log.w(TAG, "signOut() called, but was not signed in!");
            return;
        }

        mGoogleSignInClient.signOut().addOnCompleteListener(this,
                task -> {
                    boolean successful = task.isSuccessful();
                    Log.d(TAG, "signOut(): " + (successful ? "success" : "failed"));

                    onDisconnected();
                });
    }

    private void onDisconnected() {
        Log.d(TAG, "onDisconnected()");

        mAchievementsClient = null;
        mLeaderboardsClient = null;
        mPlayersClient = null;

        // Show sign-in button and signed-out greeting on main menu
        activityMainBinding.signoutLayout.setVisibility(View.GONE);
        activityMainBinding.signinLayout.setVisibility(View.VISIBLE);
        activityMainBinding.nameTextView.setText(getString(R.string.signed_out_greeting));
    }

    private static class AccomplishmentsOutbox {
        boolean mTapnadoAchievement = false;
        boolean mHailaciousAchievement = false;
        boolean mLightningAchievement = false;
        boolean mBreezyAchievement = false;
        int mNumPlays = 0;
        int mClickScore = -1;

        boolean isEmpty() {
            return !mHailaciousAchievement && !mBreezyAchievement && !mTapnadoAchievement &&
                    !mLightningAchievement && mNumPlays == 0 && mClickScore < 0;
        }
    }
}