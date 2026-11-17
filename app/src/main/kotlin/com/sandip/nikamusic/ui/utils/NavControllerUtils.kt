// NavControllerUtils.kt

package com.sandip.nikamusic.ui.utils

import androidx.navigation.NavController
import com.sandip.nikamusic.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}
