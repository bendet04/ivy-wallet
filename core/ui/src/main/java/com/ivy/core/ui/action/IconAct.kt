package com.ivy.core.ui.action

import android.content.Context
import androidx.annotation.DrawableRes
import com.ivy.base.R
import com.ivy.core.domain.action.Action
import com.ivy.core.ui.data.icon.IconSize
import com.ivy.core.ui.data.icon.ItemIcon
import com.ivy.data.ItemIconId
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class IconAct @Inject constructor(
    @ApplicationContext
    private val appContext: Context
) : Action<IconAct.Input, ItemIcon>() {
    data class Input(
        val iconId: ItemIconId?,
        val defaultTo: DefaultTo
    )

    override suspend fun Input.willDo(): ItemIcon {
        fun Input.default(): ItemIcon = ItemIcon.Sized(
            iconS = when (defaultTo) {
                DefaultTo.Account -> R.drawable.ic_custom_account_s
                DefaultTo.Category -> R.drawable.ic_custom_category_s
            },
            iconM = when (defaultTo) {
                DefaultTo.Account -> R.drawable.ic_custom_account_m
                DefaultTo.Category -> R.drawable.ic_custom_category_m
            },
            iconL = when (defaultTo) {
                DefaultTo.Account -> R.drawable.ic_custom_account_l
                DefaultTo.Category -> R.drawable.ic_custom_category_l
            },
            iconId = iconId
        )

        fun Input.unknown(): ItemIcon =
            getIcon(iconId = iconId)?.let { iconRes ->
                ItemIcon.Unknown(
                    icon = iconRes,
                    iconId = iconId,
                )
            } ?: default()


        val iconS = getSizedIcon(iconId = iconId, size = IconSize.S) ?: return unknown()
        val iconM = getSizedIcon(iconId = iconId, size = IconSize.M) ?: return unknown()
        val iconL = getSizedIcon(iconId = iconId, size = IconSize.L) ?: return unknown()

        return ItemIcon.Sized(
            iconS = iconS,
            iconM = iconM,
            iconL = iconL,
            iconId = iconId,
        )
    }

    @DrawableRes
    fun getSizedIcon(
        iconId: ItemIconId?,
        size: IconSize,
    ): Int? = iconId?.let {
        getDrawableByName(
            fileName = "ic_custom_${normalize(iconId)}_${size.value}"
        )
    }

    @DrawableRes
    private fun getIcon(
        iconId: ItemIconId?
    ): Int? = iconId?.let {
        getDrawableByName(
            fileName = normalize(iconId)
        )
    }

    @DrawableRes
    private fun getDrawableByName(fileName: String): Int? = try {
        appContext.resources.getIdentifier(
            fileName,
            "drawable",
            appContext.packageName
        ).takeIf { it != 0 }
    } catch (e: Exception) {
        null
    }

    private fun normalize(iconId: ItemIconId): String = iconId
        .replace(" ", "")
        .trim()
        .lowercase()
}

enum class DefaultTo {
    Account,
    Category
}