package com.example.investa.ui.assets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.recyclerview.widget.RecyclerView
import com.example.investa.R
import com.example.investa.data.entity.AssetEntity
import com.example.investa.model.Asset
import com.example.investa.ui.common.addAssetRow
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

    fun updateAssets(assets: List<AssetEntity>, usdExchangeRate: Double) {
        this.assets = assets
        this.usdExchangeRate = usdExchangeRate
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val page = LayoutInflater.from(parent.context)
            .inflate(R.layout.asset_category_page, parent, false)
        return PageViewHolder(page)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val category = assetPagerCategories[position]
        val filteredAssets = assets
            .filter { asset -> category == "All" || asset.category == category }
            .sortedBy { it.symbol.trim().uppercase(Locale.ROOT) }
        holder.list.removeAllViews()
        filteredAssets.forEach { entity ->
            val nativeAsset = entity.toUiAsset()
            addAssetRow(
                context = holder.list.context,
                parent = holder.list,
                asset = nativeAsset.toIdrDisplay(usdExchangeRate),
                compact = false
            ) {
                onAssetClick(nativeAsset)
            }
        }
        if (holder.list.childCount == 0) {
            val empty = LayoutInflater.from(holder.list.context)
                .inflate(R.layout.view_empty_state, holder.list, false)
            holder.list.addView(empty)
        }
    }

    override fun getItemCount(): Int = assetPagerCategories.size

    class PageViewHolder(page: View) : RecyclerView.ViewHolder(page) {
        val list: LinearLayout = page.findViewById(R.id.asset_page_list)
    }
}
