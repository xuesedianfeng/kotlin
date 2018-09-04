// CORRECT_ERROR_TYPES
// NO_VALIDATION

@file:Suppress("UNRESOLVED_REFERENCE", "DELEGATION_NOT_TO_INTERFACE")
package test

interface Intf

class TFooBarBaz: Foo(), Bar, Baz

// Error, two ()
class TFooBarBaz2: Foo(), Bar(), Baz, Intf

class TFooBarBaz3 : Foo, Bar, Baz

class TFooBar(val a: X) : Foo(), Bar by a, Intf

class TFooBar2(val a: X): Foo by a, Bar by a

class TxFooxBarxBaz : x.Foo(), x.Bar, x.Baz, Intf

// Error, two ()
class TxFooxBarxBaz2 : x.Foo(), x.Bar, x.Baz()

class Generics1 : Foo<String>()

class Generics2 : Foo<String>

class Generics3 : Foo<Bar, Baz, Boo<Baz, List<*>>, String>