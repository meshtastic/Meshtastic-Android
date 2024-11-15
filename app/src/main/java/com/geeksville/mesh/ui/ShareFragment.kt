package com.geeksville.mesh.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.R
import com.geeksville.mesh.databinding.ShareFragmentBinding
import com.geeksville.mesh.model.Contact
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.theme.AppTheme
import dagger.hilt.android.AndroidEntryPoint

internal fun FragmentManager.navigateToShareMessage(message: String) {
    val shareFragment = ShareFragment().apply {
        arguments = bundleOf("message" to message)
    }
    beginTransaction()
        .add(R.id.mainActivityLayout, shareFragment)
        .addToBackStack(null)
        .commit()
}

@AndroidEntryPoint
class ShareFragment : ScreenFragment("Messages"), Logging {

    private val model: UIViewModel by activityViewModels()
    private var _binding: ShareFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val contacts get() = model.contactList.value
    private var selectedContact = mutableStateOf("")

    private fun shareMessage(contact: Contact) {
        debug("calling MessagesFragment filter:${contact.contactKey}")
        parentFragmentManager.navigateToPreInitMessages(
            contact.contactKey,
            contact.longName,
            arguments?.getString("message").toString()
        )
    }

    private fun onClick(contact: Contact) {
        if (selectedContact.value == contact.contactKey) {
            selectedContact.value = ""
            binding.shareButton.isEnabled = false
        } else {
            selectedContact.value = contact.contactKey
            binding.shareButton.isEnabled = true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ShareFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.shareButton.isEnabled = false

        binding.shareButton.setOnClickListener {
            debug("User clicked shareButton")
            val contact = contacts.find { c -> c.contactKey == selectedContact.value }
            if (contact != null) {
                shareMessage(contact)
            }
        }

        binding.contactListView.setContent {
            val contacts by model.contactList.collectAsStateWithLifecycle()
            AppTheme {
                ShareContactListView(
                    contacts = contacts,
                    selectedContact = selectedContact.value,
                    onClick = ::onClick,
                )
            }
        }
    }
}


@Composable
fun ShareContactListView(
    contacts: List<Contact>,
    selectedContact: String,
    onClick: (Contact) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(6.dp),
    ) {
        items(contacts, key = { it.contactKey }) { contact ->
            val selected = contact.contactKey == selectedContact
            ContactItem(
                contact = contact,
                selected = selected,
                onClick = { onClick(contact) },
                onLongClick = {},
            )
        }
    }
}
