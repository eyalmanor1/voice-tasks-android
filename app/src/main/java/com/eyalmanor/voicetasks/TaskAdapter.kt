package com.eyalmanor.voicetasks

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TaskAdapter(
    private val onToggleDone: (TaskEntity) -> Unit,
    private val onPlay: (TaskEntity) -> Unit,
    private val onShare: (TaskEntity) -> Unit,
    private val onDelete: (TaskEntity) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private val items = mutableListOf<TaskEntity>()

    fun submit(list: List<TaskEntity>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.meta.text = meta(item)
        holder.transcript.text = if (item.transcript.isBlank()) "—" else item.transcript

        holder.doneBtn.text = if (item.isDone) "החזר" else "בוצע"
        holder.doneBtn.setOnClickListener { onToggleDone(item) }
        holder.playBtn.setOnClickListener { onPlay(item) }
        holder.shareBtn.setOnClickListener { onShare(item) }
        holder.deleteBtn.setOnClickListener { onDelete(item) }
    }

    private fun meta(item: TaskEntity): String {
        val d = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("he","IL")).format(Date(item.createdAt))
        val s = (item.durationMs / 1000).toInt()
        val dur = String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
        return "$d • $dur • " + (if (item.isDone) "בוצע" else "פתוח")
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.taskTitle)
        val meta: TextView = v.findViewById(R.id.taskMeta)
        val transcript: TextView = v.findViewById(R.id.taskTranscript)
        val doneBtn: Button = v.findViewById(R.id.doneBtn)
        val playBtn: Button = v.findViewById(R.id.playBtn)
        val shareBtn: Button = v.findViewById(R.id.shareBtn)
        val deleteBtn: Button = v.findViewById(R.id.deleteBtn)
    }
}
