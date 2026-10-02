package com.dtpos.salonmanager.presentation.navigation

import com.dtpos.salonmanager.presentation.messages.DuesScreen
import com.dtpos.salonmanager.presentation.messages.PromotionsScreen
import com.dtpos.salonmanager.presentation.support.SupportScreen
import com.dtpos.salonmanager.presentation.ai.AiAssistantScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dtpos.salonmanager.R
import com.dtpos.salonmanager.domain.model.ExpenseType
import com.dtpos.salonmanager.presentation.cash.CashCounterScreen
import com.dtpos.salonmanager.presentation.customers.CustomerDetailScreen
import com.dtpos.salonmanager.presentation.customers.CustomerEditScreen
import com.dtpos.salonmanager.presentation.customers.CustomerListScreen
import com.dtpos.salonmanager.presentation.dashboard.DashboardScreen
import com.dtpos.salonmanager.presentation.expenses.ExpenseCategoriesScreen
import com.dtpos.salonmanager.presentation.expenses.ExpenseEditScreen
import com.dtpos.salonmanager.presentation.expenses.ExpensesScreen
import com.dtpos.salonmanager.presentation.insights.InsightsScreen
import com.dtpos.salonmanager.presentation.lock.SecuredArea
import com.dtpos.salonmanager.presentation.reports.ReportsScreen
import com.dtpos.salonmanager.presentation.sales.PosScreen
import com.dtpos.salonmanager.presentation.sales.SaleDetailScreen
import com.dtpos.salonmanager.presentation.sales.SalesListScreen
import com.dtpos.salonmanager.presentation.services.ServicesScreen
import com.dtpos.salonmanager.presentation.settings.AboutScreen
import com.dtpos.salonmanager.presentation.settings.AccountScreen
import com.dtpos.salonmanager.presentation.settings.AppPreferencesScreen
import com.dtpos.salonmanager.presentation.settings.BackupScreen
import com.dtpos.salonmanager.presentation.settings.BusinessProfileScreen
import com.dtpos.salonmanager.presentation.settings.LicenseScreen
import com.dtpos.salonmanager.presentation.settings.PrinterSettingsScreen
import com.dtpos.salonmanager.presentation.settings.ReceiptSettingsScreen
import com.dtpos.salonmanager.presentation.settings.SecuritySettingsScreen
import com.dtpos.salonmanager.presentation.settings.SettingsScreen
import com.dtpos.salonmanager.presentation.staff.StaffDetailScreen
import com.dtpos.salonmanager.presentation.staff.StaffEditScreen
import com.dtpos.salonmanager.presentation.staff.StaffListScreen
import com.dtpos.salonmanager.presentation.targets.BudgetScreen
import com.dtpos.salonmanager.presentation.targets.TargetsScreen
import com.dtpos.salonmanager.services.security.ProtectedArea

private data class BottomItem(val route: String, val labelRes: Int, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Dashboard),
    BottomItem(Routes.SALES, R.string.nav_sales, Icons.Filled.PointOfSale),
    BottomItem(Routes.CUSTOMERS, R.string.nav_customers, Icons.Filled.People),
    BottomItem(Routes.EXPENSES, R.string.nav_expenses, Icons.Filled.Payments),
    BottomItem(Routes.REPORTS, R.string.nav_reports, Icons.Filled.BarChart),
)

@Composable
fun SalonMainScaffold(navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = currentRoute in Routes.topLevel
    val sound = com.dtpos.salonmanager.presentation.common.LocalAppContainer.current.soundEffects

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                if (currentRoute != item.route) sound.tap()
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.labelRes), maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(PaddingValues(bottom = padding.calculateBottomPadding()))) {
            SalonNavHost(navController)
        }
    }
}

