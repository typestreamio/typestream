Output run.gif

Set Theme "Monokai Pro"
Set FontSize 22
Set Width 1300
Set Height 650

Type "typestream run 'cat /dev/kafka/local/topics/books | cut .name > /dev/kafka/local/topics/book_titles'"

Enter

Sleep 2s

Type '# Since I do not remember the schema of the books topic, I can use typestream to check it out 🤓'

Enter


Sleep 2s

Type "echo 'file /dev/kafka/local/topics/books' | typestream"

Enter

Type '# All right, ready to launch a job 🚀'

Enter


Type "typestream run 'cat /dev/kafka/local/topics/books | cut .title > /dev/kafka/local/topics/book_titles'"

Enter

Sleep 2s

Type '# The job is running! 🎉'

Sleep 4s
