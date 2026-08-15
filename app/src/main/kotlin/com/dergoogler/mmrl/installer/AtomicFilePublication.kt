package com.dergoogler.mmrl.installer

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Fail-closed complete-file publication. Never degrades an atomic publication promise to copy/move semantics. */
object AtomicFilePublication {
    fun move(source: File, target: File) {
        require(source.parentFile?.canonicalFile == target.parentFile?.canonicalFile) {
            "Atomic publication requires source and target in the same directory"
        }
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
