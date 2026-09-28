package com.dtpos.salonmanager.presentation.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.core.di.AppContainer
import com.dtpos.salonmanager.core.validation.FieldResult
import com.dtpos.salonmanager.core.validation.Validators
import com.dtpos.salonmanager.data.database.entities.ExpenseCategoryEntity
import com.dtpos.salonmanager.data.repository.DataError
import com.dtpos.salonmanager.data.repository.DataResult
import com.dtpos.salonmanager.domain.model.BudgetGroup
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.presentation.common.BaseViewModel
import com.dtpos.salonmanager.presentation.common.appViewModel
import com.dtpos.salonmanager.presentation.common.labelRes
import com.dtpos.salonmanager.presentation.common.messageRes
import com.dtpos.salonmanager.presentation.components.DropdownField
import com.dtpos.salonmanager.presentation.components.FormTextField
import com.dtpos.salonmanager.presentation.components.MessageEffect
import com.dtpos.salonmanager.presentation.components.SalonTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ExpenseCategoriesViewModel(container: AppContainer, private val type: ExpenseType) : BaseViewModel() {
    private val repo = container.expenseRepository
    val categories: StateFlow<List<ExpenseCategoryEntity>> = repo.observeCategories(type)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(name: String, group: BudgetGroup?, id: Long?, onDone: () -> Unit) {
        val valid = Validators.requiredName(name, 40)
        if (valid !is FieldResult.Valid) {
            showMessage(valid.errorOrNull!!.messageRes)
            return
        }
        launchSafe {
            when (val result = repo.saveCategory(valid.value, type, group, id)) {
                is DataResult.Success -> onDone()
                is DataResult.Failure -> showMessage(result.error.messageRes)
            }
        }
    }

    fun toggle(category: ExpenseCategoryEntity) = launchSafe { repo.setCategoryActive(category.id, !category.isActive) }

    fun delete(category: ExpenseCategoryEntity, onDone: () -> Unit) = launchSafe {
        when (val result = repo.deleteCategory(category.id)) {
            is DataResult.Success -> onDone()
            is DataResult.Failure -> showMessage(if (result.error == DataError.IN_USE) R.string.expenses_category_in_use else result.error.messageRes)
        }
    }
}

@Composable
fun ExpenseCategoriesScreen(type: ExpenseType, onBack: () -> Unit) {
    val vm = appViewModel(key = "categories_${type.name}") { ExpenseCategoriesViewModel(it, type) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<ExpenseCategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    MessageEffect(vm.messages, snackbar)

    Scaffold(
        topBar = { SalonTopBar(stringResource(R.string.expenses_categories), onBack = onBack, subtitle = stringResource(type.labelRes)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.expenses_add_category)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { category ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { editing = category },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    ListItem(
                        headlineContent = { Text(ExpenseCategoryName(category)) },
                        supportingContent = {
                            val group = category.budgetGroup
                            Text(
                                if (group != null) stringResource(R.string.expenses_budget_line, stringResource(group.labelRes))
                                else stringResource(if (category.systemKey != null) R.string.expenses_builtin else R.string.expenses_custom),
                            )
                        },
                        trailingContent = { Switch(checked = category.isActive, onCheckedChange = { vm.toggle(category) }) },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        val current = editing
        CategoryDialog(
            type = type,
            category = current,
            onSave = { name, group -> vm.save(name, group, current?.id) { creating = false; editing = null } },
            onDelete = if (current != null) {
                { vm.delete(current) { editing = null } }
            } else {
                null
            },
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
}

@Composable
private fun CategoryDialog(
    type: ExpenseType,
    category: ExpenseCategoryEntity?,
    onSave: (String, BudgetGroup?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var group by remember { mutableStateOf(category?.budgetGroup) }
    val groups: List<BudgetGroup?> = if (type == ExpenseType.PERSONAL) listOf(null, BudgetGroup.FOOD, BudgetGroup.CHILDREN) else listOf(null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category == null) R.string.expenses_add_category else R.string.expenses_edit_category)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField(name, { name = it }, stringResource(R.string.field_name), capitalization = KeyboardCapitalization.Words)
                if (groups.size > 1) {
                    DropdownField(
                        label = stringResource(R.string.expenses_budget_group),
                        options = groups,
                        selected = group,
                        optionLabel = { it?.let { g -> stringResource(g.labelRes) } ?: stringResource(R.string.expenses_no_budget_group) },
                        onSelected = { group = it },
                    )
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, group) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
