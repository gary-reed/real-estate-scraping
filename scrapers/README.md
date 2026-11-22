# scrapers/

An AI agent generates a real-estate scraper here for a property portal you choose. The public repository tracks this README only. AI-generated modules stay in your clone (`scrapers/*` is git-ignored) until you decide to publish one.

## Add one with the AI prompt

Use the AI prompt. That is how a property scraper is added.

1. Clone [multi-scraper-framework](https://github.com/andre-mei/multi-scraper-framework) beside this repository and install it (`mvn install -DskipTests`, Java 17).
2. Open the AI prompt [docs/ai-scraper-recon-prompt.md](../docs/ai-scraper-recon-prompt.md), replace `{{HOMEPAGE_URL}}`, and give everything below the line to an AI agent that can read both trees.
3. The AI agent creates `scrapers/<site>-<cc>-scraper` and adds that folder as a `<module>` in the root `pom.xml`.

`<site>` is the brand, `<cc>` is the market country (`compass-us`, not a company name). One country site is one module. The steps, including how to run the jar, are at the top of the repository [README](../README.md).

Copying `templates/template-real-estate-scraper` by hand and following its README is the same result without the AI agent. The AI prompt is the specification that copy is written for.

A full working copy may already contain `compass-us-scraper`, the Compass example that the AI prompt produced. The root pom lists it. On a clone where the folder was not committed, remove that `<module>` line before `mvn test`.

## Build just that module

From the repository root, after `mvn install` in `multi-scraper-framework`:

```bash
mvn -pl scrapers/<site>-<cc>-scraper -am test
```

Run the jar from this module's directory. The database, logs, browser profile, and caches are written inside the module. The CSV spreadsheet goes in [outputs/](../outputs/README.md). Its file name is `realestate.csv-sink.file-name`, and `listings.csv` when that property is omitted.
