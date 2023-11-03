package kr.com.chappiet.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import kotlinx.coroutines.CoroutineScope
import kr.com.chappiet.BuildConfig
import kr.com.chappiet.data.model.GooglePaymentProduct
import kr.com.chappiet.ui.activity.ActionType
import kr.com.chappiet.ui.navigation.ChappieTRoute
import kr.com.chappiet.ui.navigation.ChappieTTopLevelDestination
import kr.com.chappiet.ui.navigation.NavigationActions
import kr.com.chappiet.ui.navigation.TOP_LEVEL_DESTINATIONS
import kr.com.chappiet.ui.theme.dimmed_80
import kr.com.chappiet.ui.theme.primary
import kr.com.chappiet.ui.theme.white
import kr.com.chappiet.ui.utils.ChappieTNavigationType
import kr.com.chappiet.ui.utils.DevicePosture
import kr.com.chappiet.ui.utils.isBookPosture
import kr.com.chappiet.ui.utils.isSeparating
import kr.com.chappiet.vm.ChappieTAppTranslateSettings
import kr.com.chappiet.vm.ChappieTHomeUIState
import kr.com.chappiet.vm.ChappieTPopUpUIState
import kr.com.chappiet.vm.MainViewModel
import kr.com.chappiet.vm.RequestToActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChappieTApp(
    viewModel: MainViewModel,
    windowSize: WindowSizeClass,
    displayFeatures: List<DisplayFeature>,
    chappieTHomeUIState: ChappieTHomeUIState,
    popUpUIState: ChappieTPopUpUIState,
    appTranslateSettings: ChappieTAppTranslateSettings,
    requestByViewModel: RequestToActivity,
    action: (ActionType) -> Unit,
    onClickPaymentBtn: (GooglePaymentProduct) -> Unit,
    onSheetItemSelected: (Pair<Any, String>) -> Unit,
    navigateToWebRequest: (String) -> Unit,
    bottomSheetState: SheetState,
    scope: CoroutineScope,
) {
    val foldingFeature = displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
    val foldingDevicePosture = when {
        isBookPosture(foldingFeature) ->
            DevicePosture.BookPosture(foldingFeature.bounds)

        isSeparating(foldingFeature) ->
            DevicePosture.Separating(foldingFeature.bounds, foldingFeature.orientation)

        else -> DevicePosture.NormalPosture
    }
    val navigationType: ChappieTNavigationType = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            ChappieTNavigationType.BOTTOM_NAVIGATION
        }

        WindowWidthSizeClass.Medium -> {
            ChappieTNavigationType.BOTTOM_NAVIGATION
        }

        WindowWidthSizeClass.Expanded -> {
            ChappieTNavigationType.BOTTOM_NAVIGATION
        }

        else -> {
            ChappieTNavigationType.BOTTOM_NAVIGATION
        }
    }

    ChappieTNavigationWrapper(
        viewModel = viewModel,
        navigationType = navigationType,
        displayFeatures = displayFeatures,
        chappieTHomeUIState = chappieTHomeUIState,
        popUpUIState = popUpUIState,
        appTranslateSettings = appTranslateSettings,
        requestByViewModel = requestByViewModel,
        action = action,
        onClickPaymentBtn = onClickPaymentBtn,
        navigateToWebRequest = navigateToWebRequest,
        onSheetItemSelected = onSheetItemSelected,
        bottomSheetState = bottomSheetState,
        scope = scope,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChappieTNavigationWrapper(
    viewModel: MainViewModel,
    navigationType: ChappieTNavigationType,
    displayFeatures: List<DisplayFeature>,
    chappieTHomeUIState: ChappieTHomeUIState,
    popUpUIState: ChappieTPopUpUIState,
    appTranslateSettings: ChappieTAppTranslateSettings,
    requestByViewModel: RequestToActivity,
    action: (ActionType) -> Unit,
    onClickPaymentBtn: (GooglePaymentProduct) -> Unit,
    onSheetItemSelected: (Pair<Any, String>) -> Unit,
    navigateToWebRequest: (String) -> Unit,
    bottomSheetState: SheetState,
    scope: CoroutineScope,
) {
    val navController = rememberNavController()
    val navigationActions = remember(navController) {
        NavigationActions(navController)
    }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val selectedDestination =
        navBackStackEntry?.destination?.route ?: ChappieTRoute.REAL_TIME_TRANSLATE

    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (TOP_LEVEL_DESTINATIONS.map { it.route }.contains(destination.route)) {
                viewModel.setUiState(showNavigation = true)
            } else {
                viewModel.setUiState(showNavigation = false)
            }
            when (destination.route) {
                ChappieTRoute.CHARGE_CREDIT -> {
                    action(ActionType.GET_PRODUCT_DETAIL)
                }

                ChappieTRoute.ADD_API_KEY -> {
                    viewModel.setUiState(addApiKeyStep = 1)
                }
            }
        }
    }
    LaunchedEffect(requestByViewModel) {
        if(requestByViewModel.isHandled) { return@LaunchedEffect }
        when (requestByViewModel.requestType) {
            RequestToActivity.RequestType.REQUEST_SIGN_UP -> navigationActions.navigateTo(
                ChappieTRoute.SIGN_UP
            )
            else -> {}
        }
        viewModel.setRequest(isHandled = true)
    }

    if (navigationType == ChappieTNavigationType.BOTTOM_NAVIGATION) {
        ChappieTAppContent(
            viewModel = viewModel,
            navigationType = navigationType,
            displayFeatures = displayFeatures,
            chappieTHomeUIState = chappieTHomeUIState,
            appTranslateSettings = appTranslateSettings,
            popUpUIState = popUpUIState,
            requestByViewModel = requestByViewModel,
            navController = navController,
            action = action,
            selectedDestination = selectedDestination,
            onClickPaymentBtn = onClickPaymentBtn,
            navigateToTopLevelDestination = navigationActions::navigateTo,
            navigateToWebRequest = navigateToWebRequest,
            navigateTo = navigationActions::navigateTo,
            navigateToBackStack = navigationActions::popBackStack,
            bottomSheetState = bottomSheetState,
            scope = scope,
            onSheetItemSelected = onSheetItemSelected,
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChappieTAppContent(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navigationType: ChappieTNavigationType,
    displayFeatures: List<DisplayFeature>,
    chappieTHomeUIState: ChappieTHomeUIState,
    popUpUIState: ChappieTPopUpUIState,
    appTranslateSettings: ChappieTAppTranslateSettings,
    requestByViewModel: RequestToActivity,
    navController: NavHostController,
    action: (ActionType) -> Unit,
    onClickPaymentBtn: (GooglePaymentProduct) -> Unit,
    onSheetItemSelected: (Pair<Any, String>) -> Unit,
    navigateToTopLevelDestination: (ChappieTTopLevelDestination) -> Unit,
    navigateToWebRequest: (String) -> Unit,
    navigateTo: (String, Boolean) -> Unit,
    navigateToBackStack: () -> Unit,
    bottomSheetState: SheetState,
    scope: CoroutineScope,
    selectedDestination: String,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(white)
        ) {
            ChappieTNavHost(
                viewModel = viewModel,
                navController = navController,
                displayFeatures = displayFeatures,
                chappieTHomeUIState = chappieTHomeUIState,
                appTranslateSettings = appTranslateSettings,
                popUpUIState = popUpUIState,
                requestByViewModel = requestByViewModel,
                modifier = Modifier.weight(1f),
                action = action,
                onClickPaymentBtn = onClickPaymentBtn,
                navigateToTopLevelDestination = navigateToTopLevelDestination,
                navigateToWebRequest = navigateToWebRequest,
                navigateTo = navigateTo,
                navigateToBackStack = navigateToBackStack,
                bottomSheetState = bottomSheetState,
                scope = scope
            )
            LaunchedEffect(bottomSheetState.currentValue) {
                if (bottomSheetState.currentValue == SheetValue.Hidden) {
                    viewModel.suspendSetUiState(sheetItemList = null)
                }
            }
            ModalBottomSheetLayoutSelector(
                sheetState = bottomSheetState,
                itemList = chappieTHomeUIState.sheetItemList,
                onItemSelected = onSheetItemSelected
            )
            AnimatedVisibility(visible = popUpUIState.showPopup) {
                PopUpDialog(
                    open = popUpUIState.showPopup,
                    title = popUpUIState.popUpTitle,
                    content = popUpUIState.popUpContent,
                    annotatedContent = popUpUIState.popUpAnnotatedString,
                    ok = popUpUIState.popUpOk,
                    cancel = popUpUIState.popUpCancel,
                    cancelable = popUpUIState.cancelable,
                    properties = popUpUIState.popupProperties ?: DialogProperties(),
                    scope = scope,
                    onClickOK = popUpUIState.onClickOK,
                    onClickCancel = popUpUIState.onClickCancel,
                    onDismissRequest = { viewModel.setPopUpUiState(showPopup = false) }
                )
            }
            AnimatedVisibility(
                visible = navigationType == ChappieTNavigationType.BOTTOM_NAVIGATION
                        && chappieTHomeUIState.showNavigation
            ) {
                ChappieTBottomNavigationBar(
                    selectedDestination = selectedDestination,
                    navigateToTopLevelDestination = navigateToTopLevelDestination
                )
            }
        }
        if (chappieTHomeUIState.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(dimmed_80)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                    color = primary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChappieTNavHost(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel,
    navController: NavHostController,
    displayFeatures: List<DisplayFeature>,
    chappieTHomeUIState: ChappieTHomeUIState,
    popUpUIState: ChappieTPopUpUIState,
    appTranslateSettings: ChappieTAppTranslateSettings,
    requestByViewModel: RequestToActivity,
    action: (ActionType) -> Unit,
    onClickPaymentBtn: (GooglePaymentProduct) -> Unit,
    navigateToTopLevelDestination: (ChappieTTopLevelDestination) -> Unit,
    navigateToWebRequest: (String) -> Unit,
    navigateTo: (String, Boolean) -> Unit,
    navigateToBackStack: () -> Unit,
    bottomSheetState: SheetState,
    scope: CoroutineScope,
) {
    NavHost(
        modifier = modifier,
        navController = navController,
        startDestination = ChappieTRoute.REAL_TIME_TRANSLATE
    ) {
        composable(ChappieTRoute.REAL_TIME_TRANSLATE) {
            ChappieTAppTranslate(
                viewModel = viewModel,
                chappieTHomeUIState = chappieTHomeUIState,
                appTranslateSettings = appTranslateSettings,
                action = action,
                navigateToTopLevelDestination = navigateToTopLevelDestination,
                navigateTo = navigateTo
            )
        }
        composable(ChappieTRoute.TRANSLATE_HISTORY) {
            EmptyComingSoon(
                chappieTHomeUIState = chappieTHomeUIState,
                action = action,
                navigateToTopLevelDestination = navigateToTopLevelDestination,
                navigateTo = navigateTo
            )
        }
        composable(ChappieTRoute.ADVANCED_TRANSLATE_SETTINGS) {
            AdvancedTranslateSettings(
                viewModel = viewModel,
                chappieTHomeUIState = chappieTHomeUIState,
                appTranslateSettings = appTranslateSettings,
                navigateToBackStack = navigateToBackStack,
                navigateToWebRequest = navigateToWebRequest
            )
        }
        composable(ChappieTRoute.VIDEO_SUMMARY) {
            EmptyComingSoon(
                chappieTHomeUIState = chappieTHomeUIState,
                navigateToTopLevelDestination = navigateToTopLevelDestination,
                action = action,
                navigateTo = navigateTo
            )
        }
        composable(ChappieTRoute.SETTINGS) {
            ChappieTAppSettings(
                chappieTHomeUIState = chappieTHomeUIState,
                action = action,
                navigateToWebRequest = navigateToWebRequest,
                navigateTo = navigateTo,
            )
        }
        composable(ChappieTRoute.CHARGE_CREDIT) {
            ChappieTChargeCredit(
                viewModel = viewModel,
                chappieTHomeUIState = chappieTHomeUIState,
                action = action,
                onClickPaymentBtn = onClickPaymentBtn,
                navigateToWebRequest = navigateToWebRequest,
                navigateTo = navigateTo,
                navigateToBackStack = navigateToBackStack,
                bottomSheetState = bottomSheetState,
                scope = scope
            )
        }
        composable(ChappieTRoute.API_KEYS) {
            ChappieTChargeCredit(
                viewModel = viewModel,
                chappieTHomeUIState = chappieTHomeUIState,
                action = action,
                firstSelected = "API",
                onClickPaymentBtn = onClickPaymentBtn,
                navigateToWebRequest = navigateToWebRequest,
                navigateTo = navigateTo,
                navigateToBackStack = navigateToBackStack,
                bottomSheetState = bottomSheetState,
                scope = scope
            )
        }
        composable(ChappieTRoute.APP_INFO) {
            ChappieTAppInfoScreen(navigateToWebRequest = navigateToWebRequest,
                navigateToBackStack = navigateToBackStack)
        }
        composable(ChappieTRoute.SIGN_UP) {
            ChappieTSignUp(
                viewModel = viewModel,
                chappieTHomeUIState = chappieTHomeUIState,
                requestByViewModel = requestByViewModel,
                action = action,
                navigateToBackStack = navigateToBackStack,
                navigateToWebRequest = navigateToWebRequest
            )
        }
        composable(ChappieTRoute.ADD_API_KEY) {
            RegisterApiKey(
                viewModel,
                chappieTHomeUIState,
                action,
                navigateToWebRequest,
                navigateToBackStack
            )
        }
        composable(ChappieTRoute.PROMOTIONS) {
            ChappieTPromotions(
                viewModel = viewModel,
                chappieTHomeUIState = chappieTHomeUIState,
                action = action,
                navigateToWebRequest = navigateToWebRequest,
                navigateTo = navigateTo,
                navigateToBackStack = navigateToBackStack
            )
        }
        if (BuildConfig.DEBUG) {
            composable(ChappieTRoute.DEVELOPER) {
                ChappieTDeveloperScreen(
                    viewModel = viewModel,
                    chappieTHomeUIState = chappieTHomeUIState,
                    appTranslateSettings = appTranslateSettings,
                    navigateTo = navigateTo,
                    navigateToBackStack = navigateToBackStack,
                    navigateToWebRequest = navigateToWebRequest,
                    scope = scope
                )
            }
        }
    }
}

@Composable
fun colorsByTheme(light: Color, dark: Color): Color = if (isSystemInDarkTheme()) {
    dark
} else {
    light
}