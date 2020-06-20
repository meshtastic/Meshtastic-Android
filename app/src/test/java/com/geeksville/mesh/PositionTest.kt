package com.geeksville.mesh


import org.junit.Assert
import org.junit.Test

class PositionTest {
    @Test
    fun degGood() {
        Assert.assertEquals(Position.degI(89.0), 890000000)
        Assert.assertEquals(Position.degI(-89.0), -890000000)

        Assert.assertEquals(Position.degD(Position.degI(89.0)), 89.0, 0.01)
        Assert.assertEquals(Position.degD(Position.degI(-89.0)), -89.0, 0.01)
    }


}
