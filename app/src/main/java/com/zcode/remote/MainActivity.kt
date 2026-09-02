package com.zcode.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var store: ConnectionsStore
    private lateinit var adapter: ConnectionAdapter
    private lateinit var emptyView: TextView
    private var snackbarHost: View? = null

    private val clipboard: ClipboardManager
        get() = getSystemService(ClipboardManager::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConnectionsStore(this)

        // 恢复了上次的连接页：App 重启（含从最近任务划掉后重新进入）直接回到工作画面，
        // 而不是停在连接列表。用户主动退出连接页时此记录已被清除。
        val lastId = store.lastConnectionId()
        if (lastId != null && store.get(lastId) != null) {
            startActivity(
                Intent(this, ConnectActivity::class.java)
                    .putExtra(ConnectActivity.EXTRA_ID, lastId)
            )
        }

        setContentView(R.layout.activity_main)
        InsetsHelper.apply(findViewById(R.id.root))

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = getString(R.string.app_name)

        emptyView = findViewById(R.id.empty_view)
        snackbarHost = emptyView

        val list = findViewById<RecyclerView>(R.id.conn_list)
        adapter = ConnectionAdapter(
            onClick = { conn ->
                startActivity(
                    Intent(this, ConnectActivity::class.java)
                        .putExtra(ConnectActivity.EXTRA_ID, conn.id)
                )
            },
            onLongClick = { conn, anchor -> showItemMenu(conn, anchor) }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fab_add).setOnClickListener {
            startActivity(Intent(this, AddLinkActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = store.list()
        adapter.submit(items)
        emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showItemMenu(conn: Connection, anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, getString(R.string.menu_copy_link))
            menu.add(0, 2, 0, getString(R.string.menu_delete))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        clipboard.setPrimaryClip(ClipData.newPlainText("zcode-link", conn.url))
                        snackbarHost?.let { Snackbar.make(it, R.string.copied, Snackbar.LENGTH_SHORT).show() }
                        true
                    }
                    2 -> {
                        confirmDelete(conn)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun confirmDelete(conn: Connection) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_delete)
            .setMessage(R.string.confirm_delete)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.remove(conn.id)
                refresh()
                snackbarHost?.let {
                    Snackbar.make(
                        it,
                        getString(R.string.deleted, conn.name),
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }
}

private class ConnectionAdapter(
    private val onClick: (Connection) -> Unit,
    private val onLongClick: (Connection, View) -> Unit
) : RecyclerView.Adapter<ConnectionAdapter.VH>() {

    private val items = mutableListOf<Connection>()

    fun submit(list: List<Connection>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.item_title)
        val subtitle: TextView = view.findViewById(R.id.item_subtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_connection, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.name
        holder.subtitle.text = item.url
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener {
            onLongClick(item, holder.itemView)
            true
        }
    }
}