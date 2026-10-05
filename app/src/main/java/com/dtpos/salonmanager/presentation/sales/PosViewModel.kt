package com.dtpos.salonmanager.presentation.sales

import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.util.Money
import com.dtpos.salonmanager.core.util.Percent
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.database.entities.CustomerEntity
import com.dtpos.salonmanager.data.database.entities.PaymentAccountEntity
import com.dtpos.salonmanager.data.repository.PaymentAccountRepository
import com.dtpos.salonmanager.data.database.entities.ServiceEntity
import com.dtpos.salonmanager.data.database.entities.StaffEntity
import com.dtpos.salonmanager.data.database.model.CustomerListRow
import com.dtpos.salonmanager.data.repository.CustomerInput
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.calc.CartProblem
import com.dtpos.salonmanager.domain.calc.SaleCalculator
import com.dtpos.salonmanager.domain.model.CartLine
import com.dtpos.salonmanager.domain.model.DiscountType
import com.dtpos.salonmanager.domain.model.Gender
import com.dtpos.salonmanager.domain.model.NewSaleRequest
import com.dtpos.salonmanager.domain.model.PaymentMethod
import com.dtpos.salonmanager.domain.model.SaleDiscount
import com.dtpos.salonmanager.domain.model.SaleTotals
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.messageRes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class PosStep { SERVICES, CHECKOUT }

/** Where the money goes: the cash drawer, a JazzCash / EasyPaisa / bank account, or a plain method. */
data class PayOption(val method: PaymentMethod, val accountId: Long? = null, val accountName: String? = null) {
    companion object {
        val CASH = PayOption(PaymentMethod.CASH)
    }
}

/** Editable POS state (catalog data comes from separate flows). */
data class PosDraft(
    val step: PosStep = PosStep.SERVICES,
    val serviceQuery: String = "",
    val category: String? = null,
    val defaultStaffId: Long? = null,
    val cart: List<CartLine> = emptyList(),
    val customer: CustomerEntity? = null,
    val discountType: DiscountType = DiscountType.AMOUNT,
    val discountInput: String = "",
    val pay: PayOption = PayOption.CASH,
    val tenderedInput: String = "",
    val note: String = "",
    val saving: Boolean = false,
    val problems: List<CartProblem> = emptyList(),
    /** Udhaar: the customer pays part (or nothing) now and the rest is a pending bill. */
    val credit: Boolean = false,
    val paidNowInput: String = "",
    /** Set when an existing receipt is being corrected. */
    val editingSaleId: Long? = null,
    val editingReceiptNumber: String? = null,
    /** Customer name / phone kept on a receipt whose customer record no longer exists. */
    val snapshotName: String? = null,
    val snapshotPhone: String? = null,
) {
    val paymentMethod: PaymentMethod get() = pay.method
}

