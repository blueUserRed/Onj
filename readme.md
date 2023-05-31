# Onj

<br>
<br>

### What is Onj?
Onj is a simple language with a json-like syntax used mainly for
writing config files. It is mainly intended for files written by a
human and read by a computer. It's file extension is ``onj``.

<br>

### Syntax

<br>

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

<br>

#### Data types

Onj supports the following datatypes:
 - boolean
 - int (64-bit signed)
 - float (64-bit)
 - string
 - objects
 - arrays
 - null-type

<br>

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

<br>

#### arrays

Arrays are declared using square brackets containing values
separated by commas.

````json5
arr: [
    "value", 1, 2, true
]
````

<br>

#### quoted keys

Keys containing special characters have to be wrapped in quotes.

````json5
"I contain Spaces!": true,
'123 I start with a number!': true
````

<br>

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
unterminated block comment
````

<br>

### variables

<br>

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

<br>

#### global variables

The following global variables are provided by default: ``NaN``,
``infinity``.

<br>

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

<br>

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

<br>

#### type conversions

By putting a hash and an identifier behind a value it can be
converted to different datatype.

````json5
var anInt = 2;

aFloat: anInt#float,
aString: anInt#string,
toFloatToString: anInt#float#string
````

<br>

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

<br>

### Imports
<!--
    TODO: explain imports in schema files separately because
    of named object
-->
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

<br>

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

<br>

### Schemas

Schemas can be used to validate the structure of a .onj file.
The file extension for schema files is typically ``.onjschema``.

<br>

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

<br>

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

<br>

### Named Objects

To better explain why named objects are needed, I'll start with
the problem they are trying to solve. Imagine you want to represent
an ui in an onj file. You need different widgets (labels, images)
and groups (HBoxes, VBoxes), however, depending on the widget they
will need different keys. While a label will need a 'text' key, an
image will need a 'image-path' key. One way you could implement this
is the following:

_screen.onj_
````json5
root: {
    type: "VBox",
    children: [
        {
            type: "label",
            text: "I'm a label!",
            font: "Comic Sans"
        },
        {
            type: "image",
            imagePath: "path/to/image.png"
        }
    ]
}
````

_screen.onjschema_
````json5
root: {
    type: string,
    // Because each widget requires different keys, we need to
    // allow all keys
    ...*
}
````

However, the above approach is not very good, because the schema
file is essentially worthless and the validation would have to be
almost completely done by the programmer.

Named objects try to solve this problem by giving names to objects
and requiring different keys depending on the name. Additionally,
multiple named objects can be grouped together in a named object
group, that can than be used as a type in your schema file.

Here is the better solution using named objects:

_screen.onj_
````json5
root: $VBox { // declares an object with the name VBox 
    children: [
        $Label {
            type: "label",
            text: "I'm a label!",
            font: "Comic Sans"     
        },
        $Image {
            imagePath: "path/to/image.png"
        }
    ]
}
````

_screen.onjschema_
````json5

$Widget { // declares a named object group named Widget
    
    // declares a object with name HBox in the Widget group
    $HBox {
        // the name of a object group can be used like a datatype
        // to allow any of the objects in it
        children: $Widget[]
    }
    
    $VBox {
        children: $Widget[]
    }

    $Label {
        type: string
        text: string
        font: string
    }

    $Image {
        imagePath: string
    }
}

root: $Widget

````
Note: when declaring multiple named object groups the object names
need to be unique even across different groups.

<br>

### Interacting with onj files from kotlin

<br>

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

<br>

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

<br>

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

// Be careful! Because onj uses 64-bit datatypes, an OnjInt will be
// a Long, and an OnjFloat will be a Double
val onjInt = onj.get<Long>("myInt")
````

<br>

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

<br>

#### building objects or arrays from kotlin

````kotlin
// Objects can be created using the buildOnjObject function.

val obj = buildOnjObject {
    // here, the with function can be used to declare a key
    "myKey" with OnjString("someValue")
    
    // simple values can be converted to OnjValues automatically
    "myString" with "aString"
    "myBool" with true
    
    // includes all keys of another object or of a Map<String, OnjValue>
    includeAll(anotherObject)
    
    "nestedObject" with buildOnjObject {
        "key" with 34
    }
    
    // Arrays or Collections will be automatically converted
    // to OnjArrays, including the contained values
    "arr" with arrayOf(
        true, "string", null
    )
}

// Arrays can be created using the .toOnjArray extension function
// on Collection<*> and Array<*>. Like buildOnjObject this function
// will attempt to convert values to OnjValues
val arr = arrayOf(true, false, "string").toOnjArray()

