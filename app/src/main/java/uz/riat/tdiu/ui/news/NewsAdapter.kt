package uz.riat.tdiu.ui.news

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import uz.riat.tdiu.R
import uz.riat.tdiu.data.model.NewsItem
import uz.riat.tdiu.util.ImageLoader

class NewsAdapter(
    private var items: List<NewsItem>,
    private val onItemClick: (NewsItem) -> Unit
) : RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivNewsThumbnail)
        val tvCategory: TextView = itemView.findViewById(R.id.tvNewsCategory)
        val tvTitle: TextView = itemView.findViewById(R.id.tvNewsTitle)
        val tvDate: TextView = itemView.findViewById(R.id.tvNewsDate)
        val tvViews: TextView = itemView.findViewById(R.id.tvNewsViews)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val news = items[position]
        holder.tvCategory.text = news.category
        holder.tvTitle.text = news.title
        holder.tvDate.text = news.publishDate
        holder.tvViews.text = "${news.views} просмотров"
        ImageLoader.load(holder.ivThumbnail, news.thumbnailUrl)
        holder.itemView.setOnClickListener { onItemClick(news) }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<NewsItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
