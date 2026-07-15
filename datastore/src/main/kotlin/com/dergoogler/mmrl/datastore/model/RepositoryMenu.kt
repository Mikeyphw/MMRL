@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.dergoogler.mmrl.datastore.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class RepositoryMenu
    constructor(
        @ProtoNumber(1) val option: Option = Option.UpdatedTime,
        @ProtoNumber(2) val descending: Boolean = true,
        @ProtoNumber(3) val pinInstalled: Boolean = false,
        @ProtoNumber(4) val pinUpdatable: Boolean = false,
        @ProtoNumber(5) val showIcon: Boolean = true,
        @ProtoNumber(6) val showLicense: Boolean = true,
        @ProtoNumber(7) val showUpdatedTime: Boolean = true,
        @ProtoNumber(8) val showCover: Boolean = true,
        @ProtoNumber(9) val showVerified: Boolean = true,
        @ProtoNumber(10) val showAntiFeatures: Boolean = true,
        @ProtoNumber(11) val repoListMode: RepoListMode = RepoListMode.Detailed,
        @ProtoNumber(12) val showCategory: Boolean = true,
        @ProtoNumber(13) val showStars: Boolean = true,
    )
