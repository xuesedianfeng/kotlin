// !LANGUAGE: +AllowContractsForCustomFunctions +ReadDeserializedContracts
// !USE_EXPERIMENTAL: kotlin.internal.ContractsDsl

package test

import kotlin.contracts.*

fun Any?.isNotNull(): Boolean {
    contract {
        returns(true) implies (this@isNotNull != null)
    }
    return this != null
}