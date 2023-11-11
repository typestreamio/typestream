Output enrich.gif

Source ./config.tape

Type "typestream"

Enter

Type 'cat /dev/kafka/local/topics/page_views | enrich { v -> http "https://api.country.is/#{$v.ip_address}" | cut .country }'

Enter

Sleep 5s

Ctrl+C

Ctrl+C

Type "# It's that easy 🎉"

Enter

Sleep 3s
