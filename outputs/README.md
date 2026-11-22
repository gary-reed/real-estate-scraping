# outputs/

This folder is for the CSV, or completed run writes, or for any files generated or to save afterwards. The scraper's database, logs, browser profile, and caches stay inside `scrapers/<site>-<cc>-scraper`.

```
outputs/<country>/<site>/<yyyy-MM-dd>/<file-name>
```

`<file-name>` is `realestate.csv-sink.file-name` in the scraper's `application.yaml`. When the property is omitted the name is `listings.csv`. It is a file name, not a path: the country, the site, and the date are the folders above it.

Compass, with the default, writes `outputs/us/compass/2026-11-16/listings.csv`. The date is the machine's local date. Another run the same day replaces that file with the latest snapshot. The hash memory that lets the next run push only changes stays in the scraper module (`data/csv-sink`), not here.

How the sink is turned on is in [docs/dialect.md](../docs/dialect.md). The repository [README](../README.md) starts with the AI prompt: an AI agent generates the real-estate scraper that writes this file.

Git ignores everything in this folder except this README.
