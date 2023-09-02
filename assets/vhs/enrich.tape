Output enrich.gif

Set Theme "Monokai Pro"
Set FontSize 22
Set Width 1300
Set Height 650

Type typestream

Enter

Type ' cat /dev/kafka/local/topics/page_views | enrich { view -> http "https://api.country.is/#{$view.ip_address}" | cut .country }'

Enter

Sleep 5s

Ctrl+C

Ctrl+C

Type "# It's that easy 🎉"

Enter

Sleep 3s
