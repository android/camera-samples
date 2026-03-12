package com.android.camera2.takeaphoto.ui
import org.junit.Test
import androidx.camera.viewfinder.core.ViewfinderSurfaceRequest
class DummyTest {
    @Test
    fun printMethods() {
        val s = ViewfinderSurfaceRequest::class.java.methods.joinToString("\\n") {
            it.name + ": " + it.parameterTypes.map { p -> p.name }
        }
        throw Exception(s)
    }
}