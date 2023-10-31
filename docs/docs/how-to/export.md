---
description: Lear how to export data with the official CLI
---

# Export data

If you pipe commands into the official CLI, TypeStream will run it for you and print its output to standard output. For example, if you want to quickly check the topic names in your local Kafka cluster you can run the following command:

```sh
$ echo 'ls /dev/kafka/local/topics' | typestream
_schemas
authors
books
page_views
ratings
users
```

See it in action here:

![export](../../../assets/vhs/export.gif)