@Composable
private fun SalonNavHost(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(navController = nav, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onNewSale = { nav.navigate(Routes.pos()) },
                onOpenSale = { nav.navigate(Routes.saleDetail(it)) },
                onNavigate = { nav.navigate(it) },
            )
        }
        composable(Routes.SALES) {
            SalesListScreen(
                onNewSale = { nav.navigate(Routes.pos()) },
                onOpenSale = { nav.navigate(Routes.saleDetail(it)) },
            )
        }
        composable(Routes.CUSTOMERS) {
            CustomerListScreen(
                onOpenCustomer = { nav.navigate(Routes.customerDetail(it)) },
                onAddCustomer = { nav.navigate(Routes.customerEdit()) },
            )
        }
        composable(Routes.EXPENSES) {
            SecuredArea(ProtectedArea.EXPENSES) {
                ExpensesScreen(
                    onAddExpense = { type -> nav.navigate(Routes.expenseEdit(type = type)) },
                    onEditExpense = { id, type -> nav.navigate(Routes.expenseEdit(id, type)) },
                    onManageCategories = { type -> nav.navigate(Routes.expenseCategories(type)) },
                )
            }
        }
        composable(Routes.REPORTS) {
            SecuredArea(ProtectedArea.REPORTS) {
                ReportsScreen(onOpenInsights = { nav.navigate(Routes.INSIGHTS) })
            }
        }

        composable(
            Routes.POS,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            PosScreen(
                initialCustomerId = entry.arguments?.getLong("customerId")?.takeIf { it > 0 },
                onBack = back,
                onSaleCompleted = { saleId ->
                    nav.navigate(Routes.saleDetail(saleId, fresh = true)) {
                        popUpTo(Routes.POS) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.SALE_DETAIL,
            arguments = listOf(
                navArgument("saleId") { type = NavType.LongType },
                navArgument("fresh") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { entry ->
            SaleDetailScreen(
                saleId = entry.arguments?.getLong("saleId") ?: 0L,
                isNewSale = entry.arguments?.getBoolean("fresh") ?: false,
                onBack = back,
                onNewSale = {
                    nav.navigate(Routes.pos()) { popUpTo(Routes.SALE_DETAIL) { inclusive = true } }
                },
                onOpenCustomer = { nav.navigate(Routes.customerDetail(it)) },
            )
        }

        composable(
            Routes.CUSTOMER_DETAIL,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("customerId") ?: 0L
            CustomerDetailScreen(
                customerId = id,
                onBack = back,
                onEdit = { nav.navigate(Routes.customerEdit(id)) },
                onOpenSale = { nav.navigate(Routes.saleDetail(it)) },
                onNewSale = { nav.navigate(Routes.pos(id)) },
            )
        }
        composable(
            Routes.CUSTOMER_EDIT,
            arguments = listOf(navArgument("customerId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            CustomerEditScreen(
                customerId = entry.arguments?.getLong("customerId")?.takeIf { it > 0 },
                onBack = back,
                onDeleted = { if (!nav.popBackStack(Routes.CUSTOMERS, inclusive = false)) nav.popBackStack() },
            )
        }

        composable(Routes.SERVICES) { ServicesScreen(onBack = back) }

        composable(Routes.STAFF) {
            StaffListScreen(
                onBack = back,
                onOpenStaff = { nav.navigate(Routes.staffDetail(it)) },
                onAddStaff = { nav.navigate(Routes.staffEdit()) },
            )
        }
        composable(Routes.STAFF_DETAIL, arguments = listOf(navArgument("staffId") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("staffId") ?: 0L
            StaffDetailScreen(staffId = id, onBack = back, onEdit = { nav.navigate(Routes.staffEdit(id)) })
        }
        composable(
            Routes.STAFF_EDIT,
            arguments = listOf(navArgument("staffId") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            StaffEditScreen(
                staffId = entry.arguments?.getLong("staffId")?.takeIf { it > 0 },
                onBack = back,
                onDeleted = { if (!nav.popBackStack(Routes.STAFF, inclusive = false)) nav.popBackStack() },
            )
        }

        composable(
            Routes.EXPENSE_EDIT,
            arguments = listOf(
                navArgument("expenseId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("type") { type = NavType.StringType; defaultValue = ExpenseType.BUSINESS.name },
            ),
        ) { entry ->
            SecuredArea(ProtectedArea.EXPENSES) {
                ExpenseEditScreen(
                    expenseId = entry.arguments?.getLong("expenseId")?.takeIf { it > 0 },
                    initialType = entry.arguments?.getString("type").toExpenseType(),
                    onBack = back,
                    onManageCategories = { nav.navigate(Routes.expenseCategories(it)) },
                )
            }
        }
        composable(
            Routes.EXPENSE_CATEGORIES,
            arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = ExpenseType.BUSINESS.name }),
        ) { entry ->
            SecuredArea(ProtectedArea.EXPENSES) {
                ExpenseCategoriesScreen(type = entry.arguments?.getString("type").toExpenseType(), onBack = back)
            }
        }

        composable(Routes.CASH) { CashCounterScreen(onBack = back) }
        composable(Routes.TARGETS) { TargetsScreen(onBack = back, onOpenBudget = { nav.navigate(Routes.BUDGET) }) }
        composable(Routes.BUDGET) { SecuredArea(ProtectedArea.REPORTS) { BudgetScreen(onBack = back) } }
        composable(Routes.DUES) { DuesScreen(onBack = back) }
        composable(Routes.PROMOTIONS) { PromotionsScreen(onBack = back) }
        composable(Routes.SUPPORT) { SupportScreen(onBack = back) }
        composable(Routes.AI) { SecuredArea(ProtectedArea.REPORTS) { AiAssistantScreen(onBack = back, onOpenInsights = { nav.navigate(Routes.INSIGHTS) }) } }
        composable(Routes.INSIGHTS) { SecuredArea(ProtectedArea.REPORTS) { InsightsScreen(onBack = back) } }

        composable(Routes.SETTINGS) {
            SecuredArea(ProtectedArea.SETTINGS) { SettingsScreen(onBack = back, onNavigate = { nav.navigate(it) }) }
        }
        composable(Routes.BUSINESS_PROFILE) { SecuredArea(ProtectedArea.SETTINGS) { BusinessProfileScreen(onBack = back) } }
        composable(Routes.RECEIPT_SETTINGS) { SecuredArea(ProtectedArea.SETTINGS) { ReceiptSettingsScreen(onBack = back) } }
        composable(Routes.PRINTER) { SecuredArea(ProtectedArea.SETTINGS) { PrinterSettingsScreen(onBack = back) } }
        composable(Routes.BACKUP) { SecuredArea(ProtectedArea.SETTINGS) { BackupScreen(onBack = back) } }
        composable(Routes.SECURITY) { SecuredArea(ProtectedArea.SETTINGS) { SecuritySettingsScreen(onBack = back) } }
        composable(Routes.LICENSE) { SecuredArea(ProtectedArea.SETTINGS) { LicenseScreen(onBack = back) } }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
        composable(Routes.ACCOUNT) { AccountScreen(onBack = back) }
        composable(Routes.PREFERENCES) { AppPreferencesScreen(onBack = back) }
    }
}

private fun String?.toExpenseType(): ExpenseType =
    ExpenseType.entries.firstOrNull { it.name == this } ?: ExpenseType.BUSINESS
