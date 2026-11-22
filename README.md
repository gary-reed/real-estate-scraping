# real-estate-scraping

AI scrapers for property listings, on [multi-scraper-framework](https://github.com/andre-mei/multi-scraper-framework).

An AI prompt generates a real-estate scraper for any property website. This repository was itself produced by an AI agent from that framework's industry prompt, [docs/prompts/example-prompt-scraping.md](https://github.com/andre-mei/multi-scraper-framework/blob/main/docs/prompts/example-prompt-scraping.md), filled in for real estate. The framework stays the engine: phases, HTTP, Chrome, the Manager, and the Browser Delegator. This repository is the listing model (`RealEstateListing`), the `real-estate` dialect, a site template, and the AI prompt you give an AI agent to add a portal.

The framework's own guide is [docs/GUIDE.md](https://github.com/andre-mei/multi-scraper-framework/blob/main/docs/GUIDE.md). Read it for proxies, `scraper.mode`, the Manager, and how a run is started. What the dialect adds is [docs/dialect.md](docs/dialect.md).

It adds the listing model, the dialect, a site template, and the AI prompt that generates a scraper for any property portal. Includes an example scraper created for the Compass site.

## Generate a property scraper with the AI prompt

This is the way to add a portal. Paste the AI prompt into an AI agent (Cursor, or another coding agent). Copying the template by hand is the fallback; the AI prompt is the path that fills it.

1. Clone the framework and this repository side by side, and keep these folder names. The AI prompt refers to them.

   ```bash
   git clone https://github.com/andre-mei/multi-scraper-framework.git
   git clone <this repository> real-estate-scraping
   ```

2. Install the framework once (Java 17, Maven 3.9 or newer). `<relativePath/>` in the pom means Maven reads that install from your local repository.

   ```bash
   cd multi-scraper-framework
   mvn install -DskipTests
   ```

3. Open the AI prompt, [docs/ai-scraper-recon-prompt.md](docs/ai-scraper-recon-prompt.md). Replace `{{HOMEPAGE_URL}}` with the property site's homepage.
4. Give everything **below the line** in that AI prompt to an AI agent that can read `multi-scraper-framework` and can read and write `real-estate-scraping`. Give the AI agent a browser. DevTools is enough.
5. The AI agent writes `scrapers/<site>-<cc>-scraper` and one `<module>` line in the root `pom.xml`. `<site>` is the brand and `<cc>` is the market country (`compass-us`). It compiles that module and runs its unit tests. It does not scrape the live site, and it does not edit the framework, the dialect, or the template.
6. You run the live scrape. From this repository, after the module is registered:

   ```bash
   cp config/proxies.example.txt config/proxies.txt   # then edit it, or set scraper.proxy.enabled: false
   mvn -pl scrapers/<site>-<cc>-scraper -am package -DskipTests
   ```

   Start the Manager from the framework first ([GUIDE.md](https://github.com/andre-mei/multi-scraper-framework/blob/main/docs/GUIDE.md)), then start the scraper from its own directory:

   ```bash
   cd scrapers/<site>-<cc>-scraper
   java -jar target/<site>-<cc>-scraper-1.0.0-SNAPSHOT.jar
   ```

The same AI prompt is how every later real-estate scraper is added. One country site is one module. An AI agent can generate a scraper for any property portal you name.

## Layout

```
real-estate-scraping/
├── pom.xml                              parent: multi-scraper-framework-parent
├── real-estate-model/                   RealEstateListing and its blocks
├── dialect-real-estate/                 parser, resolvers, anomaly checks, warehouse, CSV sink
├── templates/
│   └── template-real-estate-scraper/    starting point the AI prompt clones for one property site
├── scrapers/                            AI-generated site scrapers. The public tree tracks only scrapers/README.md.
├── outputs/                             CSV files a run writes. Git tracks only outputs/README.md.
├── config/                              proxies.example.txt. Your proxies.txt is git-ignored.
└── docs/
    ├── dialect.md                       what this layer adds
    └── ai-scraper-recon-prompt.md       AI prompt that fills the template for one property site
```

`scrapers/*` is git-ignored except [scrapers/README.md](scrapers/README.md). An AI-generated module stays in your clone until you choose to publish it. The root `pom.xml` in a working copy that already contains the Compass example lists `scrapers/compass-us-scraper`. On a clone where that folder is absent, remove that `<module>` line, or `mvn test` will look for a directory that is not there. After the AI prompt generates a scraper, its `<module>` line is the one the AI agent adds.

## What a scraper is

Parent `com.scraper:scraper-parent:2.0.0-SNAPSHOT`, `@SpringBootApplication @EnableCmsFlavor`, dependencies `flavor-cms` and `dialect-real-estate`, and a Phase 3 that `extends RealEstateParser`. `application.yaml` sets `scraper.flavor: cms-listings` and `scraper.dialect: real-estate`. The dialect jar registers itself. The yaml key checks that it did.

`scraper.mode` chooses how pages are loaded: `HTTP`, `BROWSER` (Chrome in this process), or `DELEGATED` (a Browser Delegator). HTTP deletes the two browser classes. A browser mode fills the browser behavior class and the site profile with the same pop-ups, cookie button, block-page text, and delisted rules. The profile's required field is `hostMatch`.

## Build

```bash
cd real-estate-scraping
mvn test
```

`mvn test` builds the model, the dialect, and the template, plus any scraper module the root pom lists and the clone actually contains.

The framework finds `config/` by walking up from the working directory. `STW_CONFIG_DIR` is the environment variable the framework reads when that folder should be somewhere else. A Webshare token is `$webshare_api_token` in `config/proxies.txt` (not committed), or `WEBSHARE_API_TOKEN`. It is not written into `application.yaml`.

## CSV result

A completed run can push the warehouse to the dialect's CSV sink (`realestate.csv-sink`). The scraper posts to it on localhost. The file is:

```
outputs/<country>/<site>/<yyyy-MM-dd>/<file-name>
```

`<file-name>` is `realestate.csv-sink.file-name`. When the property is omitted it is `listings.csv`, so the Compass example writes `outputs/us/compass/2026-11-16/listings.csv`. The value is a file name, not a path. The country, the site, and the date stay in the folders above it. Set it in the scraper's `application.yaml`:

```yaml
realestate:
  csv-sink:
    enabled: true
    port: 8893
    token: local-csv
    directory: ../../outputs
    file-name: listings.csv
    state-directory: ./data/csv-sink
```

`scraper.sink.auto-push` turns the push on after a completed run. `scraper.sink-consumers.central-db.base-url` and `token` must match the sink. Start the jar from the scraper module so `../../outputs` is this repository's `outputs/` folder. Another run the same day replaces that file. Details are in [docs/dialect.md](docs/dialect.md) and [outputs/README.md](outputs/README.md).

## Worked example

`scrapers/compass-us-scraper`, when that folder is in your clone, is an AI-generated scraper for compass.com (United States), produced with the AI prompt [docs/ai-scraper-recon-prompt.md](docs/ai-scraper-recon-prompt.md). Its README and `RECON.md` are the record of that AI run. Use it as a finished module to read. The next property portal still comes from the same AI prompt, with that site's own pages, not by copying Compass selectors.

## Responsible use

The framework ships no target site, no proxies, and no listing data. This repository ships the model, the dialect, the template, and the AI prompt. What you scrape, and how, is your responsibility: respect each site's terms and the law where you operate.

This repository does not add a `LICENSE` file. The engine it depends on is [multi-scraper-framework](https://github.com/andre-mei/multi-scraper-framework); check that repository for the terms that apply to the framework, and keep the attribution when you share an AI-generated scraper built from this AI prompt.
