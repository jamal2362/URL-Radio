/*
 * Color.kt
 * Brand colour roles for the URL Radio Compose theme
 *
 * The values below are carried over from the app's existing resource files
 * (res/values/colors.xml, res/values-night/colors.xml, res/values/styles.xml and
 * res/values-night/styles.xml) so that the pre-Compose look is preserved on
 * devices without dynamic colour. Every role not listed here keeps the Material 3
 * default from lightColorScheme()/darkColorScheme().
 *
 * This file is part of URL Radio
 * Licensed under the MIT-License
 * http://opensource.org/licenses/MIT
 */

package com.jamal2367.urlradio.ui.theme

import androidx.compose.ui.graphics.Color

/* ---- Light ---- */

// res/values/styles.xml: colorPrimary / colorAccent
val BrandPrimaryLight = Color(0xFF495D92)
val BrandOnPrimaryLight = Color(0xFFFFFFFF)

// res/values/colors.xml: search_result_background_selected / icon_lightweight_background
val BrandPrimaryContainerLight = Color(0xFFDAE2FF)
val BrandOnPrimaryContainerLight = Color(0xFF00174B)

val BrandSecondaryLight = Color(0xFF585E71)
val BrandSecondaryContainerLight = Color(0xFFC0C6DD)
val BrandOnSecondaryContainerLight = Color(0xFF151B2C)

// res/values/colors.xml: list_card_background
val BrandSurfaceLight = Color(0xFFFEFBFF)
// res/values/colors.xml: text_default
val BrandOnSurfaceLight = Color(0xFF595959)
// res/values/colors.xml: list_card_cover_background
val BrandSurfaceVariantLight = Color(0xFFE7E0EC)
// res/values/colors.xml: text_lightweight
val BrandOnSurfaceVariantLight = Color(0xFF45464F)
val BrandOutlineLight = Color(0xFFC0C6DD)

// res/values/colors.xml: list_card_delete_background
val BrandErrorLight = Color(0xFFB3261E)
val BrandOnErrorLight = Color(0xFFFFFFFF)

/* ---- Dark ---- */

// res/values-night/styles.xml: colorPrimary / colorOnPrimary
val BrandPrimaryDark = Color(0xFFDAE2FF)
val BrandOnPrimaryDark = Color(0xFF182E60)

val BrandPrimaryContainerDark = Color(0xFF314677)
val BrandOnPrimaryContainerDark = Color(0xFFDAE2FF)

val BrandSecondaryDark = Color(0xFFC0C6DD)
// res/values-night/colors.xml: icon_lightweight_background
val BrandSecondaryContainerDark = Color(0xFF585E71)
val BrandOnSecondaryContainerDark = Color(0xFFDCE1F9)

// res/values-night/colors.xml: list_card_background
val BrandSurfaceDark = Color(0xFF1B1B1F)
// res/values-night/colors.xml: text_default
val BrandOnSurfaceDark = Color(0xFFFFFFFF)
// res/values-night/colors.xml: list_card_cover_background
val BrandSurfaceVariantDark = Color(0xFF49454F)
// res/values-night/colors.xml: text_lightweight
val BrandOnSurfaceVariantDark = Color(0xFFC5C6D0)
val BrandOutlineDark = Color(0xFF585E71)

// res/values-night/colors.xml: list_card_delete_background / list_card_delete_icon
val BrandErrorDark = Color(0xFFF2B8B5)
val BrandOnErrorDark = Color(0xFF601410)

/* ---- Fixed, non-themed ---- */

// res/values/colors.xml: splashBackgroundColor
val SplashBackground = Color(0xFF1D3E66)
