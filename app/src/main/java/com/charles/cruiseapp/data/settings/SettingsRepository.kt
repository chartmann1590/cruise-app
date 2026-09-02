package com.charles.cruiseapp.data.settings

import android.content.Context
import com.charles.cruiseapp.util.UnitSystem
import com.charles.cruiseapp.util.UnitUtils
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val context: Context) {
    fun getUnitSystem(): UnitSystem = UnitUtils.getUnitSystem(context)
    fun isMetric(): Boolean = UnitUtils.isMetric(context)
    fun setUnitSystem(system: UnitSystem) = UnitUtils.setUnitSystem(context, system)
    fun observeUnitSystem(): Flow<UnitSystem> = UnitUtils.observeUnitSystem(context)
    fun observeIsMetric(): Flow<Boolean> = UnitUtils.observeIsMetric(context)
}
