# Assistant setup

## Get a key

The assistant uses **Gemini through its OpenAI-compatible endpoint**:

```
https://generativelanguage.googleapis.com/v1beta/openai/chat/completions
model: gemini-flash-lite-latest
```

You need a **Google AI Studio** key, which is free and needs no card:
<https://aistudio.google.com/apikey>

A Google key starts with `AIza`. An xAI key starts with `xai-` and Google
rejects it with `"Please pass a valid API key"` — the two are not interchangeable.

## Add it

In `local.properties` (already gitignored, never committed):

```properties
GEMINI_API_KEY=AIza...your key here...
```

Optional overrides:

```properties
GEMINI_MODEL=gemini-flash-lite-latest
AI_PROVIDER=gemini
```

Then rebuild — the key reaches the app through `buildConfigField`, so a Gradle
sync is required after changing it.

## Switching to xAI Grok

Both vendors speak the same OpenAI-compatible `chat/completions` shape, so
swapping is config only:

```properties
AI_PROVIDER=xai
XAI_API_KEY=xai-...
```

Note that xAI has **no free tier**. An account without credits returns
`permission-denied` on every request.

## Security

The key is compiled into the APK and **is extractable from a release build** by
anyone who unzips it. That is acceptable for a personal or coursework build, but
a production app would keep the key on a server and have the app call that
server instead, so the key never ships to devices.

Rotate any key that has been pasted into a chat, a commit, or a screenshot.

## How the routing keeps cost down

| Tier | What it handles | API calls |
|------|-----------------|-----------|
| 1 | "steps left today", "my streak", "calories today", "my goal", "workouts today", "my weight" | **0** — answered from Room |
| 2 | Actions: set goal, set weight, log workout, add custom exercise | 1 |
| 3 | General fitness questions | 1, then cached for 30 days |

Tier 1 needs no key and no network, so the assistant stays useful offline and
after a quota is exhausted. Tiers 2 and 3 show an "assistant unavailable"
message rather than failing silently.

Further savings:
- Context is a ~50-token summary line, never a JSON dump of your data.
- Only the last 6 messages are sent as conversation history.
- Tier 3 answers are cached by a hash of the normalised question. Questions
  mentioning your own numbers ("my", "today") are never cached, since they go
  stale immediately.
- A client-side rate limiter queues requests instead of firing them into a 429.

## Actions

The model never touches the database. It emits an intent that `ActionExecutor`
validates first: step goal 1,000–50,000, duration 1–300 min, weight 30–250 kg,
and exercise names resolved against your real catalogue with fuzzy matching.

Writes show a confirm/cancel chip before anything changes, because a model can
misparse "eight thousand" as 8. Reads run immediately.

There are **no delete actions** by design.
