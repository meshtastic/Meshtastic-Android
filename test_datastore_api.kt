import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import okio.Path.Companion.toPath
fun test() {
    PreferenceDataStoreFactory.createWithPath(produceFile = { "test".toPath() })
}
