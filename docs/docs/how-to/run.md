---
description: Learn how to run data pipelines in production
---

# Run data pipelines

If you launch the TypeStream CLI without any arguments, you'll be starting an
interactive session with the TypeStream Server. It's designed to feel like a
terminal session and allow you to quickly iterate on your data pipelines.

Once you're ready to run a pipeline in production, you want to use the `run` command:

```bash
$ typestream run 'cat /dev/kafka/local/topics/books | cut .title > /dev/kafka/local/topics/book_titles'
🚀 job created: typestream-app-e800fb40-de6b-42b9-a055-06565ec2d3be
```

This is how the `run` command looks like in action:

![run](../../../assets/vhs/run.gif)

This will create a job that will run in the background. You can check the status
using the `ps` command and stop it using the `kill` command. For a full list of
supported commands, see the [shell](/reference/language/shell.md) reference.
