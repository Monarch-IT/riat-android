package uz.riat.tdiu.ui.departments

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import uz.riat.tdiu.R
import uz.riat.tdiu.data.model.Department
import uz.riat.tdiu.data.model.Leader
import uz.riat.tdiu.util.ImageLoader

sealed class FacultyListItem {
    data class LeaderItem(val leader: Leader) : FacultyListItem()
    data class DepartmentItem(val department: Department) : FacultyListItem()
}

class FacultyAdapter(private var items: List<FacultyListItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_LEADER = 0
        private const val TYPE_DEPARTMENT = 1
    }

    inner class LeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivPhoto: ImageView = itemView.findViewById(R.id.ivLeaderPhoto)
        val tvName: TextView = itemView.findViewById(R.id.tvLeaderName)
        val tvRole: TextView = itemView.findViewById(R.id.tvLeaderRole)
        val tvDegree: TextView = itemView.findViewById(R.id.tvLeaderDegree)
        val tvPhone: TextView = itemView.findViewById(R.id.tvLeaderPhone)
        val tvReception: TextView = itemView.findViewById(R.id.tvLeaderReception)
    }

    inner class DepartmentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvDeptName)
        val tvDesc: TextView = itemView.findViewById(R.id.tvDeptDescription)
        val programsContainer: LinearLayout = itemView.findViewById(R.id.programsContainer)
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is FacultyListItem.LeaderItem -> TYPE_LEADER
        is FacultyListItem.DepartmentItem -> TYPE_DEPARTMENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LEADER -> LeaderViewHolder(inflater.inflate(R.layout.item_leader_card, parent, false))
            else -> DepartmentViewHolder(inflater.inflate(R.layout.item_department_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is FacultyListItem.LeaderItem -> {
                val l = item.leader
                val lh = holder as LeaderViewHolder
                lh.tvName.text = l.fullName
                lh.tvRole.text = l.role
                lh.tvDegree.text = l.degree
                lh.tvPhone.text = l.phone
                lh.tvReception.text = l.reception
                val photoUrl = if (l.photo.startsWith("http")) l.photo
                    else "https://tsue-digital-economy.web.app/${l.photo}"
                ImageLoader.load(lh.ivPhoto, photoUrl)
            }
            is FacultyListItem.DepartmentItem -> {
                val d = item.department
                val dh = holder as DepartmentViewHolder
                dh.tvName.text = d.name
                dh.tvDesc.text = d.description
                dh.programsContainer.removeAllViews()
                d.programs.forEach { prog ->
                    val tv = TextView(holder.itemView.context).apply {
                        text = prog
                        setTextColor(0xFF94A3B8.toInt())
                        textSize = 12f
                        setPadding(0, 2, 0, 2)
                    }
                    dh.programsContainer.addView(tv)
                }
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<FacultyListItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
