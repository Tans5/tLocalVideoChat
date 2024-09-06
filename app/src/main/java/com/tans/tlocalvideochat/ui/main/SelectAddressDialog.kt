package com.tans.tlocalvideochat.ui.main

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.tans.tlocalvideochat.R
import com.tans.tlocalvideochat.databinding.AddressItemLayoutBinding
import com.tans.tlocalvideochat.databinding.SelectAdddressDialogBinding
import com.tans.tlocalvideochat.ui.CoroutineDialogCancelableResultCallback
import com.tans.tlocalvideochat.ui.coroutineShowSafe
import com.tans.tlocalvideochat.webrtc.InetAddressWrapper
import com.tans.tuiutils.adapter.impl.builders.SimpleAdapterBuilderImpl
import com.tans.tuiutils.adapter.impl.databinders.DataBinderImpl
import com.tans.tuiutils.adapter.impl.datasources.FlowDataSourceImpl
import com.tans.tuiutils.adapter.impl.viewcreatators.SingleItemViewCreatorImpl
import com.tans.tuiutils.dialog.BaseCoroutineStateCancelableResultDialogFragment
import com.tans.tuiutils.dialog.DialogCancelableResultCallback
import com.tans.tuiutils.view.clicks
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class SelectAddressDialog : BaseCoroutineStateCancelableResultDialogFragment<SelectAddressDialog.Companion.State, InetAddressWrapper> {

    constructor() : super(State(), null)


    constructor(
        allAddresses: List<InetAddressWrapper>,
        defaultSelectedAddress: InetAddressWrapper,
        callback: DialogCancelableResultCallback<InetAddressWrapper>
    ) : super(State(allAddresses, Optional.of(defaultSelectedAddress)), callback)

    override fun createContentView(context: Context, parent: ViewGroup): View {
        return LayoutInflater.from(context).inflate(R.layout.select_adddress_dialog, parent, false)
    }

    override fun bindContentView(view: View) {
        val viewBinding = SelectAdddressDialogBinding.bind(view)
        viewBinding.addressesRv.adapter = SimpleAdapterBuilderImpl(
            itemViewCreator = SingleItemViewCreatorImpl(R.layout.address_item_layout),
            dataSource = FlowDataSourceImpl(
                dataFlow = stateFlow().map { s ->
                    val selectedAddress = s.selectedAddress.getOrNull()
                    s.allAddresses.map { (it == selectedAddress) to it }
                },
                areDataItemsTheSameParam = { d1, d2 -> d1.second == d2.second },
                getDataItemsChangePayloadParam = { d1, d2 -> if (d1.second == d2.second && d1.first != d2.first) Unit else null }
            ),
            dataBinder = DataBinderImpl<Pair<Boolean, InetAddressWrapper>> { data, itemView, _ ->
                val itemViewBinding = AddressItemLayoutBinding.bind(itemView)
                itemViewBinding.addressTv.text = data.second.toString()
            }.addPayloadDataBinder(Unit) { data, itemView, _ ->
                val itemViewBinding = AddressItemLayoutBinding.bind(itemView)
                itemViewBinding.selectedRb.isChecked = data.first
                itemViewBinding.root.clicks(this@SelectAddressDialog) {
                    val lastSelectedAddress = currentState().selectedAddress.getOrNull()
                    if (data.second != lastSelectedAddress) {
                        updateState { s -> s.copy(selectedAddress = Optional.of(data.second)) }
                    }
                }
            }
        ).build()

        viewBinding.cancelBt.clicks(this) {
            onCancel()
        }

        viewBinding.okBt.clicks(this) {
            val selectedAddress = currentState().selectedAddress.getOrNull()
            if (selectedAddress != null) {
                onResult(selectedAddress)
            } else {
                onCancel()
            }
        }
    }

    override fun firstLaunchInitData() { }


    companion object {
        data class State(
            val allAddresses: List<InetAddressWrapper> = emptyList(),
            val selectedAddress: Optional<InetAddressWrapper> = Optional.empty()
        )
    }
}

suspend fun FragmentManager.showSelectAddressDialog(allAddresses: List<InetAddressWrapper>, defaultSelectAddress: InetAddressWrapper): InetAddressWrapper? {
    return suspendCancellableCoroutine { cont ->
        val callback = CoroutineDialogCancelableResultCallback<InetAddressWrapper>(cont)
        val d = SelectAddressDialog(allAddresses, defaultSelectAddress, callback)
        this.coroutineShowSafe(d, "SelectAddressDialog#${System.currentTimeMillis()}", cont)
    }
}

