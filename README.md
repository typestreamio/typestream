<div align="center">
    <img src="/assets/avatar-transparent.png?raw=true" width="86">
</div>

<h1 align="center">TypeStream</h1>

<br />

<div align="center">
    <a href="https://github.com/typestreamio/typestream/blob/main/LICENSE">
        <img src="https://img.shields.io/github/license/typestreamio/typestream" />
    </a>
    <a href="https://discord.gg/Ha9sJWXb">
        <img src="https://img.shields.io/badge/Chat-on%20Discord-blue" alt="Discord invite" />
    </a>
</div>

<p align="center">
    <a href="#why-typestream">Why TypeStream?</a>
    ·
    <a href="#getting-started">Getting started</a>
    ·
    <a href="#how-to-contribute">How to contribute</a>
    ·
    <a href="#code-of-conduct">Code of conduct</a>
    ·
    <a href="#license">License</a>
</p>

<h3 align="center">

TypeStream is an abstraction layer on top of Kafka that allows you to write
and run <i>typed</i> data pipelines with a minimal, familiar syntax.

</h3 >

## Why TypeStream?

Building streaming data pipelines on top of Kafka comes with some fixed costs.
You have to write an app, test it, then deploy and manage it in production. Even
for the simplest pipelines, this can be a lot of work.

With TypeStream you can write powerful, typed data pipelines the way you'd write
a simple UNIX pipeline in your terminal. For example, imagine you'd like to
filter a "books" topic. With TypeStream, it's a one liner:

```sh
$ typestream
> cat /dev/kafka/local/topics/books | grep "station" > /dev/kafka/local/topics/stations
```

TypeStream will take care of type-checking your pipeline and then run it for
you. Here's how grepping looks like in action:

![grepping with TypeStream](/assets/vhs/grep.gif?raw=true)

Another common use case that requires a lot of boilerplate is to enrich a topic
with data from an external source. For example, you might have a topic with an
ip address field and you may want to enrich it with country information. With
TypeStream, you can do it (again!) in a single line:

```sh
$ typestream
> cat /dev/kafka/local/topics/page_views | enrich { view -> http "https://api.country.is/#{$view.ip_address}" | cut .country } > /dev/kafka/local/topics/page_views_with_country
```

Here's how enriching looks like in action:

![enriching with TypeStream](/assets/vhs/enrich.gif?raw=true)

As you can see from the previous command, in the spirit of UNIX, we used cut to
extract the country field from the response. Here's the kick, you can use `cut`
(and many other Unix commands) on streams as well:

```sh
$ typestream
> cat /dev/kafka/local/topics/books | cut .title > /dev/kafka/local/topics/book_titles
```

Here's how cutting looks like in action:

![cutting with TypeStream](/assets/vhs/cut.gif?raw=true)

Another problem that TypeStream solves is preventing you from writing faulty
pipelines by type-checking them before executing them. Here's type checking in
action:

![type checking with TypeStream](/assets/vhs/type-checking.gif?raw=true)

If you'd like to learn more about TypeStream, check out [the official
documentation](https://docs.typestream.io/).

## Getting started

If you use [Homebrew](https://brew.sh/):

```sh
brew install typestreamio/tap/typestream
$ typestream --version
```

if you see something like this:

```sh
typestream version 2023.08.31+3 42f7762daac1872416bebab7a34d0b79a838d40a (2023-09-02 09:20:52)
```

then you're good to go! You can now run:

```sh
typestream local start
typestream local seed
```

to start a local TypeStream server and seed it with some sample data. Now you're
ready to start writing your own pipelines!

Check out our [documentation](https://docs.typestream.io/) to learn more about
TypeStream.

## How to contribute

We love every form of contribution! Good entry points to the project are:

- Our [contributing guidelines](/CONTRIBUTING.md) document.
- Issues with the tag
  [gardening](https://github.com/typestreamio/typestream/issues?q=is%3Aissue+is%3Aopen+label%3Agardening).
- Issues with the tag [good first
  patch](https://github.com/typestreamio/typestream/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+patch%22).

If you're not sure where to start, open a [new
issue](https://github.com/typestreamio/typestream/issues/new) or hop on to our
[discord](https://discord.gg/Ha9sJWXb) server and we'll gladly help you get
started.

## Code of Conduct

You are expected to follow our [code of conduct](/CODE_OF_CONDUCT.md) when
interacting with the project via issues, pull requests, or in any other form.
Many thanks to the awesome [contributor
covenant](http://contributor-covenant.org/) initiative!

## License

[Apache 2.0](/LICENSE)
