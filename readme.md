
### What is Onj?
Onj is a simple language with a json-like syntax used mainly for
writing config files. It is mainly intended for files written by a
human and read by a computer. It's file extension is ``onj``.

### syntax

#### top-level
The top level of an .onj file is always an object consisting of 
key-value pairs.

````json5
key: "value",
key2: 'value',
boolean: true,
int: 34,
float: 1.23,
nullValue: null,
````
Key-value pairs are separated by commas, trailing commas are allowed.

#### data types

Onj supports the following datatypes:
 - boolean
 - int (64-bit signed)
 - float (64-bit)
 - string
 - objects
 - arrays
 - null-type

#### nested objects

Objects are declared using curly braces
containing more key-value pairs.

````json5
object: {
    key: "value",
    otherKey: "value",
    nestedObject: {
        key: "value"
    }
}
````

#### arrays

Arrays are declared using square brackets containing values
separated by commas.

````json5
arr: [
    "value", 1, 2, true
]
````

#### quoted keys

Keys containing special characters have to be wrapped in quotes.

````json5
"I contain Spaces!": true,
'123 I start with a number!': true
````

#### comments

Onj supports line or block comments. Line comments are declared 
using ``//`` and block comments are started with ``/*`` and closed
with ``*/``. If a block comment is not closed, it will go on to the
end of the file.

````json5

/*
a block comment
*/

// a line comment

