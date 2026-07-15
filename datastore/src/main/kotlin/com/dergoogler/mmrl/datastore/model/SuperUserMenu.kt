@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.dergoogler.mmrl.datastore.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class SuperUserMenu
    constructor(
        @ProtoNumber(1) val option: Option = Option.Name,
        @ProtoNumber(2) val descending: Boolean = false,
        @ProtoNumber(3) val pinHasRoot: Boolean = true,
        @ProtoNumber(4) val showSystemApps: Boolean = false,
    )
