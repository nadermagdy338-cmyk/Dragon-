/*
 * Copyright (C) 2026-2027 MaxManager contributors
 *
 * Licensed under the Apache License, Version 2.0.
 */
package nd.max.ui.subscreens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import nd.max.R
import nd.max.ui.component.PrivilegePanel
import nd.max.ui.design.MaxScreen

/**
 * شاشة «الوصول والامتيازات» (`AR-20`) — نفس لوحة شاشة البداية، بسياق إعدادات.
 *
 * تعتمد هيكل الصفحة المشترك [MaxScreen] (شريط علوي واحد · هامش واحد · حالة/بانر موحّدان)
 * بدل أن تبني `Scaffold` خاصًّا بها.
 */
@Composable
fun PrivilegeScreen(navController: NavController) {
    MaxScreen(
        title = stringResource(R.string.max_privilege_title),
        onBack = { navController.navigateUp() },
    ) {
        PrivilegePanel()
    }
}
