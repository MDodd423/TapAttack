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
import androidx.databinding.DataBindingUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.*
import com.google.android.gms.tasks.Task
import com.google.android.material.switchmaterial.SwitchMaterial
import tech.dodd.tapattack.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "TapAttack" // TAG for debug logging
    }

    private lateinit var binding: ActivityMainBinding
    private var clickScore = 0 // The game score
    private var playing = false // Whether the game is being played or not
    private var day1night2 = 1
    private lateinit var mGoogleSignInClient: GoogleSignInClient // Client used to sign in with Google APIs
    private lateinit var mAchievementsClient: AchievementsClient // Client Achievement Variable
    private lateinit var mLeaderboardsClient: LeaderboardsClient // Client Leaderboard Variable
    private lateinit var mPlayersClient: PlayersClient // Client Player Variable
    private val mOutbox =
        AccomplishmentsOutbox() // achievements and scores we're pending to push to the cloud (waiting for the user to sign in, for instance)
    private var signInActivityResultLauncher: ActivityResultLauncher<Intent>? = null
    private lateinit var dayNightSwitch: SwitchMaterial

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        //binding.handlers = this

        signInActivityResultLauncher = registerForActivityResult(
            StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    onConnected(account)
                } catch (apiException: ApiException) {
                    onDisconnected()
                }
            } else if (!isSignedIn()) {
                Toast.makeText(this, "Account Not Selected or Signed Out", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        //Used when the Google SignIn button is pressed.
        binding.signinButton.setOnClickListener { startSignInIntent() }

        // Create the client used to sign in to Google services.
        mGoogleSignInClient = GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN).build()
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)

        //Practice Day/Night Themes
        val item = menu.findItem(R.id.daynightSwitch)
        dayNightSwitch = item.actionView.findViewById(R.id.daynightSwitch)
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

    fun doClick(v: View) {
        if (v.id == R.id.achievementsButton) {
            //If the Achievements button is pressed and the user is signed in show Achievements else Toast
            if (isSignedIn()) {
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
        } else if (v.id == R.id.leaderboardButton) {
            //If the Leaderboards button is pressed and the user is signed in show Leaderboards else Toast
            if (isSignedIn()) {
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
        } else if (v.id == R.id.signoutButton) {
            signOut()
        } else if (v.id == R.id.mainButton) {
            game()
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

                    //Updates the number of times the game is played.
                    mOutbox.mNumPlays++
                    // update leaderboards
                    updateLeaderboards(clickScore)
                }
            }.start() // Start the timer
        } else {
            // Subsequent clicks
            clickScore++
            val clickScoreText = resources.getString(R.string.clickscorestring, clickScore)
            binding.scoreTextView.text = clickScoreText
            // check for achievements
            checkForAchievements(clickScore)
        }
    }

    private fun startSignInIntent() {
        signInActivityResultLauncher!!.launch(mGoogleSignInClient.signInIntent)
    }

    private fun isSignedIn(): Boolean {
        return GoogleSignIn.getLastSignedInAccount(this) != null
    }

    private fun signInSilently() {
        Log.d(TAG, "signInSilently()")
        mGoogleSignInClient.silentSignIn().addOnCompleteListener(
            this
        ) { task: Task<GoogleSignInAccount> ->
            if (task.isSuccessful) {
                Log.d(TAG, "signInSilently(): success")
                onConnected(task.result)
            } else {
                Log.d(TAG, "signInSilently(): failure", task.exception)
                onDisconnected()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> day1night2 = 2
            Configuration.UI_MODE_NIGHT_NO -> day1night2 = 1
        }
        Log.d(TAG, "onResume()")

        // Since the state of the signed in user can change when the activity is not active
        // it is recommended to try and sign in silently from when the app resumes.
        signInSilently()
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

    // Check for achievements and unlock the appropriate ones @param clickScore
    private fun checkForAchievements(score: Int) {
        // Check if each condition is met; if so, unlock the corresponding achievement.
        if (score == 200) {
            mOutbox.mTapnadoAchievement = true
            achievementToast(getString(R.string.achievement_tapnado_toast_text))
            pushAccomplishments()
        }
        if (score == 150) {
            mOutbox.mHailaciousAchievement = true
            achievementToast(getString(R.string.achievement_hailacious_toast_text))
            pushAccomplishments()
        }
        if (score == 100) {
            mOutbox.mLightningAchievement = true
            achievementToast(getString(R.string.achievement_lightning_toast_text))
            pushAccomplishments()
        }
        if (score == 50) {
            mOutbox.mBreezyAchievement = true
            achievementToast(getString(R.string.achievement_breezy_toast_text))
            pushAccomplishments()
        }
    }

    private fun achievementToast(achievement: String) {
        // Only show toast if not signed in. If signed in, the standard Google Play
        // toasts will appear, so we don't need to show our own.
        if (!isSignedIn()) {
            Toast.makeText(
                this, getString(R.string.achievement) + ": " + achievement,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    //Update leaderboards with the user's score.
    private fun updateLeaderboards(score: Int) {
        if (mOutbox.mClickScore < score) {
            mOutbox.mClickScore = score
            pushAccomplishments()
        }
    }

    private fun pushAccomplishments() {
        if (!isSignedIn()) {
            // can't push to the cloud, try again later
            return
        }
        if (mOutbox.mTapnadoAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_tapnado))
            mOutbox.mTapnadoAchievement = false
        }
        if (mOutbox.mHailaciousAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_hailacious))
            mOutbox.mHailaciousAchievement = false
        }
        if (mOutbox.mLightningAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_lightning))
            mOutbox.mLightningAchievement = false
        }
        if (mOutbox.mBreezyAchievement) {
            mAchievementsClient.unlock(getString(R.string.achievement_breezy))
            mOutbox.mBreezyAchievement = false
        }
        if (mOutbox.mNumPlays > 0) {
            mAchievementsClient.increment(
                getString(R.string.achievement_play_a_whole_lot),
                mOutbox.mNumPlays
            )
            mAchievementsClient.increment(
                getString(R.string.achievement_play_a_lot),
                mOutbox.mNumPlays
            )
            mOutbox.mNumPlays = 0
        }
        //Change this number to only submit scores higher than x
        if (mOutbox.mClickScore >= 0) {
            mLeaderboardsClient.submitScore(
                getString(R.string.leaderboard_most_taps_attacked),
                mOutbox.mClickScore.toLong()
            )
            mOutbox.mClickScore = -1
        }
    }

    private fun onConnected(googleSignInAccount: GoogleSignInAccount) {
        Log.d(TAG, "onConnected(): connected to Google APIs")
        mAchievementsClient = Games.getAchievementsClient(this, googleSignInAccount)
        mLeaderboardsClient = Games.getLeaderboardsClient(this, googleSignInAccount)
        mPlayersClient = Games.getPlayersClient(this, googleSignInAccount)

        // Hide sign-in button and Show sign-out button on main menu
        binding.signinLayout.visibility = View.GONE
        binding.signoutLayout.visibility = View.VISIBLE

        // Set the greeting appropriately on main menu
        mPlayersClient.currentPlayer.addOnCompleteListener { task: Task<Player> ->
            val displayName: String = if (task.isSuccessful) {
                Objects.requireNonNull(task.result).displayName
            } else {
                val e = task.exception
                handleException(e!!, getString(R.string.players_exception))
                "???"
            }
            val nameStringText = resources.getString(R.string.namestring, displayName)
            binding.nameTextView.text = nameStringText
        }


        // if we have accomplishments to push, push them
        if (!mOutbox.isEmpty) {
            pushAccomplishments()
            Toast.makeText(
                this, getString(R.string.your_progress_will_be_uploaded),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun signOut() {
        Log.d(TAG, "signOut()")
        if (!isSignedIn()) {
            Log.w(TAG, "signOut() called, but was not signed in!")
            return
        }
        mGoogleSignInClient.signOut().addOnCompleteListener(
            this
        ) { task: Task<Void?> ->
            val successful = task.isSuccessful
            Log.d(TAG, "signOut(): " + if (successful) "success" else "failed")
            onDisconnected()
        }
    }

    private fun onDisconnected() {
        Log.d(TAG, "onDisconnected()")
        //mAchievementsClient = null
        //mLeaderboardsClient = null
        //mPlayersClient = null

        // Show sign-in button and signed-out greeting on main menu
        binding.signoutLayout.visibility = View.GONE
        binding.signinLayout.visibility = View.VISIBLE
        binding.nameTextView.text = getString(R.string.signed_out_greeting)
    }

    private class AccomplishmentsOutbox {
        var mTapnadoAchievement = false
        var mHailaciousAchievement = false
        var mLightningAchievement = false
        var mBreezyAchievement = false
        var mNumPlays = 0
        var mClickScore = -1
        val isEmpty: Boolean
            get() = !mHailaciousAchievement && !mBreezyAchievement && !mTapnadoAchievement &&
                    !mLightningAchievement && mNumPlays == 0 && mClickScore < 0
    }
}