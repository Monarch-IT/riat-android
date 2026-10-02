package uz.riat.tdiu.ui.departments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import uz.riat.tdiu.R
import uz.riat.tdiu.data.repository.FacultyRepository

class DepartmentsFragment : Fragment() {

    private lateinit var adapter: FacultyAdapter
    private lateinit var repository: FacultyRepository
    private var showingLeadership = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_departments, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = FacultyRepository(requireContext())

        val rv = view.findViewById<RecyclerView>(R.id.rvFaculty)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = FacultyAdapter(emptyList())
        rv.adapter = adapter

        val tabLeadership = view.findViewById<TextView>(R.id.tabLeadership)
        val tabDepartments = view.findViewById<TextView>(R.id.tabDepartments)

        tabLeadership.setOnClickListener {
            showingLeadership = true
            updateTabState(tabLeadership, tabDepartments)
            loadData()
        }
        tabDepartments.setOnClickListener {
            showingLeadership = false
            updateTabState(tabDepartments, tabLeadership)
            loadData()
        }

        loadData()
    }

    private fun updateTabState(active: TextView, inactive: TextView) {
        active.setBackgroundResource(R.drawable.bg_pill_primary_active)
        active.setTextColor(0xFFFFFFFF.toInt())
        inactive.setBackgroundResource(R.drawable.bg_pill_primary)
        inactive.setTextColor(0xFF94A3B8.toInt())
    }

    private fun loadData() {
        lifecycleScope.launch {
            val data = repository.getFacultyData()
            val items = if (showingLeadership) {
                data.leaders.map { FacultyListItem.LeaderItem(it) }
            } else {
                data.departments.map { FacultyListItem.DepartmentItem(it) }
            }
            adapter.updateData(items)
        }
    }
}
