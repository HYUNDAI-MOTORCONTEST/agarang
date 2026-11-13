package com.hmgchallenge.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.hmgchallenge.app.R
import com.hmgchallenge.app.ui.theme.PleosTheme

internal enum class CareSymbol(@param:DrawableRes val outlined: Int, @param:DrawableRes val filled: Int) {
    FEED(R.drawable.ic_agarang_feed_outlined, R.drawable.ic_agarang_feed_filled),
    DIAPER(R.drawable.ic_agarang_diaper_outlined, R.drawable.ic_agarang_diaper_filled),
    SLEEP(R.drawable.ic_agarang_sleep_outlined, R.drawable.ic_agarang_sleep_filled),
    ROUTE(R.drawable.ic_agarang_route_outlined, R.drawable.ic_agarang_route_filled),
    CHECKLIST(R.drawable.ic_agarang_checklist_outlined, R.drawable.ic_agarang_checklist_filled),
    START(R.drawable.ic_agarang_start_outlined, R.drawable.ic_agarang_start_filled),
    CARE(R.drawable.ic_agarang_care_outlined, R.drawable.ic_agarang_care_filled),
    STOP(R.drawable.ic_agarang_stop_outlined, R.drawable.ic_agarang_stop_filled),
    CHECK(R.drawable.ic_agarang_check_outlined, R.drawable.ic_agarang_check_filled),
    CHEVRON(R.drawable.ic_agarang_chevron_outlined, R.drawable.ic_agarang_chevron_filled),
    HOME(R.drawable.ic_agarang_home_outlined, R.drawable.ic_agarang_home_filled),
    UNDO(R.drawable.ic_agarang_undo_outlined, R.drawable.ic_agarang_undo_filled),
    BABY(R.drawable.ic_agarang_baby_outlined, R.drawable.ic_agarang_baby_filled),
    SOOTHE(R.drawable.ic_agarang_soothe_outlined, R.drawable.ic_agarang_soothe_filled),
}

/** Original artwork traced to transparent vectors on the Pleos 48dp grid (docs/agarang/icons.md).
 * Selection uses the filled variant. The adjacent label and parent control expose meaning/state,
 * so the icon stays decorative: https://developer.android.com/develop/ui/compose/resources
 */
@Composable
internal fun CareGlyph(
    symbol: CareSymbol,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    selected: Boolean = false,
) {
    Icon(
        painter = painterResource(if (selected) symbol.filled else symbol.outlined),
        contentDescription = null,
        modifier = modifier.size(PleosTheme.dimens.iconSize),
        tint = tint,
    )
}
