package nd.max.data.datasources

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nd.max.core.hardware.HardwareDataSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HardwareDataSourceImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : HardwareDataSource(context)