/*
unterminaded block comment
````

### variables

#### declaring variables

Variables can be used to extract common values to a single place and
reuse them. They are declared at the top-level using
the ``var`` keyword. 

````json5
var obj = {
    key: "value"
};

var number = 5;

first: obj,
second: obj,
favoriteNumber: number,
arr: [
    true,
    number,
    obj
]
````

#### the triple-dot syntax

When objects or arrays only share some values and not others,
they can still be extracted to a variable and then included using
the triple-dot syntax.

````json5
var catKeys = {
    type: "cat",
    amountLegs: 4
};

pets: [
    {
        name: "Lilly",
        ...catKeys
    },
    {
        name: "Bello",
        type: "dog",
        amountLegs: 4,
    },
    {
        ...catKeys,
        name: "Snowflake"
    }
],

var commonFruits = [ "apple", "orange" ];

fruitSaladOne: [
    "pear",
    ...commonFruits,
    "blueberry"
],
fruitSaladTwo: [
    "pineapple",
    "strawberry",
    ...commonFruits
]
````

### calculations

Onj supports simple mathematical expressions.

````json5
var two = 1 + 1;

five: 1 + two * (2 / 1),
negative: -two
````

Onj does integer division when both operands are integers. For all
operations the following rule applies: If one operand is a float,
the result is a float.

#### type conversions

By putting a hash and an identifier behind a value it can be
converted to different datatype.

````json5
var anInt = 2;

aFloat: anInt#float,
aString: anInt#string,
toFloatToString: anInt#float#string
````

### the variable access syntax

If a variable is of type object or array, the variable access
syntax can be used to access values from it.

````json5
var colors = {
    red: "#ff0000",
    green: "#00ff00",
    blue: "#0000ff"
};

// properties of objects can be accessed by using a dot
// followed by an identifier
favoriteColor: color.blue,
// identifiers can be wrapped in quotes
anotherColor: color."green",

var animals = [ "cat", "dog", "bird", "fish" ];

// values of arrays can be accessed by using an integer
// after the dot
favoriteAnimal: animals.0, // arrays are zero-indexed

var outer = {
    nested: {
        arr: [ "a value" ]
    }
};

accessingNested: outer.nested.arr.0
````

By putting parenthesis after the dot, the access can be
made dynamic. The value inside the parenthesis must resolve to a
string when accessing an object, or to an int when accessing an
array.

````json5

var indexOffset = 2;
var arr = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15];

dynamic: arr.(indexOffset + 4),

var strings = [ "a", "b", "c", "d" ];
var object = {
    a: 0,
    b: 1,
    c: 2,
    d: 3
};

dynamic2: object.(strings.(object.a))

````

### Imports

Imports can be used to split up large files or to extract structures
used across multiple files.

````json5
import "path/from/working/dir/file.onj" as imported;

someValue: imported
````

The import statement will create a variable with the name
specified after the ``as``. This variable contains the structure
of the imported file.

The path to the file can be dynamic.

````json5
var paths = [
    "fist/path/file.onj",
    "second/path/file.onj"
];

import (paths.0) as imported;
````

### Functions

Onj has the sqrt, pow and in functions built-in.

````json5
four: sqrt(16.0),

// pow and in are infix functions, meaning you can call them
// using the following syntax: param1 function param2

nine: 3 pow 2,
// But they can be called using the conventional syntax too
alsoNine: pow(3, 2),

// the in-function checks if a value is present in an array
isTrue: 1 in [ 1, 2, 3, 4 ]
````

### Schemas

Schemas can be used to validate the structure of a .onj file.
The file extension for schema files is typically ``.onjschema``.

#### Syntax

The syntax of onjschema files is very similar to onj files, but
instead of values, they specify data types. Some syntactical
structures (like variable accesses) are not supported by 
onjschemas.

````json5
anInt: int,
aString: boolean,
anArrayLiteral: [ boolean, string, int ],
anObject: {
    aFloat: float,
    aString: string
}
````

Objects must contain exactly the same keys with matching datatypes
and cannot contain keys not specified in the schema. The same 
applies for array literals.

#### special types and syntax

````json5
// To indicate that a value is nullable, use a question mark
// behind the type
nullableBool: boolean?,

// To indicate that a key is optional, use a question mark behind
// the key
optionalKey?: int,

// To make a type into an array, use square brackets
arrOfArbitraryLength: int[],
// To specify the length of an array, write a number in the
// square brackets
arrOfLengthFive: int[5],

// To indicate that the value can be any type, use a star
any: *,

// To indicate that an object can contain keys that where not
// specified use the triple-dot syntax followed by a star
...*,
// Keys that where not specified can have any type

// types are always read from left to right
key: int?[]?, // a nullable array of nullable ints

// the question mark and the square brackets can be used on
// objects or arrays as well
nullableArr: [ string, int ]?,
objectArr: { x: float, y: float }[]
````

### Interacting with onj files from kotlin

#### parsing onj files

.onj files can be parsed using the companion object of OnjParser.

`````kotlin
// using a string
val structure = OnjParser.parseFile("path/file.onj")

// using a file
OnjParser.parseFile(Paths.get(path).toFile())

// parsing a string directly
OnjParser.parse("key: 'value'")
`````

If an error occurs during parsing, the parser will throw an 
OnjParserException.
(This also means only the first syntax error
will be reported, I'm aware that this is not great)

````kotlin
val structure = try {
    OnjParser.parseFile("path/file.onj")
} catch (e: OnjParserException) {
    null
}
````

#### Matching with a schema

````kotlin
val schema = OnjSchemaParser.parseFile("file.onjschema")
val onj = OnjParser.parseFile("file.onj")

// throws a OnjSchemaException when the schema doesn't
// match the file
schema.assertMatches(onj) 

// returns null when the schema matches, or a string containing
// an error message otherwise
val result = schema.check(onj)

// casting is now safe
onj as OnjObject
````

#### reading values from a parsed object

````kotlin
val schema = OnjSchemaParser.parseFile("file.onjschema")
val onj = OnjParser.parseFile("file.onj")
schema.assertMatches(onj) 
onj as OnjObject

// a map containing the keys can be accessed using .value
val keys: Map<String, OnjValue> = onj.value

// .value is of type String here
val string = (keys["myString"] as OnjString).value

// Instead the generic .get function can be used instead.
// An Exception will be thrown if the key doesn't exist
// or the key has a wrong type, but in this case the schema
// check would have crashed first
val string = onj.get<OnjString>("myString").value

// To save the .value access, the type of .value can be used in
// the .get function instead, which will then access .value itself
val string = onj.get<String>("myString")

// Be careful! Because onj uses 64-bit datatypes, a OnjInt will be
// a Long, and a OnjFloat will be a Double
val onjInt = onj.get<Long>("myInt")
````

#### reading values from a parsed array

````kotlin
val schema = OnjSchemaParser.parseFile("file.onjschema")
val onj = OnjParser.parseFile("file.onj")
schema.assertMatches(onj) 
onj as OnjObject
val arr = onj.get<OnjArray>("arr")

// loop over all values
arr
    .value
    .forEach { onjValue ->
        println(onjValue.value)
    }

// access an index
// throws an IndexOutOfBoundsException if the index doesn't exist
val first = arr[0] as OnjString
````

### namespaces and Customization
TODO
