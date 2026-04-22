package com.example.timetable.data

import java.nio.file.Files
import kotlin.io.path.writeBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCacheManagerTest {

    @Test
    fun clearDirectoryContentsDeletesNestedCacheFilesButKeepsRootDirectory() {
        val root = Files.createTempDirectory("cache-test").toFile()
        val topFile = root.resolve("a.tmp").apply { writeText("abcd") }
        val nestedDir = root.resolve("nested").apply { mkdirs() }
        val nestedFile = nestedDir.toPath().resolve("b.tmp").also { it.writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }.toFile()
        val expectedBytes = topFile.length() + nestedFile.length()

        val result = AppCacheManager.clearDirectoryContents(root)

        assertEquals(expectedBytes, result.bytesCleared)
        assertTrue(result.deletedEntryCount >= 3)
        assertTrue(root.exists())
        assertTrue(root.listFiles().isNullOrEmpty())

        root.deleteRecursively()
    }
}
