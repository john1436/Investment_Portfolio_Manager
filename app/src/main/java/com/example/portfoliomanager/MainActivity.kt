package com.example.portfoliomanager

import android.os.Bundle
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import android.content.Context
import android.text.InputType
import kotlin.math.abs

// Data Models
data class Stock(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val ticker: String,
    var quantity: Double,
    var avgBuyPrice: Double,
    var currentPrice: Double,
    val sector: String
) {
    fun investedAmount(): Double = quantity * avgBuyPrice
    fun currentValue(): Double = quantity * currentPrice
    fun gainLoss(): Double = currentValue() - investedAmount()
    fun gainLossPercent(): Double = if (investedAmount() > 0) (gainLoss() / investedAmount()) * 100 else 0.0
}

data class TargetAllocation(
    val sector: String,
    val targetPercent: Double
)

class MainActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var portfolioView: View
    private lateinit var stocksView: View
    private lateinit var fab: FloatingActionButton

    private val stocks = mutableListOf<Stock>()
    private val gson = Gson()

    // Target allocations based on requirements
    private val targetAllocations = listOf(
        TargetAllocation("Gold and Silver", 20.0),
        TargetAllocation("Indian Equities", 40.0),
        TargetAllocation("US Tech Stocks", 15.0),
        TargetAllocation("US ETF", 5.0),
        TargetAllocation("Crypto", 10.0),
        TargetAllocation("Business/Cash", 10.0)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadData()
        setupUI()
    }

    private fun setupUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Tab Layout
        tabLayout = TabLayout(this).apply {
            addTab(newTab().setText("Portfolio"))
            addTab(newTab().setText("Stocks"))
            tabGravity = TabLayout.GRAVITY_FILL
        }
        mainLayout.addView(tabLayout)

        // Container for views
        val container = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        portfolioView = createPortfolioView()
        stocksView = createStocksView()

        container.addView(portfolioView)
        container.addView(stocksView)
        stocksView.visibility = View.GONE

        mainLayout.addView(container)

        // FAB
        fab = FloatingActionButton(this).apply {
            setImageResource(android.R.drawable.ic_input_add)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                setMargins(0, 0, 48, 48)
            }
            visibility = View.GONE
            setOnClickListener { showAddStockDialog() }
        }

        val rootFrame = FrameLayout(this)
        rootFrame.addView(mainLayout)
        rootFrame.addView(fab)

        setContentView(rootFrame)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        portfolioView.visibility = View.VISIBLE
                        stocksView.visibility = View.GONE
                        fab.visibility = View.GONE
                        refreshPortfolioView()
                    }
                    1 -> {
                        portfolioView.visibility = View.GONE
                        stocksView.visibility = View.VISIBLE
                        fab.visibility = View.VISIBLE
                        refreshStocksView()
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun createPortfolioView(): ScrollView {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Summary Cards
        val summaryLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 32)
        }
        layout.addView(summaryLayout)

        // Pie Chart
        val pieChart = PieChart(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                600
            )
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
            holeRadius = 40f
            transparentCircleRadius = 45f
        }
        layout.addView(pieChart)

        // Sectors List
        val sectorsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 0)
        }
        layout.addView(sectorsLayout)

        // Rebalancing Section
        val rebalanceLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 32, 0, 0)
        }
        layout.addView(rebalanceLayout)

        scrollView.addView(layout)
        return scrollView
    }

    private fun refreshPortfolioView() {
        val scrollView = portfolioView as ScrollView
        val mainLayout = scrollView.getChildAt(0) as LinearLayout

        val summaryLayout = mainLayout.getChildAt(0) as LinearLayout
        summaryLayout.removeAllViews()

        val totalInvested = stocks.sumOf { it.investedAmount() }
        val totalCurrent = stocks.sumOf { it.currentValue() }
        val totalGainLoss = totalCurrent - totalInvested
        val totalGainLossPercent = if (totalInvested > 0) (totalGainLoss / totalInvested) * 100 else 0.0

        summaryLayout.addView(createSummaryCard("Total Invested", "₹%.2f".format(totalInvested), Color.BLUE))
        summaryLayout.addView(createSummaryCard("Current Value", "₹%.2f".format(totalCurrent), Color.GREEN))
        summaryLayout.addView(createSummaryCard(
            "Gain/Loss",
            "₹%.2f (%.2f%%)".format(totalGainLoss, totalGainLossPercent),
            if (totalGainLoss >= 0) Color.GREEN else Color.RED
        ))
        summaryLayout.addView(createSummaryCard(
            "Holdings",
            "${stocks.size} stocks in ${stocks.map { it.sector }.distinct().size} sectors",
            Color.GRAY
        ))

        // Update Pie Chart
        val pieChart = mainLayout.getChildAt(1) as PieChart
        updatePieChart(pieChart, totalCurrent)

        // Update Sectors List
        val sectorsLayout = mainLayout.getChildAt(2) as LinearLayout
        sectorsLayout.removeAllViews()

        val sectorTitle = TextView(this).apply {
            text = "Sector Allocation"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        sectorsLayout.addView(sectorTitle)

        val sectorMap = stocks.groupBy { it.sector }
        for (sector in targetAllocations.map { it.sector }) {
            val sectorStocks = sectorMap[sector] ?: emptyList()
            val sectorValue = sectorStocks.sumOf { it.currentValue() }
            val sectorPercent = if (totalCurrent > 0) (sectorValue / totalCurrent) * 100 else 0.0
            val target = targetAllocations.find { it.sector == sector }?.targetPercent ?: 0.0

            sectorsLayout.addView(createSectorCard(sector, sectorPercent, target, sectorStocks, totalCurrent))
        }

        // Update Rebalancing Section
        val rebalanceLayout = mainLayout.getChildAt(3) as LinearLayout
        rebalanceLayout.removeAllViews()

        val rebalanceTitle = TextView(this).apply {
            text = "Rebalancing Recommendations"
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }
        rebalanceLayout.addView(rebalanceTitle)

        // Target allocation table
        val targetCard = createCard()
        val targetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val targetHeader = TextView(this).apply {
            text = "Target Portfolio Allocation"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        targetLayout.addView(targetHeader)

        for (target in targetAllocations) {
            val targetRow = TextView(this).apply {
                text = "${target.sector}: ${target.targetPercent}%"
                textSize = 14f
                setPadding(0, 4, 0, 4)
            }
            targetLayout.addView(targetRow)
        }
        targetCard.addView(targetLayout)
        rebalanceLayout.addView(targetCard)

        // Rebalancing alerts
        for (target in targetAllocations) {
            val sectorStocks = sectorMap[target.sector] ?: emptyList()
            val sectorValue = sectorStocks.sumOf { it.currentValue() }
            val sectorPercent = if (totalCurrent > 0) (sectorValue / totalCurrent) * 100 else 0.0
            val diff = sectorPercent - target.targetPercent

            if (abs(diff) > 2.0) {
                val amountToAdjust = (diff / 100) * totalCurrent
                val alert = if (diff > 0) {
                    "⚠️ ${target.sector} is %.2f%% above target, consider reducing by ₹%.2f".format(diff, abs(amountToAdjust))
                } else {
                    "💡 Add ₹%.2f to ${target.sector} to reach target (%.2f%% below)".format(abs(amountToAdjust), abs(diff))
                }

                rebalanceLayout.addView(createAlertCard(alert, if (diff > 0) Color.RED else Color.rgb(255, 193, 7)))
            }
        }

        // Check for individual stock concentration
        for (stock in stocks) {
            val stockPercent = if (totalCurrent > 0) (stock.currentValue() / totalCurrent) * 100 else 0.0
            if (stockPercent > 30) {
                rebalanceLayout.addView(createAlertCard(
                    "🚨 ${stock.name} (${stock.ticker}) exceeds 30% of portfolio (${String.format("%.2f", stockPercent)}%)",
                    Color.RED
                ))
            }
        }
    }

    private fun createSummaryCard(title: String, value: String, color: Int): View {
        val card = createCard()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        layout.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(Color.GRAY)
        })

        layout.addView(TextView(this).apply {
            text = value
            textSize = 20f
            setTextColor(color)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 0)
        })

        card.addView(layout)
        return card
    }

    private fun createSectorCard(sector: String, current: Double, target: Double, sectorStocks: List<Stock>, totalValue: Double): View {
        val card = createCard()
        val diff = current - target
        val bgColor = when {
            abs(diff) < 2.0 -> Color.WHITE
            diff > 0 -> Color.rgb(255, 235, 238)
            else -> Color.rgb(255, 248, 225)
        }
        card.setBackgroundColor(bgColor)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val textLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        textLayout.addView(TextView(this).apply {
            text = sector
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        textLayout.addView(TextView(this).apply {
            text = "Current: %.2f%% | Target: %.2f%%".format(current, target)
            textSize = 14f
            setTextColor(Color.GRAY)
        })

        val statusText = when {
            abs(diff) < 2.0 -> "✓ Balanced"
            diff > 0 -> "⚠️ Overweight (+%.2f%%)".format(diff)
            else -> "💡 Underweight (%.2f%%)".format(diff)
        }

        textLayout.addView(TextView(this).apply {
            text = statusText
            textSize = 12f
            setTextColor(when {
                abs(diff) < 2.0 -> Color.GREEN
                diff > 0 -> Color.RED
                else -> Color.rgb(255, 152, 0)
            })
        })

        headerLayout.addView(textLayout)

        val moreBtn = Button(this).apply {
            text = "More"
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                showSectorDetailDialog(sector, sectorStocks, totalValue)
            }
        }
        headerLayout.addView(moreBtn)

        layout.addView(headerLayout)
        card.addView(layout)
        return card
    }

    private fun showSectorDetailDialog(sector: String, sectorStocks: List<Stock>, totalValue: Double) {
        val dialog = AlertDialog.Builder(this)
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Pie chart for sector
        val pieChart = PieChart(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                500
            )
            setUsePercentValues(true)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setHoleColor(Color.WHITE)
        }

        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()
        val colorArray = intArrayOf(
            Color.rgb(255, 87, 34), Color.rgb(33, 150, 243), Color.rgb(76, 175, 80),
            Color.rgb(156, 39, 176), Color.rgb(255, 193, 7), Color.rgb(0, 188, 212)
        )

        var idx = 0
        for (stock in sectorStocks) {
            val stockValue = stock.currentValue()
            entries.add(PieEntry(stockValue.toFloat(), stock.name))
            colors.add(colorArray[idx % colorArray.size])
            idx++
        }

        val dataSet = PieDataSet(entries, "").apply {
            setColors(colors)
            valueTextSize = 12f
            valueTextColor = Color.WHITE
        }

        pieChart.data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(pieChart))
        }
        pieChart.invalidate()

        dialogView.addView(pieChart)

        // Stock list
        val stocksTitle = TextView(this).apply {
            text = "Holdings"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }
        dialogView.addView(stocksTitle)

        val sectorValue = sectorStocks.sumOf { it.currentValue() }
        val sectorPercent = if (totalValue > 0) (sectorValue / totalValue) * 100 else 0.0
        val target = targetAllocations.find { it.sector == sector }?.targetPercent ?: 0.0
        val diff = sectorPercent - target

        dialogView.addView(TextView(this).apply {
            text = "Sector: %.2f%% | Target: %.2f%%".format(sectorPercent, target)
            textSize = 14f
            setTextColor(Color.GRAY)
            setPadding(0, 0, 0, 16)
        })

        for (stock in sectorStocks) {
            val stockPercent = if (totalValue > 0) (stock.currentValue() / totalValue) * 100 else 0.0
            val stockCard = TextView(this).apply {
                text = "${stock.name} (${stock.ticker})\n%.2f%% of portfolio".format(stockPercent)
                textSize = 14f
                setPadding(16, 12, 16, 12)
                setBackgroundColor(Color.rgb(245, 245, 245))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8)
                }
            }
            dialogView.addView(stockCard)
        }

        // Recommendations
        if (abs(diff) > 2.0) {
            val rebalanceTitle = TextView(this).apply {
                text = "Recommendations"
                textSize = 18f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 16, 0, 8)
            }
            dialogView.addView(rebalanceTitle)

            val amountToAdjust = (diff / 100) * totalValue
            val alert = if (diff > 0) {
                "⚠️ Sector is %.2f%% above target, reduce by ₹%.2f".format(diff, abs(amountToAdjust))
            } else {
                "💡 Add ₹%.2f to reach target (%.2f%% below)".format(abs(amountToAdjust), abs(diff))
            }

            dialogView.addView(TextView(this).apply {
                text = alert
                textSize = 14f
                setTextColor(if (diff > 0) Color.RED else Color.rgb(255, 152, 0))
                setPadding(16, 8, 16, 8)
            })
        }

        // Check individual stocks
        for (stock in sectorStocks) {
            val stockPercent = if (totalValue > 0) (stock.currentValue() / totalValue) * 100 else 0.0
            if (stockPercent > 30) {
                dialogView.addView(TextView(this).apply {
                    text = "🚨 ${stock.name} exceeds 30% of total portfolio"
                    textSize = 14f
                    setTextColor(Color.RED)
                    setPadding(16, 8, 16, 8)
                })
            }
        }

        val scrollView = ScrollView(this)
        scrollView.addView(dialogView)

        dialog.setTitle(sector)
            .setView(scrollView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun createAlertCard(message: String, color: Int): View {
        val card = createCard()
        card.setBackgroundColor(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)))

        val textView = TextView(this).apply {
            text = message
            textSize = 14f
            setTextColor(color)
            setPadding(16, 16, 16, 16)
        }

        card.addView(textView)
        return card
    }

    private fun updatePieChart(pieChart: PieChart, totalValue: Double) {
        val entries = mutableListOf<PieEntry>()
        val colors = mutableListOf<Int>()

        val sectorMap = stocks.groupBy { it.sector }
        val colorArray = intArrayOf(
            Color.rgb(255, 193, 7), Color.rgb(33, 150, 243), Color.rgb(76, 175, 80),
            Color.rgb(156, 39, 176), Color.rgb(255, 87, 34), Color.rgb(0, 188, 212)
        )

        var idx = 0
        for (sector in targetAllocations.map { it.sector }) {
            val sectorStocks = sectorMap[sector] ?: emptyList()
            val sectorValue = sectorStocks.sumOf { it.currentValue() }
            if (sectorValue > 0) {
                val percent = (sectorValue / totalValue) * 100
                entries.add(PieEntry(sectorValue.toFloat(), "$sector\n%.1f%%".format(percent)))
                colors.add(colorArray[idx % colorArray.size])
                idx++
            }
        }

        if (entries.isEmpty()) {
            entries.add(PieEntry(1f, "No Data"))
            colors.add(Color.GRAY)
        }

        val dataSet = PieDataSet(entries, "Portfolio Allocation").apply {
            setColors(colors)
            valueTextSize = 12f
            valueTextColor = Color.BLACK
        }

        pieChart.data = PieData(dataSet)
        pieChart.invalidate()
    }

    private fun createStocksView(): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val recyclerView = RecyclerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            this.layoutManager = LinearLayoutManager(this@MainActivity)
        }

        layout.addView(recyclerView)
        return layout
    }

    private fun refreshStocksView() {
        val layout = stocksView as LinearLayout
        val recyclerView = layout.getChildAt(0) as RecyclerView
        recyclerView.adapter = StockAdapter(stocks)
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
            elevation = 4f
        }
    }

    private fun showAddStockDialog(existingStock: Stock? = null) {
        val dialog = AlertDialog.Builder(this)
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val nameInput = EditText(this).apply {
            hint = "Company Name"
            setText(existingStock?.name ?: "")
        }
        dialogView.addView(nameInput)

        val tickerInput = EditText(this).apply {
            hint = "Ticker"
            setText(existingStock?.ticker ?: "")
        }
        dialogView.addView(tickerInput)

        val qtyInput = EditText(this).apply {
            hint = "Quantity"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (existingStock != null) existingStock.quantity.toString() else "")
        }
        dialogView.addView(qtyInput)

        val buyPriceInput = EditText(this).apply {
            hint = "Average Buy Price"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (existingStock != null) existingStock.avgBuyPrice.toString() else "")
        }
        dialogView.addView(buyPriceInput)

        val currentPriceInput = EditText(this).apply {
            hint = "Current Price"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(if (existingStock != null) existingStock.currentPrice.toString() else "")
        }
        dialogView.addView(currentPriceInput)

        val sectorSpinner = Spinner(this)
        val sectors = targetAllocations.map { it.sector }.toMutableList()
        sectorSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sectors)
        existingStock?.let {
            val index = sectors.indexOf(it.sector)
            if (index >= 0) sectorSpinner.setSelection(index)
        }
        dialogView.addView(sectorSpinner)

        dialog.setTitle(if (existingStock != null) "Edit Stock" else "Add Stock")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text.toString()
                val ticker = tickerInput.text.toString()
                val qty = qtyInput.text.toString().toDoubleOrNull() ?: 0.0
                val buyPrice = buyPriceInput.text.toString().toDoubleOrNull() ?: 0.0
                val currentPrice = currentPriceInput.text.toString().toDoubleOrNull() ?: 0.0
                val sector = sectorSpinner.selectedItem.toString()

                if (name.isNotEmpty() && ticker.isNotEmpty()) {
                    if (existingStock != null) {
                        existingStock.quantity = qty
                        existingStock.avgBuyPrice = buyPrice
                        existingStock.currentPrice = currentPrice
                    } else {
                        // Check if stock already exists for averaging
                        val existing = stocks.find { it.ticker == ticker && it.sector == sector }
                        if (existing != null) {
                            val newQty = existing.quantity + qty
                            val newAvgPrice = ((existing.quantity * existing.avgBuyPrice) + (qty * buyPrice)) / newQty
                            existing.quantity = newQty
                            existing.avgBuyPrice = newAvgPrice
                            existing.currentPrice = currentPrice
                        } else {
                            stocks.add(Stock(
                                name = name,
                                ticker = ticker,
                                quantity = qty,
                                avgBuyPrice = buyPrice,
                                currentPrice = currentPrice,
                                sector = sector
                            ))
                        }
                    }
                    saveData()
                    refreshStocksView()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    inner class StockAdapter(private val stocks: MutableList<Stock>) :
        RecyclerView.Adapter<StockAdapter.StockViewHolder>() {

        inner class StockViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: LinearLayout = view.findViewById(android.R.id.content)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StockViewHolder {
            val card = LinearLayout(parent.context).apply {
                id = android.R.id.content
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                setPadding(24, 24, 24, 24)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(16, 8, 16, 8)
                }
            }
            return StockViewHolder(card)
        }

        override fun onBindViewHolder(holder: StockViewHolder, position: Int) {
            val stock = stocks[position]
            holder.card.removeAllViews()

            val headerLayout = LinearLayout(holder.itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            val infoLayout = LinearLayout(holder.itemView.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            infoLayout.addView(TextView(holder.itemView.context).apply {
                text = "${stock.name} (${stock.ticker})"
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            infoLayout.addView(TextView(holder.itemView.context).apply {
                text = "Sector: ${stock.sector}"
                textSize = 12f
                setTextColor(Color.GRAY)
                setPadding(0, 4, 0, 0)
            })

            infoLayout.addView(TextView(holder.itemView.context).apply {
                text = "Qty: ${stock.quantity} @ ₹${String.format("%.2f", stock.avgBuyPrice)}"
                textSize = 14f
                setPadding(0, 8, 0, 0)
            })

            infoLayout.addView(TextView(holder.itemView.context).apply {
                text = "Current: ₹${String.format("%.2f", stock.currentPrice)}"
                textSize = 14f
                setPadding(0, 4, 0, 0)
            })

            val gainLoss = stock.gainLoss()
            val gainLossPercent = stock.gainLossPercent()
            infoLayout.addView(TextView(holder.itemView.context).apply {
                text = "P&L: ₹${String.format("%.2f", gainLoss)} (${String.format("%.2f", gainLossPercent)}%)"
                textSize = 14f
                setTextColor(if (gainLoss >= 0) Color.GREEN else Color.RED)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, 4, 0, 0)
            })

            headerLayout.addView(infoLayout)

            val buttonLayout = LinearLayout(holder.itemView.context).apply {
                orientation = LinearLayout.VERTICAL
            }

            val editBtn = Button(holder.itemView.context).apply {
                text = "Edit"
                setOnClickListener {
                    showAddStockDialog(stock)
                }
            }
            buttonLayout.addView(editBtn)

            val deleteBtn = Button(holder.itemView.context).apply {
                text = "Delete"
                setTextColor(Color.RED)
                setOnClickListener {
                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("Delete Stock")
                        .setMessage("Are you sure you want to delete ${stock.name}?")
                        .setPositiveButton("Delete") { _, _ ->
                            stocks.remove(stock)
                            saveData()
                            notifyDataSetChanged()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            buttonLayout.addView(deleteBtn)

            headerLayout.addView(buttonLayout)
            holder.card.addView(headerLayout)
        }

        override fun getItemCount() = stocks.size
    }

    private fun saveData() {
        val json = gson.toJson(stocks)
        getSharedPreferences("portfolio_data", Context.MODE_PRIVATE)
            .edit()
            .putString("stocks", json)
            .apply()
    }

    private fun loadData() {
        val json = getSharedPreferences("portfolio_data", Context.MODE_PRIVATE)
            .getString("stocks", "[]")
        val type = object : TypeToken<MutableList<Stock>>() {}.type
        val loadedStocks: MutableList<Stock>? = gson.fromJson(json, type)
        if (loadedStocks != null) {
            stocks.clear()
            stocks.addAll(loadedStocks)
        }
    }
}