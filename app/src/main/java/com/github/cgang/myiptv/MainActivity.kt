package com.github.cgang.myiptv

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.preference.PreferenceManager

/**
 * The base activity for playback.
 */
open class MainActivity : AppCompatActivity() {
    lateinit var preferences: SharedPreferences
    private lateinit var changeSettings: ActivityResultLauncher<Intent>
    private val viewModel: PlaylistViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate()")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        supportFragmentManager
            .beginTransaction()
            .add(R.id.playlist_fragment, PlaylistFragment(viewModel))
            .commit()

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        changeSettings =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                onPreferenceChanged()
            }

        viewModel.getPlaylist().observe(this) {
            updatePlaylist(it)
        }

        getPrefString(PLAYLIST_URL, R.string.default_playlist_url)?.let {
            Downloader.getPlaylist(it)
        }
        getPrefString(EPG_URL, R.string.default_epg_url)?.let {
            Downloader.getEPG(it)
        }
    }

    private fun getPrefString(key: String, resId: Int): String? {
        return preferences.getString(key, resources.getString(resId))
    }

    private fun hideSystemUI() {
        val decorView = window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    private fun showSystemUI() {
        val decorView = window.decorView
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d(TAG, "onTouchEvent($event)")
        val controlsLayout = findViewById<FrameLayout>(R.id.playlist_fragment)
        if (controlsLayout.visibility == View.VISIBLE) {
            return if (event.action == MotionEvent.ACTION_UP) {
                hideControls()
                true
            } else {
                super.onTouchEvent(event)
            }
        }
        return if (event.action == MotionEvent.ACTION_UP) {
            viewModel.setGroup("") // disable group
            controlsLayout.visibility = View.VISIBLE
            showControls()
            true
        } else {
            super.onTouchEvent(event)
        }
    }


    private fun showControls() {
        val frag = supportFragmentManager.findFragmentById(R.id.playlist_fragment)
        if (frag is PlaylistFragment) {
            supportFragmentManager.beginTransaction()
                .show(frag)
                .commit()
            frag.listView.requestFocus()
        }
    }

    fun hideControls() {
        Log.d(TAG, "Trying to hide controls")
        val frag = supportFragmentManager.findFragmentById(R.id.playlist_fragment)
        val layout = findViewById<FrameLayout>(R.id.playlist_fragment)
        if (layout.visibility == View.VISIBLE && frag != null) {
            supportFragmentManager.beginTransaction()
                .hide(frag)
                .commit()
            layout.visibility = View.GONE
            hideSystemUI()
        }
    }

    private fun showConfig() {
        val intent = Intent(this, SettingsActivity::class.java)
        changeSettings.launch(intent)
    }

    private fun onPreferenceChanged() {
        getPrefString(PLAYLIST_URL, R.string.default_playlist_url)
            ?.let {
                Downloader.getPlaylist(it)
            }
        getPrefString(EPG_URL, R.string.default_epg_url)?.let {
            Downloader.getEPG(it)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        Log.d(TAG, "onWindowFocusChanged($hasFocus)")
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        } else {
            showSystemUI()
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d(TAG, "onKeyUp($keyCode)")
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            showConfig()
            return true
        }

        val controlsLayout = findViewById<FrameLayout>(R.id.playlist_fragment)
        if (controlsLayout.visibility == View.VISIBLE) {
            return when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    hideControls()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    viewModel.switchGroup(-1)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    viewModel.switchGroup(1)
                    return true
                }

                else -> super.onKeyUp(keyCode, event)
            }

        }

        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                switchChannel(-1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                switchChannel(1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER -> {
                viewModel.resetGroup()
                controlsLayout.visibility = View.VISIBLE
                showControls()
                return true
            }

            else -> super.onKeyUp(keyCode, event)
        }
    }

    private fun updatePlaylist(playlist: Playlist) {
        val frag = supportFragmentManager.findFragmentById(R.id.playlist_fragment)
        if (frag is PlaylistFragment) {
            frag.setPlaylist(playlist)
        }
        val channel = playlist.default ?: return
        playDefault(channel)
    }

    private fun playDefault(channel: Channel) {
        val frag = supportFragmentManager.findFragmentById(R.id.playback_fragment_root)
        if (frag is PlaybackFragment) {
            frag.playDefault(channel)
            hideControls()
        }
    }

    fun play(channel: Channel) {
        val frag = supportFragmentManager.findFragmentById(R.id.playback_fragment_root)
        if (frag is PlaybackFragment) {
            frag.switchTo(channel)
            hideControls()
        }
    }

    private fun switchChannel(step: Int) {
        var current: String? = null
        val playback = supportFragmentManager.findFragmentById(R.id.playback_fragment_root)
        if (playback is PlaybackFragment) {
            current = playback.lastUrl
        }

        if (current == null) {
            Log.d(TAG, "current playing URL not found")
            return
        }

        val frag = supportFragmentManager.findFragmentById(R.id.playlist_fragment)
        if (frag is PlaylistFragment && playback is PlaybackFragment) {
            frag.switchChannel(current, step)?.let {
                playback.switchTo(it)
            }
        }
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
    }
}

