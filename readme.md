
### What is Onj?
Onj is a simple language with a json-like syntax used mainly for
writing config files. It is mainly intended for files written by a
human and read by a computer.

### syntax overview
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

Arrays are declared using square brackets containing values
separated by commas.

````json5
arr: [
    "value", 1, 2, true
]
````

<br>

Keys containing special characters have to be wrapped in quotes.

````json5
"I contain Spaces!": true,
'123 I start with a number!': true
````

### variables

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

By putting a hash and an identifier behind a value it can be
converted to different datatype.

````json5
var anInt = 2;

aFloat: anInt#float,
aString: anInt#string,
toFloatToStrin: anInt#float#string
````

TODO: continue
