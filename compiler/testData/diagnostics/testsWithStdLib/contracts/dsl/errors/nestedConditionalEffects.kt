// !LANGUAGE: +AllowContractsForCustomFunctions +UseReturnsEffect
// !USE_EXPERIMENTAL: kotlin.internal.ContractsDsl
// !DIAGNOSTICS: -INVISIBLE_REFERENCE -INVISIBLE_MEMBER

import kotlin.contracts.*

fun foo(boolean: Boolean) {
    contract {
        (returns() implies (boolean)) <!UNRESOLVED_REFERENCE!>implies<!> (!boolean)
    }
}