Output grep.gif

Source ./config.tape

Type typestream

Enter

Sleep 400ms

Type "# Let's start with a simple example"

Enter

Type "# By default, grep matches the pattern against the whole 'content' of the record"

Enter

Type "grep /dev/kafka/local/topics/books the"

Enter

Sleep 3s

Ctrl+C

Type "# grep can also match against a specific field"

Enter

Type "# Let's check that out 🤓"

Enter

Type "grep /dev/kafka/local/topics/books [.word_count > 200]"

Enter

Sleep 3s

Ctrl+C
Ctrl+C

Type "# It's that easy 🎉"

Enter

Sleep 3s
