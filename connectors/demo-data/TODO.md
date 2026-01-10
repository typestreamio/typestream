# Demo Data Connectors TODO

## Coinbase Crypto Tickers (Priority: High) - DONE

- [x] Implement WebSocket client using OkHttp
- [x] Connect to `wss://ws-feed.exchange.coinbase.com`
- [x] Subscribe to ticker channel for product pairs
- [x] Parse ticker messages (price, volume_24h, timestamp)
- [x] Create topic with 15-minute retention
- [x] Produce to `crypto_tickers` topic
- [x] Add reconnect logic on disconnect
- [x] CLI args: `--products ETH-USD,BTC-USD`

**Volume:** 100-1000+ events/sec | **Retention:** 15 minutes
**Auth:** None required for public data
**Docs:** https://docs.cdp.coinbase.com/exchange/websocket-feed/channels

---

## Wikipedia Recent Changes (Priority: High) - DONE

- [x] Implement SSE client using OkHttp SSE
- [x] Connect to `https://stream.wikimedia.org/v2/stream/recentchange`
- [x] Parse edit events (wiki, title, user, type, timestamp)
- [x] Create topic with 1-hour retention
- [x] Produce to `wikipedia_changes` topic
- [x] CLI args: `--wikis enwiki,dewiki` (filter by wiki)

**Volume:** 100-500 events/sec | **Retention:** 1 hour
**Auth:** None required
**Demo ideas:** Aggregations by language, bot vs human analysis, vandalism detection

---

## USGS Earthquakes (Priority: Medium)

- [ ] Implement HTTP polling
- [ ] Poll `https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_hour.geojson`
- [ ] Parse GeoJSON features (magnitude, location, depth, time)
- [ ] Create topic with 24-hour retention
- [ ] Produce to `earthquakes` topic
- [ ] Deduplicate by event ID
- [ ] CLI args: `--interval 60` (poll interval in seconds)

**Volume:** 5-50 events/hour | **Retention:** 24 hours
**Auth:** None required
**Demo ideas:** Geo enrichment, magnitude alerts, materialized views

---

## Hacker News (Priority: Medium)

- [ ] Implement Firebase polling or HTTP polling
- [ ] Poll `https://hacker-news.firebaseio.com/v0/newstories.json`
- [ ] Fetch item details for each story
- [ ] Create topic with 24-hour retention
- [ ] Produce to `hackernews_stories` topic
- [ ] Track last seen ID to avoid duplicates

**Volume:** 50-200 items/hour | **Retention:** 24 hours
**Auth:** None required
**Demo ideas:** Sentiment analysis, topic classification, trending detection

---

## GitHub Events (Priority: Medium)

- [ ] Implement HTTP polling
- [ ] Poll `https://api.github.com/events`
- [ ] Parse event types (PushEvent, PullRequestEvent, etc.)
- [ ] Create topic with 1-hour retention
- [ ] Produce to `github_events` topic
- [ ] Handle rate limiting (use token for higher limits)
- [ ] CLI args: `--token` (optional GitHub token)

**Volume:** Thousands/minute | **Retention:** 1 hour
**Auth:** Optional (token for higher rate limits)
**Demo ideas:** Developer activity, repo analytics, PR classification

---

## OpenSky Flight Tracking (Priority: Low)

- [ ] Implement HTTP polling
- [ ] Poll `https://opensky-network.org/api/states/all`
- [ ] Parse aircraft states (callsign, origin, position, velocity)
- [ ] Create topic with 15-minute retention (high volume snapshots)
- [ ] Produce to `flight_positions` topic
- [ ] Respect rate limits (400 req/day free tier)

**Volume:** ~10k positions per poll | **Retention:** 15 minutes
**Auth:** Optional (higher limits with account)
**Demo ideas:** Geo-filtering, airline stats, high-volume aggregations

---

## Reddit (Priority: Low)

- [ ] Implement Reddit API client (or PRAW-style)
- [ ] Poll subreddit new posts/comments
- [ ] Create topic with 6-hour retention
- [ ] Produce to `reddit_posts` topic
- [ ] Handle OAuth flow for credentials

**Volume:** Customizable by subreddit | **Retention:** 6 hours
**Auth:** Required (Reddit app credentials)
**Demo ideas:** Sentiment analysis, PII detection, topic classification

---

## Implementation Order

1. **Coinbase** - WebSocket pattern, high volume, great first demo
2. **Wikipedia** - SSE pattern, no auth, diverse use cases
3. **USGS** - Simple polling pattern, low volume baseline
4. **Hacker News** - Polling + item fetch pattern
5. **GitHub** - Rate limit handling pattern
6. **OpenSky/Reddit** - Lower priority, more complex auth
