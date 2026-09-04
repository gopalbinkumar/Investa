package com.example.investa.ui.assets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.investa.R
import com.example.investa.data.entity.AssetEntity
import com.example.investa.model.Asset
import com.example.investa.ui.common.bindAsset
import com.example.investa.utils.toIdrDisplay
import com.example.investa.utils.toUiAsset
import java.util.Locale

internal val assetPagerCategories = listOf(
    "All",
    "Crypto",
    "ID Stocks",
    "US Stocks",
    "Bonds",
    "Mutual Fund",
    "Gold"
)

internal class AssetCategoryPagerAdapter(
    private val onAssetClick: (Asset) -> Unit
) : RecyclerView.Adapter<AssetCategoryPagerAdapter.PageViewHolder>() {
    private var assets: List<AssetEntity> = emptyList()
    private var usdExchangeRate = 16500.0
    private var hasData = false

    fun updateAssets(
        assets: List<AssetEntity>,
        usdExchangeRate: Double,
        notifyAdapter: Boolean = true
    ) {
        if (hasData && this.assets == assets && this.usdExchangeRate == usdExchangeRate) return
        this.assets = assets
        this.usdExchangeRate = usdExchangeRate
        hasData = true
        if (notifyAdapter) notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val page = LayoutInflater.from(parent.context)
            .inflate(R.layout.asset_category_page, parent, false)
        return PageViewHolder(page, onAssetClick)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val category = assetPagerCategories[position]
        val filteredAssets = assets
            .filter { asset -> category == "All" || asset.category == category }
            .sortedBy { it.symbol.trim().uppercase(Locale.ROOT) }
        holder.updateList(filteredAssets, usdExchangeRate)
    }

    override fun getItemCount(): Int = assetPagerCategories.size

    class PageViewHolder(
        page: View,
        onAssetClick: (Asset) -> Unit
    ) : RecyclerView.ViewHolder(page) {
        val list: RecyclerView = page.findViewById(R.id.asset_page_list)
        private val listAdapter = AssetListAdapter(onAssetClick)

        init {
            list.layoutManager = LinearLayoutManager(list.context)
            list.adapter = listAdapter
        }

        fun updateList(assets: List<AssetEntity>, usdExchangeRate: Double) {
            listAdapter.update(assets, usdExchangeRate)
        }
    }

    private class AssetListAdapter(
        private val onAssetClick: (Asset) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var assets: List<AssetEntity> = emptyList()
        private var usdExchangeRate = 16500.0
        private var hasData = false

        fun update(assets: List<AssetEntity>, usdExchangeRate: Double) {
            if (hasData && this.assets == assets && this.usdExchangeRate == usdExchangeRate) return
            this.assets = assets
            this.usdExchangeRate = usdExchangeRate
            hasData = true
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = if (assets.isEmpty()) 1 else assets.size

        override fun getItemViewType(position: Int): Int = if (assets.isEmpty()) EMPTY_VIEW else ASSET_VIEW

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val layout = if (viewType == EMPTY_VIEW) R.layout.view_empty_state else R.layout.asset_item
            val view = LayoutInflater.from(parent.context).inflate(layout, parent, false)
            return if (viewType == EMPTY_VIEW) {
                EmptyViewHolder(view)
            } else {
                AssetViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder !is AssetViewHolder) return
            val entity = assets[position]
            val nativeAsset = entity.toUiAsset(holder.itemView.context)
            bindAsset(holder.itemView, nativeAsset.toIdrDisplay(usdExchangeRate), compact = false)
            holder.itemView.setOnClickListener { onAssetClick(nativeAsset) }
        }

        private class AssetViewHolder(view: View) : RecyclerView.ViewHolder(view)

        private class EmptyViewHolder(view: View) : RecyclerView.ViewHolder(view)

        private companion object {
            const val ASSET_VIEW = 0
            const val EMPTY_VIEW = 1
        }
    }
}
