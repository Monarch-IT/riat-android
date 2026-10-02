package uz.riat.tdiu.ui.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import uz.riat.tdiu.R
import uz.riat.tdiu.data.model.NewsItem
import uz.riat.tdiu.data.repository.FacultyRepository

class NewsFragment : Fragment() {

    private lateinit var adapter: NewsAdapter
    private lateinit var repository: FacultyRepository
    private var allNews = listOf<NewsItem>()
    private var selectedCategory = "Все"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_news, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = FacultyRepository(requireContext())

        val rv = view.findViewById<RecyclerView>(R.id.rvNews)
        rv.layoutManager = LinearLayoutManager(requireContext())
        adapter = NewsAdapter(emptyList()) { }
        rv.adapter = adapter

        val progress = view.findViewById<ProgressBar>(R.id.newsProgress)
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            allNews = repository.getNews()
            progress.visibility = View.GONE
            buildFilterChips(view)
            applyFilter(view)
        }
    }

    private fun buildFilterChips(view: View) {
        val container = view.findViewById<LinearLayout>(R.id.filterChipsContainer)
        container.removeAllViews()
        val categories = listOf("Все") + allNews.map { it.category }.distinct()
        categories.forEach { cat ->
            val tv = TextView(requireContext()).apply {
                text = cat
                textSize = 13f
                val isActive = cat == selectedCategory
                setTextColor(if (isActive) 0xFFFFFFFF.toInt() else 0xFF94A3B8.toInt())
                setBackgroundResource(
                    if (isActive) R.drawable.bg_pill_primary_active
                    else R.drawable.bg_pill_primary
                )
                setPadding(32, 16, 32, 16)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 8, 0) }
                layoutParams = lp
                setOnClickListener {
                    selectedCategory = cat
                    buildFilterChips(view)
                    applyFilter(view)
                }
            }
            container.addView(tv)
        }
    }

    private fun applyFilter(view: View) {
        val filtered = if (selectedCategory == "Все") allNews
            else allNews.filter { it.category == selectedCategory }
        adapter.updateData(filtered)
    }
}