data class PosUiState(
    val draft: PosDraft = PosDraft(),
    val services: List<ServiceEntity> = emptyList(),
    val categories: List<String> = emptyList(),
    val staff: List<StaffEntity> = emptyList(),
    val totals: SaleTotals = SaleTotals.EMPTY,
    val discountValid: Boolean = true,
    val tenderedMinor: Long? = null,
    val readOnly: Boolean = false,
    val accounts: List<PaymentAccountEntity> = emptyList(),
    /** Paid now when selling on udhaar (null = invalid input). */
    val paidNowMinor: Long? = 0,
) {
    /** Cash first, then the owner's accounts; without accounts the plain card / bank / other methods. */
    val payOptions: List<PayOption>
        get() {
            val fromAccounts = accounts.map { PayOption(PaymentAccountRepository.methodFor(it.kind), it.id, it.name) }
            val base = listOf(PayOption.CASH) + fromAccounts.ifEmpty {
                listOf(PayOption(PaymentMethod.CARD), PayOption(PaymentMethod.BANK), PayOption(PaymentMethod.OTHER))
            }
            return if (draft.pay in base) base else base + draft.pay
        }
    val creditMinor: Long
        get() = if (!draft.credit) 0L else (totals.totalMinor - (paidNowMinor ?: 0L)).coerceAtLeast(0L)
    val customerName: String? get() = draft.customer?.name ?: draft.snapshotName

    val filteredServices: List<ServiceEntity>
        get() = services.filter { s ->
            (draft.category == null || s.category == draft.category) &&
                (draft.serviceQuery.isBlank() || s.name.contains(draft.serviceQuery.trim(), ignoreCase = true))
        }
    val itemCount: Int get() = draft.cart.sumOf { it.quantity }
    val changeMinor: Long get() = SaleCalculator.change(totals.totalMinor, tenderedMinor)
    fun quantityOf(serviceId: Long): Int = draft.cart.filter { it.serviceId == serviceId }.sumOf { it.quantity }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PosViewModel(private val container: AppContainer, initialCustomerId: Long?, editSaleId: Long? = null) : BaseViewModel() {

    private val draft = MutableStateFlow(PosDraft())

    val state: StateFlow<PosUiState> = combine(
        draft,
        container.serviceRepository.observeActive(),
        container.staffRepository.observeActive(),
        container.licenseManager.state,
        container.paymentAccountRepository.observeActive(),
    ) { d, services, staff, license, accounts ->
        val discount = discountOf(d)
        val tendered = if (d.paymentMethod == PaymentMethod.CASH && !d.credit) Money.parse(d.tenderedInput) else null
        PosUiState(
            draft = d,
            services = services,
            categories = services.map { it.category }.distinct(),
            staff = staff,
            totals = SaleCalculator.calculate(d.cart, discount ?: SaleDiscount.NONE),
            discountValid = discount != null,
            tenderedMinor = tendered,
            readOnly = license.isReadOnly,
            accounts = accounts,
            paidNowMinor = if (d.paidNowInput.isBlank()) 0L else Money.parse(d.paidNowInput),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PosUiState())

    // ---- Customer picker ----------------------------------------------------------------

    private val customerQuery = MutableStateFlow("")
    val customerQueryText: StateFlow<String> = customerQuery.asStateFlow()
    val customerResults: StateFlow<List<CustomerListRow>> = customerQuery
        .debounce(150)
        .flatMapLatest { container.customerRepository.search(it, limit = 30) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _completed = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    /** Emits the id of a sale right after it was saved. */
    val completed: SharedFlow<Long> = _completed.asSharedFlow()

    init {
        if (editSaleId != null) loadForEdit(editSaleId)
        if (initialCustomerId != null) {
            viewModelScope.launch {
                container.customerRepository.get(initialCustomerId)?.let { c -> draft.update { it.copy(customer = c) } }
            }
        }
        // Default the "served by" staff to the only / first active staff member.
        viewModelScope.launch {
            state.collect { s ->
                if (draft.value.defaultStaffId == null && s.staff.size == 1) {
                    draft.update { it.copy(defaultStaffId = s.staff.first().id) }
                }
            }
        }
    }

    fun onCustomerQuery(value: String) {
        customerQuery.value = value
    }

    fun selectCustomer(customer: CustomerEntity?) = draft.update { it.copy(customer = customer, snapshotName = null, snapshotPhone = null) }

    /** Quick customer creation from the POS (name + optional phone). */
    fun quickAddCustomer(name: String, phone: String, onDone: () -> Unit) {
        val validName = Validators.requiredName(name)
        val validPhone = Validators.phone(phone)
        if (validName !is FieldResult.Valid) {
            showMessage(validName.errorOrNull!!.messageRes)
            return
        }
        if (validPhone !is FieldResult.Valid) {
            showMessage(validPhone.errorOrNull!!.messageRes)
            return
        }
        launchSafe {
            when (val result = container.customerRepository.save(
                CustomerInput(validName.value, validPhone.value, Gender.UNSPECIFIED, null, null, null),
            )) {
                is DataResult.Success -> {
                    selectCustomer(container.customerRepository.get(result.data))
                    onDone()
                }
                is DataResult.Failure -> showMessage(result.error.messageRes)
            }
        }
    }

    // ---- Catalog & cart --------------------------------------------------------------------

    fun onServiceQuery(value: String) = draft.update { it.copy(serviceQuery = value) }

    fun onCategory(category: String?) = draft.update { it.copy(category = category) }

    fun onDefaultStaff(staffId: Long?) = draft.update { it.copy(defaultStaffId = staffId) }

    fun addService(service: ServiceEntity) {
        val staff = state.value.staff.firstOrNull { it.id == draft.value.defaultStaffId }
        draft.update { d ->
            val existing = d.cart.firstOrNull { it.serviceId == service.id && it.staffId == staff?.id && it.discountMinor == 0L && it.unitPriceMinor == service.priceMinor }
            val cart = if (existing != null && existing.quantity < Validators.MAX_QUANTITY) {
                d.cart.map { if (it.key == existing.key) it.copy(quantity = it.quantity + 1) else it }
            } else {
                d.cart + CartLine(
                    key = UUID.randomUUID().toString(),
                    serviceId = service.id,
                    serviceName = service.name,
                    staffId = staff?.id,
                    staffName = staff?.name,
                    unitPriceMinor = service.priceMinor,
                    quantity = 1,
                    commissionBps = staff.commissionRate(),
                )
            }
            d.copy(cart = cart, problems = emptyList())
        }
    }

    fun changeQuantity(key: String, delta: Int) = draft.update { d ->
        d.copy(
            cart = d.cart.mapNotNull { line ->
                if (line.key != key) line
                else (line.quantity + delta).takeIf { it > 0 }?.let { line.copy(quantity = it.coerceAtMost(Validators.MAX_QUANTITY)) }
            },
            problems = emptyList(),
        )
    }

    fun removeLine(key: String) = draft.update { d -> d.copy(cart = d.cart.filterNot { it.key == key }) }

    fun setLineStaff(key: String, staffId: Long?) {
        val staff = state.value.staff.firstOrNull { it.id == staffId }
        draft.update { d ->
            d.copy(
                cart = d.cart.map {
                    if (it.key == key) it.copy(staffId = staff?.id, staffName = staff?.name, commissionBps = staff.commissionRate()) else it
                },
            )
        }
    }

    /** Applies price / discount edits from the line dialog. Returns false when invalid. */
    fun updateLine(key: String, priceInput: String, discountInput: String): Boolean {
        val price = Validators.amount(priceInput).valueOrNull ?: return false
        val discount = Validators.amount(discountInput, allowZero = true, required = false).valueOrNull ?: return false
        val line = draft.value.cart.firstOrNull { it.key == key } ?: return false
        if (discount > price * line.quantity) return false
        draft.update { d -> d.copy(cart = d.cart.map { if (it.key == key) it.copy(unitPriceMinor = price, discountMinor = discount) else it }) }
        return true
    }

    fun clearCart() {
        draft.value = PosDraft(defaultStaffId = draft.value.defaultStaffId)
    }

    // ---- Checkout --------------------------------------------------------------------------

    fun goTo(step: PosStep) {
        if (step == PosStep.CHECKOUT && draft.value.cart.isEmpty()) {
            showMessage(R.string.pos_error_empty)
            return
        }
        draft.update { it.copy(step = step) }
    }

    fun onDiscountType(type: DiscountType) = draft.update { it.copy(discountType = type, discountInput = "") }
    fun onDiscountInput(value: String) = draft.update { it.copy(discountInput = value, problems = emptyList()) }
    fun onPay(option: PayOption) = draft.update { it.copy(pay = option, problems = emptyList()) }
    fun onCredit(credit: Boolean) = draft.update { it.copy(credit = credit, paidNowInput = "", problems = emptyList()) }
    fun onPaidNow(value: String) = draft.update { it.copy(paidNowInput = value) }

    /** Loads a completed receipt into the cart so the owner can correct it. */
    private fun loadForEdit(saleId: Long) = launchSafe {
        val loaded = container.saleRepository.saleWithItems(saleId)
        if (loaded == null) {
            showMessage(R.string.error_not_found)
            return@launchSafe
        }
        val sale = loaded.sale
        val customer = sale.customerId?.let { container.customerRepository.get(it) }
        draft.value = PosDraft(
            step = PosStep.CHECKOUT,
            cart = loaded.items.map {
                CartLine(
                    key = UUID.randomUUID().toString(),
                    serviceId = it.serviceId,
                    serviceName = it.serviceName,
                    staffId = it.staffId,
                    staffName = it.staffName,
                    unitPriceMinor = it.unitPriceMinor,
                    quantity = it.quantity,
                    discountMinor = it.lineDiscountMinor,
                    commissionBps = it.commissionBps,
                )
            },
            customer = customer,
            discountType = sale.saleDiscountType,
            discountInput = when {
                sale.saleDiscountValue <= 0L -> ""
                sale.saleDiscountType == DiscountType.AMOUNT -> Money.toInput(sale.saleDiscountValue)
                else -> Percent.formatBps(sale.saleDiscountValue.toInt())
            },
            pay = PayOption(sale.paymentMethod, sale.paymentAccountId, sale.paymentAccountName),
            tenderedInput = sale.amountTenderedMinor?.let(Money::toInput).orEmpty(),
            note = sale.note.orEmpty(),
            credit = sale.creditMinor > 0,
            paidNowInput = if (sale.creditMinor > 0 && sale.paidMinor > 0) Money.toInput(sale.paidMinor) else "",
            editingSaleId = sale.id,
            editingReceiptNumber = sale.receiptNumber,
            snapshotName = if (customer == null) sale.customerName else null,
            snapshotPhone = if (customer == null) sale.customerPhone else null,
        )
    }
    fun onTendered(value: String) = draft.update { it.copy(tenderedInput = value, problems = emptyList()) }
    fun onNote(value: String) = draft.update { it.copy(note = value.take(Validators.MAX_NOTE_LENGTH)) }

    fun completeSale() {
        val d = draft.value
        if (d.saving) return
        if (state.value.readOnly) {
            showMessage(R.string.error_read_only)
            return
        }
        val discount = discountOf(d)
        if (discount == null) {
            showMessage(R.string.error_invalid_amount)
            return
        }
        val cashTendered = d.paymentMethod == PaymentMethod.CASH && !d.credit && d.tenderedInput.isNotBlank()
        val tendered = if (cashTendered) Money.parse(d.tenderedInput) else null
        if (cashTendered && tendered == null) {
            showMessage(R.string.error_invalid_amount)
            return
        }
        val s = state.value
        if (d.credit) {
            if (s.customerName.isNullOrBlank()) {
                showMessage(R.string.pos_credit_need_customer)
                return
            }
            val paidNow = s.paidNowMinor
            if (paidNow == null || paidNow > s.totals.totalMinor) {
                showMessage(R.string.error_invalid_amount)
                return
            }
            if (s.creditMinor <= 0L) {
                showMessage(R.string.pos_credit_nothing)
                return
            }
        }
        val problems = SaleCalculator.validate(d.cart, discount, tendered)
        if (problems.isNotEmpty()) {
            draft.update { it.copy(problems = problems) }
            showMessage(problems.first().messageRes)
            return
        }
        draft.update { it.copy(saving = true) }
        launchSafe {
            val request = NewSaleRequest(
                customerId = d.customer?.id,
                customerName = d.customer?.name ?: d.snapshotName,
                customerPhone = d.customer?.phone ?: d.snapshotPhone,
                lines = d.cart,
                discount = discount,
                paymentMethod = d.paymentMethod,
                amountTenderedMinor = tendered,
                note = d.note,
                paymentAccountId = d.pay.accountId,
                paymentAccountName = d.pay.accountName,
                creditMinor = s.creditMinor,
            )
            val result = d.editingSaleId?.let { container.saleRepository.updateSale(it, request) }
                ?: container.saleRepository.completeSale(request)
            when (result) {
                is DataResult.Success -> {
                    draft.value = PosDraft(defaultStaffId = d.defaultStaffId)
                    _completed.tryEmit(result.data)
                }
                is DataResult.Failure -> {
                    draft.update { it.copy(saving = false) }
                    showMessage(result.error.messageRes)
                }
            }
        }.invokeOnCompletion { draft.update { it.copy(saving = false) } }
    }

    private fun discountOf(d: PosDraft): SaleDiscount? {
        if (d.discountInput.isBlank()) return SaleDiscount.NONE
        return when (d.discountType) {
            DiscountType.AMOUNT -> Money.parse(d.discountInput)?.let(SaleDiscount::amount)
            DiscountType.PERCENT -> Percent.parseBps(d.discountInput)?.let(SaleDiscount::percent)
        }
    }

    private fun StaffEntity?.commissionRate(): Int =
        if (this != null && salaryType.hasCommission) commissionBps else 0
}
