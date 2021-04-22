package updated.mysterium.vpn.ui.manual.connect.select.node.saved

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import network.mysterium.vpn.databinding.FragmentSavedNodesBinding
import org.koin.android.ext.android.inject
import updated.mysterium.vpn.model.manual.connect.Proposal
import updated.mysterium.vpn.ui.favourites.FavouritesAdapter
import updated.mysterium.vpn.ui.manual.connect.home.HomeActivity
import updated.mysterium.vpn.ui.manual.connect.select.node.all.AllNodesViewModel

class SavedNodesFragment : Fragment() {

    private companion object {
        const val TAG = "SavedNodesFragment"
    }

    private lateinit var binding: FragmentSavedNodesBinding
    private val allNodesViewModel: AllNodesViewModel by inject()
    private val viewModel: SavedNodesViewModel by inject()
    private val savedNodesAdapter = FavouritesAdapter()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSavedNodesBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSavedListRecycler()
        subscribeViewModel()
    }

    private fun subscribeViewModel() {
        allNodesViewModel.proposals.observe(viewLifecycleOwner, {
            getFavouritesList(it.first().proposalList)
        })
    }

    private fun initSavedListRecycler() {
        savedNodesAdapter.onProposalClicked = {
            navigateToHome(it)
        }
        savedNodesAdapter.onDeleteClicked = { proposal ->
            viewModel.deleteNodeFromFavourite(proposal).observe(viewLifecycleOwner, { result ->
                result.onSuccess {
                    getFavouritesList()
                }
                result.onFailure {
                    Log.e(TAG, "Deleting failed")
                    // TODO("Implement error handling")
                }
            })
        }
        binding.nodesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = savedNodesAdapter
        }
        allNodesViewModel.getProposals()
    }

    private fun getFavouritesList(proposals: List<Proposal>? = null) {
        viewModel.getSavedNodes(proposals).observe(viewLifecycleOwner, { result ->
            result.onSuccess {
                savedNodesAdapter.replaceAll(it)
            }
            result.onFailure {
                if (it is NoSuchElementException) {
                    deleteListTitles()
                    savedNodesAdapter.clear()
                }
            }
            binding.loaderAnimation.visibility = View.GONE
            binding.loaderAnimation.cancelAnimation()
        })
    }

    private fun navigateToHome(proposal: Proposal) {
        val intent = Intent(requireContext(), HomeActivity::class.java)
        intent.putExtra(HomeActivity.EXTRA_PROPOSAL_MODEL, proposal)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
    }

    private fun deleteListTitles() {
        binding.nodeTextView.visibility = View.INVISIBLE
        binding.priceTextView.visibility = View.INVISIBLE
        binding.qualityTextView.visibility = View.INVISIBLE
    }
}
