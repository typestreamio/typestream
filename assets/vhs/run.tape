Output run.gif

Source ./config.tape

Type "# Let's say you need to run a job that extracts the titles of books from a Kafka topic"

Enter

Type "# and you need to store the result into another Kafka topic"

Enter

Type "# You can use TypeStream to run this job!"

Enter

Type "# Let's first check out the books schema so we don't make mistakes 🤓"

Enter

Type "echo 'file /dev/kafka/local/topics/books' | typestream"

Enter

Sleep 2s

Type "# All right, now you're ready to launch a job 🚀"

Enter

Type "typestream run 'cat /dev/kafka/local/topics/books | cut .title > /dev/kafka/local/topics/book_titles'"

Enter

Sleep 2s

Type "# The job is running!"

Enter

Type "It's that easy to run a job with TypeStream 🤯"

Enter

Sleep 4s
