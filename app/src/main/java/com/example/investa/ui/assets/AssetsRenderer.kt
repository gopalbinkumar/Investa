package com.example.investa.ui.assets

import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.investa.R
import com.example.investa.navigation.AppScreen
import com.example.investa.navigation.ScreenHost
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

internal class AssetsRenderer(private val host: ScreenHost) {
    private var pager: ViewPager2? = null
    private var tabLayout: TabLayout? = null
    private var pagerAdapter: AssetCategoryPagerAdapter? = null
    private var tabMediator: TabLayoutMediator? = null
    private var isInitialized = false

    fun render() {
        val root = host.assetsRoot ?: host.inflate(R.layout.screen_assets).also { host.assetsRoot = it }
        if (root.parent == null) host.attach(root)

        val tabs = root.findViewById<TabLayout>(R.id.category_container)
        val viewPager = root.findViewById<ViewPager2>(R.id.asset_category_pager)
        if (!isInitialized) {
            setupTabsAndPager(tabs, viewPager)
            isInitialized = true
        }

        pagerAdapter?.updateAssets(host.databaseAssets, host.exchangeRateFor("USD"))
        val selectedIndex = assetPagerCategories.indexOf(host.selectedCategory)
            .coerceIn(0, assetPagerCategories.lastIndex)
        if (viewPager.currentItem != selectedIndex) {
            viewPager.setCurrentItem(selectedIndex, false)
        }
        host.selectedCategory = assetPagerCategories[selectedIndex]
        updateAssetCount(selectedIndex)
        updateTabStyles(tabs, selectedIndex)
    }

    private fun setupTabsAndPager(tabs: TabLayout, viewPager: ViewPager2) {
        tabLayout = tabs
        pager = viewPager
        pagerAdapter = AssetCategoryPagerAdapter { asset ->
            host.selectedAsset = asset
            host.showScreen(AppScreen.DETAIL)
        }
        viewPager.adapter = pagerAdapter
        viewPager.offscreenPageLimit = 1

        tabMediator = TabLayoutMediator(tabs, viewPager) { tab, position ->
            tab.text = assetPagerCategories[position]
            tab.contentDescription = assetPagerCategories[position]
            tab.customView = createTabView(assetPagerCategories[position])
        }.also { it.attach() }

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val position = tab.position
                if (position in assetPagerCategories.indices) {
                    updateTabStyles(tabs, position)
                    updateAssetCount(position)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                updateTabStyles(tabs, viewPager.currentItem)
            }

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position !in assetPagerCategories.indices) return
                host.selectedCategory = assetPagerCategories[position]
                updateAssetCount(position)
                updateTabStyles(tabs, position)
            }
        })
    }

    private fun createTabView(category: String): TextView = TextView(host.activity).apply {
        text = category
        textSize = 12f
        setPadding(host.dp(17), host.dp(9), host.dp(17), host.dp(9))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun updateTabStyles(tabs: TabLayout, selectedPosition: Int) {
        for (position in assetPagerCategories.indices) {
            val tab = tabs.getTabAt(position) ?: continue
            val tabText = tab.customView as? TextView ?: continue
            val selected = position == selectedPosition
            tab.view.minimumWidth = 0
            tab.view.minimumHeight = 0
            tab.view.setPadding(0, 0, 0, 0)
            (tab.view.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                marginEnd = host.dp(8)
                tab.view.layoutParams = this
            }
            tabText.background = ContextCompat.getDrawable(
                host.activity,
                if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip
            )
            tabText.setTextColor(ContextCompat.getColor(
                host.activity,
                if (selected) R.color.investa_background else R.color.investa_text_secondary
            ))
        }
        tabs.setSelectedTabIndicatorColor(Color.TRANSPARENT)
    }

    private fun updateAssetCount(position: Int) {
        val count = host.databaseAssets.count { asset ->
            position == 0 || asset.category == assetPagerCategories[position]
        }
        val root = host.assetsRoot ?: return
        root.findViewById<TextView>(R.id.asset_count).text = "$count Assets"
    }
}
