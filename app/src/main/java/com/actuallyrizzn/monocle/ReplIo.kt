package com.actuallyrizzn.monocle

object ReplIo {
    fun atPrompt(buf: String): Boolean {
        val t = buf.replace("\r", "").trimEnd()
        return t.endsWith(">>>")
    }

    fun rawReady(buf: String): Boolean {
        val t = buf.replace("\r", "")
        return t.contains("raw REPL") || t.trimEnd().endsWith(">")
    }
}
