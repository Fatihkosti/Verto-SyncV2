package com.verto.app.feature.expenses.di

import com.verto.app.feature.expenses.application.quickaction.ExpensesQuickActionProvider
import com.verto.feature.dashboard.api.QuickActionProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ExpensesQuickActionModule {
    @Provides
    @IntoSet
    @Singleton
    fun provideExpensesQuickActionProvider(
        provider: ExpensesQuickActionProvider,
    ): QuickActionProvider = provider
}
