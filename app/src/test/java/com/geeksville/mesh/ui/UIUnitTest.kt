package com.geeksville.mesh.ui

import com.geeksville.mesh.model.getInitials
import org.junit.Assert
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class UIUnitTest {
    @Test
    fun initialsGood() {
        Assert.assertEquals("KH", getInitials("Kevin Hester"))
        Assert.assertEquals("KHL", getInitials("  Kevin Hester Lesser Cat  "))
        Assert.assertEquals("", getInitials("  "))
        Assert.assertEquals("gks", getInitials("geeksville"))
        Assert.assertEquals("gee", getInitials("geek"))
        Assert.assertEquals("gks", getInitials("geeks"))
    }
}
