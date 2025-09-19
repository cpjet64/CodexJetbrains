package dev.curt.codexjb.proto

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}

