package com.peerlock.domain.policy

sealed class PolicyAction {
    data object Monitor : PolicyAction()
    data object Suspend : PolicyAction()
    data object Unsuspend : PolicyAction()
}
