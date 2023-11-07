Output grep.gif

Set Theme "Monokai Pro"
Set FontSize 22
Set Width 1300
Set Height 650

Type typestream

Enter

Type "# Let's start with a simple example"

Enter

Type "grep /dev/kafka/local/topics/books the"

Enter

Sleep 3s

Ctrl+C

Type "# By default, grep matches the pattern against the whole 'content' of the record"

Enter

Type "# Sometimes, we want to match against a specific field"

Enter

Type "# Let's check that out 🤓"

Enter

Type "grep /dev/kafka/local/topics/books [.word_count > 200]"

Enter

Sleep 3s

Ctrl+C

Type "# It's that easy 🎉"

Enter

Sleep 3s
