package cn.edu.sicnu.cs.stu.guolijie.musicplayer

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.edu.sicnu.cs.stu.guolijie.musicplayer.service.MusicService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机上的简单测试：包名正确、MusicService 能正常创建并绑定。
 */
@RunWith(AndroidJUnit4::class)
class MusicServiceTest {

    @Test
    fun useAppContext_hasCorrectPackageName() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("cn.edu.sicnu.cs.stu.guolijie.musicplayer", appContext.packageName)
    }

    @Test
    fun musicService_canBeCreated() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(appContext, MusicService::class.java)
        assertNotNull(appContext.startService(intent))
    }
}
