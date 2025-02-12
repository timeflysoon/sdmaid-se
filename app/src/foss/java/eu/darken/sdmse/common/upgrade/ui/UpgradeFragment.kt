package eu.darken.sdmse.common.upgrade.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import dagger.hilt.android.AndroidEntryPoint
import eu.darken.sdmse.R
import eu.darken.sdmse.common.EdgeToEdgeHelper
import eu.darken.sdmse.common.uix.Fragment3
import eu.darken.sdmse.common.viewbinding.viewBinding
import eu.darken.sdmse.databinding.UpgradeFragmentBinding

@AndroidEntryPoint
class UpgradeFragment : Fragment3(R.layout.upgrade_fragment) {

    override val vm: UpgradeViewModel by viewModels()
    override val ui: UpgradeFragmentBinding by viewBinding()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        EdgeToEdgeHelper(requireActivity()).apply {
            insetsPadding(ui.toolbar, bottom = null)
            insetsPadding(ui.root, top = null, bottom = null)
            insetsPadding(ui.scrollView, top = null, left = null, right = null)
        }

        ui.toolbar.setupWithNavController(findNavController())

        ui.upgradeGithubSponsorsAction.setOnClickListener {
            Toast.makeText(
                requireContext(),
                R.string.upgrade_screen_thanks_toast,
                Toast.LENGTH_LONG,
            ).show()
            vm.goGithubSponsors()
        }

        super.onViewCreated(view, savedInstanceState)
    }

}
