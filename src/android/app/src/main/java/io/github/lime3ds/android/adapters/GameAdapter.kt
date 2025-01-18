// Copyright Citra Emulator Project / Lime3DS Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

package io.github.lime3ds.android.adapters

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.SystemClock
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.lime3ds.android.HomeNavigationDirections
import io.github.lime3ds.android.LimeApplication
import io.github.lime3ds.android.R
import io.github.lime3ds.android.databinding.CardGameBigBinding
import io.github.lime3ds.android.databinding.CardGameBinding
import io.github.lime3ds.android.features.cheats.ui.CheatsFragmentDirections
import io.github.lime3ds.android.model.Game
import io.github.lime3ds.android.utils.GameIconUtils
import io.github.lime3ds.android.viewmodel.GamesViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GameAdapter(private val activity: AppCompatActivity, private val inflater: LayoutInflater) :
    ListAdapter<Game, GameAdapter.GameViewHolder>(AsyncDifferConfig.Builder(DiffCallback()).build()),
    View.OnClickListener, View.OnLongClickListener {

    companion object {
        const val VIEW_TYPE_LIST = 0
        const val VIEW_TYPE_GRID = 1
    }

    private var viewType = VIEW_TYPE_LIST

    fun setViewType(type: Int) {
        viewType = type
        notifyDataSetChanged()
    }

    fun getViewType(): Int = viewType

    private var lastClickTime = 0L

    override fun getItemViewType(position: Int): Int = viewType

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_LIST -> CardGameBinding.inflate(inflater, parent, false)
            VIEW_TYPE_GRID -> CardGameBigBinding.inflate(inflater, parent, false)
            else -> throw IllegalArgumentException("Invalid view type")
        }
        return GameViewHolder(binding, viewType)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(currentList[position])
    }

    override fun getItemCount(): Int = currentList.size

    /**
     * Launches the game that was clicked on.
     *
     * @param view The card representing the game the user wants to play.
     */
    override fun onClick(view: View) {
        // Double-click prevention, using threshold of 1000 ms
        if (SystemClock.elapsedRealtime() - lastClickTime < 1000) {
            return
        }
        lastClickTime = SystemClock.elapsedRealtime()

        val holder = view.tag as GameViewHolder
        gameExists(holder)

        val preferences =
            PreferenceManager.getDefaultSharedPreferences(LimeApplication.appContext)
        preferences.edit()
            .putLong(
                holder.game.keyLastPlayedTime,
                System.currentTimeMillis()
            )
            .apply()

        val action = HomeNavigationDirections.actionGlobalEmulationActivity(holder.game)
        view.findNavController().navigate(action)
    }

    /**
     * Opens the about game dialog for the game that was clicked on.
     *
     * @param view The view representing the game the user wants to play.
     */
    override fun onLongClick(view: View): Boolean {
        val context = view.context
        val holder = view.tag as GameViewHolder
        gameExists(holder)

        if (holder.game.titleId == 0L) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.properties)
                .setMessage(R.string.properties_not_loaded)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } else {
            showAboutGameDialog(context, holder.game, holder, view)
        }
        return true
    }

    // Triggers a library refresh if the user clicks on stale data
    private fun gameExists(holder: GameViewHolder): Boolean {
        if (holder.game.isInstalled) {
            return true
        }

        val gameExists = DocumentFile.fromSingleUri(
            LimeApplication.appContext,
            Uri.parse(holder.game.path)
        )?.exists() == true
        return if (!gameExists) {
            Toast.makeText(
                LimeApplication.appContext,
                R.string.loader_error_file_not_found,
                Toast.LENGTH_LONG
            ).show()

            ViewModelProvider(activity)[GamesViewModel::class.java].reloadGames(true)
            false
        } else {
            true
        }
    }

    inner class GameViewHolder(
        private val binding: ViewBinding,
        private val viewType: Int
    ) : RecyclerView.ViewHolder(binding.root) {
        lateinit var game: Game

        init {
            binding.root.tag = this
            binding.root.setOnClickListener(this@GameAdapter)
            binding.root.setOnLongClickListener(this@GameAdapter)
        }

        fun bind(game: Game) {
            this.game = game

            when (viewType) {
                VIEW_TYPE_LIST -> bindListView(binding as CardGameBinding, game)
                VIEW_TYPE_GRID -> bindGridView(binding as CardGameBigBinding, game)
            }
        }

        private fun bindListView(binding: CardGameBinding, game: Game) {
            binding.textGameTitle.text = game.title
            binding.textCompany.text = game.company
            GameIconUtils.loadGameIcon(activity, game, binding.imageGameScreen)
            binding.textGameTitle.visibility = if (game.title.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
            binding.textCompany.visibility = if (game.company.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }

            binding.textGameRegion.text = game.regions

        val backgroundColorId =
            if (
                isValidGame(game.filename.substring(game.filename.lastIndexOf(".") + 1).lowercase())
            ) {
                R.attr.colorSurface
            } else {
                R.attr.colorErrorContainer
            }
        binding.cardContents.setBackgroundColor(
            MaterialColors.getColor(
                binding.cardContents,
                backgroundColorId
            )
        )

        binding.textGameTitle.postDelayed(
            {
                binding.textGameTitle.ellipsize = TextUtils.TruncateAt.MARQUEE
                binding.textGameTitle.isSelected = true

                binding.textCompany.ellipsize = TextUtils.TruncateAt.MARQUEE
                binding.textCompany.isSelected = true

                binding.textGameRegion.ellipsize = TextUtils.TruncateAt.MARQUEE
                binding.textGameRegion.isSelected = true
            },
            3000
        )
    }

        private fun bindGridView(binding: CardGameBigBinding, game: Game) {
            binding.textGameTitle.text = game.title
            binding.textGameTitle.visibility = if (game.title.isEmpty()) {
                View.GONE
            } else {
                View.VISIBLE
            }
            GameIconUtils.loadGameIcon(activity, game, binding.imageGameScreen)
            binding.textGameTitle.postDelayed({
                binding.textGameTitle.ellipsize = TextUtils.TruncateAt.MARQUEE
                binding.textGameTitle.isSelected = true
            }, 3000)
        }
    }

    private fun showAboutGameDialog(context: Context, game: Game, holder: GameViewHolder, view: View) {
        val bottomSheetView = inflater.inflate(R.layout.dialog_about_game, null)

        val bottomSheetDialog = BottomSheetDialog(context)
        bottomSheetDialog.setContentView(bottomSheetView)

        bottomSheetView.findViewById<TextView>(R.id.about_game_title).text = game.title
        bottomSheetView.findViewById<TextView>(R.id.about_game_company).text = game.company
        bottomSheetView.findViewById<TextView>(R.id.about_game_region).text = game.regions
        bottomSheetView.findViewById<TextView>(R.id.about_game_id).text = "ID: " + String.format("%016X", game.titleId)
        bottomSheetView.findViewById<TextView>(R.id.about_game_filename).text = "File: " + game.filename
        GameIconUtils.loadGameIcon(activity, game, bottomSheetView.findViewById(R.id.game_icon))

        bottomSheetView.findViewById<MaterialButton>(R.id.about_game_play).setOnClickListener {
            val action = HomeNavigationDirections.actionGlobalEmulationActivity(holder.game)
            view.findNavController().navigate(action)
        }

        bottomSheetView.findViewById<MaterialButton>(R.id.game_shortcut).setOnClickListener {
            val shortcutManager = activity.getSystemService(ShortcutManager::class.java)

            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = (bottomSheetView.findViewById<ImageView>(R.id.game_icon).drawable as BitmapDrawable).bitmap
                val icon = Icon.createWithBitmap(bitmap)

                val shortcut = ShortcutInfo.Builder(context, game.title)
                    .setShortLabel(game.title)
                    .setIcon(icon)
                    .setIntent(game.launchIntent.apply {
                        putExtra("launched_from_shortcut", true)
                    })
                    .build()
                shortcutManager.requestPinShortcut(shortcut, null)
            }
        }

        bottomSheetView.findViewById<MaterialButton>(R.id.cheats).setOnClickListener {
            val action = CheatsFragmentDirections.actionGlobalCheatsFragment(holder.game.titleId)
            view.findNavController().navigate(action)
            bottomSheetDialog.dismiss()
        }

        val bottomSheetBehavior = bottomSheetDialog.getBehavior()
        bottomSheetBehavior.skipCollapsed = true
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED

        bottomSheetDialog.show()
    }

    private fun isValidGame(extension: String): Boolean {
        return Game.badExtensions.stream()
            .noneMatch { extension == it.lowercase() }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Game>() {
        override fun areItemsTheSame(oldItem: Game, newItem: Game): Boolean {
            return oldItem.titleId == newItem.titleId
        }

        override fun areContentsTheSame(oldItem: Game, newItem: Game): Boolean {
            return oldItem == newItem
        }
    }
}
