package tech.dodd.tapattack

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.AchievementsClient
import com.google.android.gms.games.AuthenticationResult
import com.google.android.gms.games.LeaderboardsClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.Player
import com.google.android.gms.tasks.Task
import com.google.android.material.switchmaterial.SwitchMaterial
import tech.dodd.tapattack.databinding.ActivityMainBinding

class MainKotlinActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TapAttack" // TAG for debug logging
    }

    private lateinit var binding: ActivityMainBinding
    private var clickScore = 0 // The game score
    private var playing = false // Whether the game is being played or not
    private var day1night2 = 1
    private var isAuthenticated = false
    private var numPlays = 0 // Number of times the game is played
    private lateinit var mAchievementsClient: AchievementsClient // Client Achievement Variable
    private lateinit var mLeaderboardsClient: LeaderboardsClient // Client Leaderboard Variable
    private var signInActivityResultLauncher: ActivityResultLauncher<Intent>? = null
    private lateinit var dayNightSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        GPGSSignIn()

        signInActivityResultLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult -> }

        binding.apply{
            //Used when the Google SignIn button is pressed.
            signinButton.setOnClickListener { GPGSSignIn() }
            mainButton.setOnClickListener { game() }
            leaderboardButton.setOnClickListener { leaderboard() }
            achievementsButton.setOnClickListener { achievements() }
        }
        mAchievementsClient = PlayGames.getAchievementsClient(this)
        mLeaderboardsClient = PlayGames.getLeaderboardsClient(this)
    }

    private fun GPGSSignIn() {
        //GPGS Login / Check Logged In
        val gamesSignInClient = PlayGames.getGamesSignInClient(this)
        gamesSignInClient.signIn()
            .addOnCompleteListener { isAuthenticatedTask: Task<AuthenticationResult?>? ->
                val authenticationResult: AuthenticationResult = isAuthenticatedTask!!.getResult()!!
                if (isAuthenticatedTask.isSuccessful && authenticationResult.isAuthenticated) {
                    // Enable Play Games Services integration
                    isAuthenticated = true
                    // Set the greeting appropriately on main menu
                    PlayGames.getPlayersClient(this).currentPlayer
                        .addOnCompleteListener { mTask: Task<Player?>? ->
                            binding.nameTextView.text = mTask!!.getResult()!!.displayName
                        }
                    binding.signinLayout.visibility = View.GONE
                }
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        //Practice Day/Night Themes
        val item = menu.findItem(R.id.daynightSwitch)
        item.actionView?.let { dayNightSwitch = it.findViewById(R.id.daynightSwitch) }
        if (day1night2 == 2) {
            dayNightSwitch.isChecked = true
        }
        dayNightSwitch.setOnClickListener {
            if (day1night2 == 1) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else if (day1night2 == 2) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }
        return super.onCreateOptionsMenu(menu)
    }

    private fun leaderboard() {
        //If the Leaderboards button is pressed and the user is signed in show Leaderboards else Toast
        if (isAuthenticated) {
            mLeaderboardsClient.allLeaderboardsIntent
                .addOnSuccessListener { intent: Intent? ->
                    signInActivityResultLauncher!!.launch(
                        intent
                    )
                }
                .addOnFailureListener { e: Exception ->
                    handleException(
                        e,
                        getString(R.string.leaderboards_exception)
                    )
                }
        } else {
            Toast.makeText(this, getString(R.string.notconnectedtext), Toast.LENGTH_LONG).show()
        }
    }
    private fun achievements() {
        if (isAuthenticated) {
            mAchievementsClient.achievementsIntent
                .addOnSuccessListener { intent: Intent? ->
                    signInActivityResultLauncher!!.launch(
                        intent
                    )
                }
                .addOnFailureListener { e: Exception ->
                    handleException(
                        e,
                        getString(R.string.achievements_exception)
                    )
                }
        } else {
            Toast.makeText(this, getString(R.string.notconnectedtext), Toast.LENGTH_LONG).show()
        }
    }

    private fun game() {
        if (!playing) {
            // The first click
            playing = true
            clickScore = 0
            binding.mainButton.setText(R.string.keep_clicking)

            // Initialize CountDownTimer to 30 seconds
            object : CountDownTimer(30000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    //As each second ticks down during the game the textView is updated.
                    val timeViewText =
                        resources.getString(R.string.timeviewstring, millisUntilFinished / 1000 + 1)
                    binding.timeTextView.text = timeViewText
                }

                override fun onFinish() {
                    //When the game is finished - the time has run out.

                    //Resets the game
                    playing = false
                    binding.mainButton.setText(R.string.start_text)
                    binding.timeTextView.setText(R.string.game_over_text)

                    numPlays++ //Updates the number of times the game is played.
                    checkForAchievements(clickScore)
                }
            }.start() // Start the timer
        } else {
            // Subsequent clicks
            clickScore++
            val clickScoreText = resources.getString(R.string.clickscorestring, clickScore)
            binding.scoreTextView.text = clickScoreText
        }
    }

    override fun onResume() {
        super.onResume()
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> day1night2 = 2
            Configuration.UI_MODE_NIGHT_NO -> day1night2 = 1
        }
        Log.d(TAG, "onResume()")
    }

    private fun handleException(e: Exception, details: String) {
        var status = 0
        if (e is ApiException) {
            status = e.statusCode
        }
        val message = getString(R.string.status_exception_error, details, status, e)
        AlertDialog.Builder(this)
            .setMessage(message)
            .setNeutralButton(android.R.string.ok, null)
            .show()
    }

    // Check for achievements and unlock the appropriate ones
    private fun checkForAchievements(score: Int) {
        // Check if each condition is met; if so, unlock the corresponding achievement.
        if (score >= 200) {
            handleAchievement(
                isAuthenticated,
                getString(R.string.achievement_tapnado),
                getString(R.string.achievement_tapnado_toast_text)
            )
        } else if (score >= 150) {
            handleAchievement(
                isAuthenticated,
                getString(R.string.achievement_hailacious),
                getString(R.string.achievement_hailacious_toast_text)
            )
        } else if (score >= 100) {
            handleAchievement(
                isAuthenticated,
                getString(R.string.achievement_lightning),
                getString(R.string.achievement_lightning_toast_text)
            )
        } else if (score >= 50) {
            handleAchievement(
                isAuthenticated,
                getString(R.string.achievement_breezy),
                getString(R.string.achievement_breezy_toast_text)
            )
        }
        if (numPlays > 0) {
            MainJavaActivity.mAchievementsClient.increment(
                getString(R.string.achievement_play_a_whole_lot),
                numPlays
            )
            MainJavaActivity.mAchievementsClient.increment(
                getString(R.string.achievement_play_a_lot),
                numPlays
            )
        }
        //Change this number to only submit scores higher than x
        if (clickScore >= 30) {
            MainJavaActivity.mLeaderboardsClient.submitScore(
                getString(R.string.leaderboard_most_taps_attacked),
                clickScore.toLong()
            )
        }
    }

    private fun handleAchievement(
        isAuthenticated: Boolean,
        achievementId: String,
        toastTextResId: String?
    ) {
        if (isAuthenticated) {
            MainJavaActivity.mAchievementsClient.unlock(achievementId)
        } else {
            Toast.makeText(this, toastTextResId, Toast.LENGTH_SHORT).show()
        }
    }
}