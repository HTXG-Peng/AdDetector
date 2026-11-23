package com.example.addetector

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class JumpAdapter(
    private val activity: Activity,
    private val items: List<JumpEvent>
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(activity)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): JumpEvent = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_jump, parent, false)
        val holder = view.tag as? Holder ?: Holder(view).also { view.tag = it }
        val item = items[position]

        holder.time.text = JumpEvent.formatClock(item.time)
        holder.route.text = activity.getString(
            R.string.route_format,
            AppLabels.of(activity, item.from),
            AppLabels.of(activity, item.to)
        )

        if (item.auto) {
            holder.meta.text = activity.getString(
                R.string.auto_badge,
                JumpEvent.formatDwell(item.dwellMs)
            )
            holder.meta.setTextColor(activity.getColor(R.color.auto_badge))
        } else {
            holder.meta.text = if (item.from == null) {
                activity.getString(R.string.first_seen)
            } else {
                activity.getString(
                    R.string.normal_badge,
                    JumpEvent.formatDwell(item.dwellMs)
                )
            }
            holder.meta.setTextColor(activity.getColor(R.color.text_secondary))
        }

        return view
    }

    private class Holder(view: View) {
        val time: TextView = view.findViewById(R.id.time)
        val route: TextView = view.findViewById(R.id.route)
        val meta: TextView = view.findViewById(R.id.meta)
    }
}
