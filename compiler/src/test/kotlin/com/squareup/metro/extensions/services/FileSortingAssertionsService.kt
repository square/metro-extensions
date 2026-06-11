package com.squareup.metro.extensions.services

import java.io.File
import kotlin.time.Duration
import org.jetbrains.kotlin.test.services.AssertionsService
import org.jetbrains.kotlin.test.services.JUnit5Assertions

/**
 * Custom [AssertionsService] that sorts `// FILE:` blocks alphabetically before comparing expected
 * and actual content. This ensures IR dump golden files are stable across platforms, where the
 * iteration order of synthetic IR files (e.g., contribution hints) can differ.
 */
object FileSortingAssertionsService : AssertionsService() {
  private val delegate: AssertionsService = JUnit5Assertions

  /**
   * Splits text into blocks delimited by `// FILE:` headers and sorts them alphabetically by their
   * header line. Returns the reassembled text. Content before the first `// FILE:` header (if any)
   * is preserved at the beginning.
   */
  private fun sortFileBlocks(text: String): String {
    val lines = text.lines()
    val blocks = mutableListOf<Pair<String, List<String>>>()
    var currentHeader = ""
    var currentLines = mutableListOf<String>()

    for (line in lines) {
      if (line.startsWith("// FILE:")) {
        if (currentHeader.isNotEmpty() || currentLines.isNotEmpty()) {
          blocks.add(currentHeader to currentLines)
        }
        currentHeader = line
        currentLines = mutableListOf()
      } else {
        currentLines.add(line)
      }
    }
    if (currentHeader.isNotEmpty() || currentLines.isNotEmpty()) {
      blocks.add(currentHeader to currentLines)
    }

    // Separate the preamble (content before the first // FILE: header) from file blocks.
    val preamble =
      if (blocks.isNotEmpty() && blocks[0].first.isEmpty()) blocks.removeAt(0) else null
    blocks.sortBy { it.first }

    // Trim trailing blank lines from each block to normalize whitespace between blocks.
    fun List<String>.trimTrailingBlanks() = dropLastWhile { it.isBlank() }

    return buildString {
        if (preamble != null) {
          for (line in preamble.second.trimTrailingBlanks()) appendLine(line)
        }
        for ((index, entry) in blocks.withIndex()) {
          val (header, bodyLines) = entry
          if (index > 0 || preamble != null) appendLine()
          appendLine(header)
          for (line in bodyLines.trimTrailingBlanks()) appendLine(line)
        }
      }
      .trimEnd('\n') + "\n"
  }

  override fun doesEqualToFile(
    expectedFile: File,
    actual: String,
    sanitizer: (String) -> String,
  ): Boolean {
    return delegate.doesEqualToFile(expectedFile, sortFileBlocks(actual)) {
      sortFileBlocks(sanitizer(it))
    }
  }

  override fun assertEqualsToFile(
    expectedFile: File,
    actual: String,
    sanitizer: (String) -> String,
    message: () -> String,
  ) {
    delegate.assertEqualsToFile(
      expectedFile,
      sortFileBlocks(actual),
      { sortFileBlocks(sanitizer(it)) },
      message,
    )
  }

  override fun assertEquals(expected: Any?, actual: Any?, message: (() -> String)?) {
    delegate.assertEquals(expected, actual, message ?: { "" })
  }

  override fun assertNotEquals(expected: Any?, actual: Any?, message: (() -> String)?) {
    delegate.assertNotEquals(expected, actual, message ?: { "" })
  }

  override fun assertTrue(value: Boolean, message: (() -> String)?) {
    delegate.assertTrue(value, message ?: { "" })
  }

  override fun assertFalse(value: Boolean, message: (() -> String)?) {
    delegate.assertFalse(value, message ?: { "" })
  }

  override fun failAll(exceptions: List<Throwable>) = delegate.failAll(exceptions)

  override fun assertAll(conditions: List<() -> Unit>) = delegate.assertAll(conditions)

  override fun assertTimeoutPreemptively(
    timeout: Duration,
    message: () -> String,
    action: () -> Unit,
  ) {
    delegate.assertTimeoutPreemptively(timeout, message, action)
  }

  override fun assertNotNull(value: Any?, message: (() -> String)?) {
    delegate.assertNotNull(value, message ?: { "" })
  }

  override fun <T> assertSameElements(
    expected: Collection<T>,
    actual: Collection<T>,
    message: (() -> String)?,
  ) {
    delegate.assertSameElements(expected, actual, message ?: { "" })
  }

  override fun assertTimeoutPreemptively(
    timeout: Duration,
    message: () -> String,
    action: () -> Unit,
  ) {
    delegate.assertTimeoutPreemptively(timeout, message, action)
  }

  override fun fail(message: () -> String): Nothing = delegate.fail(message)
}
