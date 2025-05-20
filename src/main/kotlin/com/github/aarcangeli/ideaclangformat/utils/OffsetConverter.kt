package com.github.aarcangeli.ideaclangformat.utils

/**
 * Converts offsets from utf-8 (clang-format) to utf16 (java)
 */
class OffsetConverter(private val content: ByteArray) {
  private var cursorUtf8 = 0
  private var cursorUtf16 = 0

  fun toUtf16(offsetUtf8: Int): Int {
    assert(offsetUtf8 >= cursorUtf8)
    var cursorUtf8 = cursorUtf8
    var cursorUtf16 = cursorUtf16
    while (offsetUtf8 > cursorUtf8) {
      var sizeUtf8: Int
      var sizeUtf16: Int
      val ch = content[cursorUtf8]
      if (ch >= 0) {
        // 1 byte code point
        sizeUtf8 = 1
        sizeUtf16 = 1
      }
      else if (ch.toInt() and 224 == 192) {
        // 2 bytes code point
        sizeUtf8 = 2
        sizeUtf16 = 1
      }
      else if (ch.toInt() and 240 == 224) {
        // 3 bytes code point
        sizeUtf8 = 3
        sizeUtf16 = 1
      }
      else {
        // 4 bytes code point
        sizeUtf8 = 4
        sizeUtf16 = 2
      }
      cursorUtf16 += sizeUtf16
      cursorUtf8 += sizeUtf8
    }
    this.cursorUtf8 = cursorUtf8
    this.cursorUtf16 = cursorUtf16
    return cursorUtf16
  }
}
