package nd.max.ui.mainscreens

import org.junit.Assert.assertNull

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFocusModelTest {

    @Test
    fun `without a measurement the storage is unknown and never shows a warning`() {
        assertEquals(HomeStorageState.UNKNOWN, HomeFocusModel.storageState(0f, 0f))
        assertFalse(HomeFocusModel.storageCardVisible(HomeStorageState.UNKNOWN, hidden = false))
    }

    @Test
    fun `a disk with less than a tenth free is full and a roomy disk is healthy`() {
        assertEquals(HomeStorageState.FULL, HomeFocusModel.storageState(479f, 471.6f))
        assertEquals(HomeStorageState.HEALTHY, HomeFocusModel.storageState(479f, 200f))
    }

    @Test
    fun `the card shows only when the disk is full and the user has not hidden it`() {
        assertTrue(HomeFocusModel.storageCardVisible(HomeStorageState.FULL, hidden = false))
        assertFalse(HomeFocusModel.storageCardVisible(HomeStorageState.FULL, hidden = true))
        assertFalse(HomeFocusModel.storageCardVisible(HomeStorageState.HEALTHY, hidden = false))
    }

    @Test
    fun `a hide is cleared only by a measured healthy disk, never by an unknown one`() {
        assertTrue(HomeFocusModel.clearsHide(HomeStorageState.HEALTHY))
        assertFalse(HomeFocusModel.clearsHide(HomeStorageState.UNKNOWN))
        assertFalse(HomeFocusModel.clearsHide(HomeStorageState.FULL))
    }
    @Test
    fun `one rule chooses the warning and whether it rises - heat, then storage, then memory`() {
        assertEquals(HomeFocusKind.HEAT, HomeFocusModel.focusKind(46, 0.95f, storageVisible = true))
        assertEquals(HomeFocusKind.STORAGE, HomeFocusModel.focusKind(30, 0.95f, storageVisible = true))
        assertEquals(HomeFocusKind.MEMORY, HomeFocusModel.focusKind(30, 0.95f, storageVisible = false))
        assertNull(HomeFocusModel.focusKind(30, 0.50f, storageVisible = false))
    }

}
