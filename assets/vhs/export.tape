Output export.gif

Set Theme "Monokai Pro"
Set FontSize 22
Set Width 1300
Set Height 650

Type "# Let's say you are debugging a problem with analytics"

Enter

Type "# and that for your investigation you need an export of US page_views"

Enter

Type "# You can quickly export data from your sources by piping commands into TypeStream!"

Enter

Type "# Let's see it in action 🚀"

Enter

Type "echo 'cat /dev/kafka/local/topics/page_views | enrich {p -> http "

Type '"https://api.country.is/#{$p.ip_address}" | cut .country } | grep [.country == "US"]'

Type "'| typestream > page_views.txt"

Enter

Type "# Now you have a file with US page views on your machine 🎉"

Enter

Type "# Piping a data pipeline into TypeStream is a great way to quickly export data! No pun intended 😅"

Enter

Sleep 4s
