package com.example.investa.navigation

enum class AppScreen { HOME, ASSETS, CASH, REPORTS, SETTINGS, THEME, LANGUAGE, EXCHANGE_RATE, PRIMARY_CURRENCY, NUMBER_FORMAT, ABOUT, DETAIL, ADD, EDIT }

enum class ScreenTransition { FORWARD, BACKWARD }

internal val AppScreen.isMainScreen: Boolean
    get() = when (this) {
        AppScreen.HOME,
        AppScreen.ASSETS,
        AppScreen.CASH,
        AppScreen.REPORTS,
        AppScreen.SETTINGS -> true
        else -> false
    }