// a similar functions for objects exists on Map<String, *>
val obj = mapOf(
    "key" to true,
    "key2" to "string"
).toOnjObject()
````

The OnjValue class also provides functions to convert the structure
back to a string or even a json string. However, when reading a 
file and writing it again, things like variables, imports and
calculations are lost.

````kotlin
val onj = OnjParser.parseFile("file.onj")

val asString = onj.toString()
val asJson = onj.toJsonString()
````

### namespaces and Customization

Namespaces allow adding custom functions, operator overloads, 
conversions, global variables and datatypes.

<br>

#### declaring a namespace

Namespaces are typically objects and annotated with the 
``@OnjNameSpace`` annotation.

<br>

#### declaring functions

Functions are registered using the ``@RegisterOnjFunction``
annotation. This annotation takes a parameter containing a
string containing an onjschema with a key named 'params'
and a value of type array. This array tells the OnjParser
which types this functions takes and should match the actual
signature of the function. The function can only take types
that extend OnjValue and must return a type that extends
OnjValue as well.

````kotlin
@OnjNameSpace
object MyNamespace {
    
    @RegisterOnjFunction(schema = "params: [string]")
    fun greeting(name: OnjString): OnjString {
        return OnjString("hello, ${name.value}!")
    }
    
}
````

The ``@RegisterOnjFunction`` annotation also takes a second,
optional parameter that indicates the type of function. The default
type is ``normal``, but it can also be set to ``infix``,
``operator`` or ``conversion``.

``infix`` signals that a function can be used as an infix-function
(callable using the ``param1 function param2`` syntax). The function
must take exactly two parameters.

``operator`` indicates that the function overloads an operator.
It's name must be one of: plus, minus, star, div. It must take
exactly two parameters.

``conversion`` indicates that the function can be called using the
conversion syntax (``value#function``). It must take exactly one
parameter.

<br>

#### adding global variables

To add custom global variables to the namespace create a field of
type Map<String, OnjValue> and annotate it with the
``@OnjNamespaceVariables`` annotation. This map contains the
variable names as keys and the value of the variable as value.

````kotlin
@OnjNameSpace
object MyNamespace {

    @OnjNamespaceVariables
    val variables: Map<String, OnjValue> = mapOf(
        "variable" to OnjString("value")
    )
    
}
````

<br>

#### adding custom datatypes

To add a custom datatype, first the class representing it must be
created. It has to extend OnjValue.

````kotlin
class OnjColor(
    // the abstract field 'value' must be overridden
    override val value: Color // Color is an imaginary class
) : OnjValue() {
    
    // OnjValue requires you to override two toString functions
    // the toString functions should always return valid onj, that 
    // when parsed results in the same value.
    // If the resulting string contains newlines, it should take the 
    // indentationLevel parameter into account.
    
    override fun toString(): String = "color('${value.toHexString()}')"
    override fun toString(indentiationLevel: Int): String = toString()
    
    // OnjValue also requires you to override two toJsonString functions
    // These functions should always return valid json that in some
    // way represents the type
    // If the resulting string contains newlines, it should take the 
    // indentationLevel parameter into account.
    
    override fun toJsonString(): String = "'${value.toHexString()}'"
    override fun toJsonString(indentationLevel: Int): String = toJsonString()
}

````

To make the type available in onjschemas you need to go into your
namespace and create a field of type Map<String, KClass<*>> and
annotate it with the ``@OnjNamespaceDatatypes``. The Map contains the 
name of the datatype as the key and the KClass as the value.

````kotlin
@OnjNameSpace
object MyNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "Color" to OnjColor::class
    )
    
}
````

Lastly, you need a way to actually create a value of the added type.
This is commonly done using a function.

````kotlin
@OnjNameSpace
object MyNamespace {

    @RegisterOnjFunction(schema = "params: [string]")
    fun color(hex: OnjString): OnjColor {
        return OnjColor(Color.fromHex(hex.value))
    }
    
}
````

<br>

#### registering the namespace 

````kotlin
fun init() {
    OnjConfig.registerNameSpace(MyNamespace, "MyNamespace")
}
````

<br>

#### using the namespace

To include the namespace in an onj or onjschema file, the ``use``
keyword is used.

_file.onj_
````json5
use MyNamespace;

myColor: color("00ff00"),
myGlobal: variable,
myFunction: greeting("reader")
````
_file.onjschema_
````json5
use MyNamespace;

myColor: Color,
myGlobal: string,
myFunction: string
````
