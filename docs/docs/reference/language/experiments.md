# Experiments

This document is where we sketch language ideas. They may never see the light of
day.

Refer to the official language [specs](specs.md) if you're looking for a formal
description of `TypeStream` syntax.

## Join operations

```sh
# by default, window joins
join /dev/kafka/local/clicks /dev/kafka/local/users

# works a little like grep
cat /dev/kafka/local/clicks | join /dev/kafka/local/users

# windows options (in seconds)
join --hopping-size 360 --by 60 /dev/kafka/local/clicks /dev/kafka/local/users

# lookup syntax
cat /dev/kafka/local/clicks | join --lookup /dev/kafka/local/users

# join by some key

cat /dev/kafka/local/clicks | join --lookup --by .internal_id /dev/kafka/local/users
```

## Variables

Showcase variable declarations:

```sh
# Number
let times=3

echo $times

# List of numbers
let luckyNumbers=[3,7,42]

echo $luckNumbers

# String

let foo="bar"
echo $foo

# List of strings
list=["one", "two", "three"]

echo $list

# Struct

let message = struct{
    foo: "bar",
    num: 42,
}
```

Long running split:

```sh
let done_orders = $(cat /dev/kafka/local/topics/orders | grep done)

grep $done_orders 42 > user_42

cat $done_orders | grep failed > errors
```

FileSystem pre-compute examples:

```sh
let topics = $(find /dev/kafka/local/topics/ops.logs.*)

cat $topics | grep 42 > answers

cd /dev/kafka/local/topics

grep orders "DONE" > completed_orders
```

Branching:

```sh
let grepA = $(grep a foo)
let grepB = $(grep b bar)

$grepA | $grepB > b

$grepA | wc > a

grep c baz > c
```

## Per record syntax

```sh
# order count by day
cat /dev/kafka/local/topics/orders | wc --by .date > /dev/ext/orders | sort

# questions is a stream where each record has id and text fields
cat /dev/kafka/local/topics/questions | grep [.text == "what is the meaning of life?" && .id > 42]

## impressions is a stream where each record has a page_id and a timestamp
cat /dev/kafka/local/topics/impressions | grep [.timestamp > $yesterday]  | wc --by .page_id | head -5

## alternative syntax with aliases
select /dev/kafka/local/topics/impressions | where [.timestamp > $yesterday] | by .page_id | top 5
```

## Enrich records

```js
cat /dev/kafka/local/topics/views | enrich { view ->
    http get "http://example.com/#{$view.ip}" | cut .country_code
} > /dev/kafka/local/topics/enriched_views
```
