package com.android.camera2.takeaphoto.ui
import org.junit.Test
import androidx.camera.viewfinder.view.ViewfinderView
class DummyTest {
    @Test
    fun printMethods() {
        ViewfinderView::class.java.methods.forEach {
            println(it.name)
        }
    }
}