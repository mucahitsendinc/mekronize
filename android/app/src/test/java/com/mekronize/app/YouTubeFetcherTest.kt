package com.mekronize.app

import com.mekronize.app.sync.YouTubeFetcher
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Ağ gerektirir: MEK_NET=1 ./gradlew :app:testDebugUnitTest */
class YouTubeFetcherTest {
    private val id = "jNQXAC9IVRw" // "Me at the zoo" (19 sn)

    @Test
    fun listsStreams() {
        assumeTrue(System.getenv("MEK_NET") == "1")
        val info = YouTubeFetcher.info(id)
        println("TITLE=${info.name} DUR=${info.duration}")
        info.audioStreams.forEach { println("A itag=${it.itag} ${it.format} br=${it.averageBitrate} ${it.deliveryMethod} url=${it.isUrl}") }
        info.videoStreams.forEach { println("V itag=${it.itag} ${it.format} ${it.height}p ${it.codec} ${it.deliveryMethod}") }
        info.videoOnlyStreams.forEach { println("VO itag=${it.itag} ${it.format} ${it.height}p ${it.codec} ${it.deliveryMethod}") }
        println("PICK audio=" + YouTubeFetcher.pickAudio(info.audioStreams)?.itag)
        println("PICK video360=" + YouTubeFetcher.pickVideo(info, 360).let { "${it.first?.itag}/${it.second?.itag}" })
        println("PICK video720=" + YouTubeFetcher.pickVideo(info, 720).let { "${it.first?.itag}/${it.second?.itag}" })
    }

    @Test
    fun downloadsAudioAndVideo() = runBlocking {
        assumeTrue(System.getenv("MEK_NET") == "1")
        val dir = File(System.getProperty("java.io.tmpdir"), "mek-test").apply { deleteRecursively(); mkdirs() }
        val a = YouTubeFetcher.fetch(id, false, 0, 1, dir) {}
        println("AUDIO ${a.file.name} ${a.file.length()} dur=${a.durationMs}")
        check(a.file.length() > 50_000)
        val v = YouTubeFetcher.fetch(id, true, 0, 2, dir) {}
        println("VIDEO ${v.file.name} ${v.file.length()}")
        check(v.file.length() > 100_000)
    }
}
