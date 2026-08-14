package com.dergoogler.mmrl.platform.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShellCommandTest {
    @Test
    fun quotesEveryArgumentAsData() {
        val command = ShellCommand.of("ksud", "module", "install", "/tmp/module $(id) 'one'.zip")
        assertEquals("'ksud' 'module' 'install' '/tmp/module $(id) '\"'\"'one'\"'\"'.zip'", command)
        assertFalse(command.startsWith("ksud "))
    }

    @Test
    fun singleQuoteCannotTerminateQuotedArgument() {
        assertEquals("'a'\"'\"'b'", ShellCommand.quote("a'b"))
    }
}
