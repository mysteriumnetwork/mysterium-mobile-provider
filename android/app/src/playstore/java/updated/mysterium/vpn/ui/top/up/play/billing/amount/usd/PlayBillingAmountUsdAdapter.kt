package updated.mysterium.vpn.ui.top.up.play.billing.amount.usd

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import network.mysterium.vpn.R
import network.mysterium.vpn.databinding.ItemCardElementBinding
import updated.mysterium.vpn.common.adapters.ContentListAdapter
import updated.mysterium.vpn.model.top.up.TopUpPlayBillingCardItem

class PlayBillingAmountUsdAdapter :
    ContentListAdapter<TopUpPlayBillingCardItem, PlayBillingAmountUsdAdapter.TopUpAmountUSDViewHolder>() {

    private var selectedCardItem: TopUpPlayBillingCardItem? = null
    var onItemSelected: ((TopUpPlayBillingCardItem) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TopUpAmountUSDViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_card_element, parent, false)
    )

    override fun onBindViewHolder(holder: TopUpAmountUSDViewHolder, position: Int) {
        holder.bind(items[position])
    }

    fun getSelectedValue() = selectedCardItem?.amountUsd

    inner class TopUpAmountUSDViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val binding = ItemCardElementBinding.bind(itemView)

        fun bind(cardItem: TopUpPlayBillingCardItem) {
            binding.cardItemValue.text =
                itemView.context.getString(R.string.top_up_amount_usd, cardItem.amountUsd)
            if (cardItem.isSelected) {
                selectedCardItem = cardItem
                onItemSelected?.invoke(cardItem)
                selectedState()
            } else {
                unselectedState()
            }
            binding.cardItemFrame.setOnClickListener {
                if (cardItem != selectedCardItem) {
                    onItemSelected?.invoke(cardItem)
                    selectedCardItem?.changeState()
                    cardItem.changeState()
                    selectedCardItem = cardItem
                    notifyDataSetChanged()
                }
            }
        }

        private fun selectedState() {
            binding.cardItemValue.setTextColor(Color.WHITE)
            binding.cardItemFrame.background = ContextCompat.getDrawable(
                itemView.context, R.drawable.shape_card_element_selected
            )
            binding.shadow.visibility = View.VISIBLE
        }

        private fun unselectedState() {
            binding.cardItemValue.setTextColor(
                itemView.context.getColor(R.color.onboarding_title_dark_blue)
            )
            binding.cardItemFrame.background = ContextCompat.getDrawable(
                itemView.context, R.drawable.shape_card_element_unselected
            )
            binding.shadow.visibility = View.INVISIBLE
        }
    }
}
