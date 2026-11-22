# config/ — private settings

Git ignores everything here except this README and the `*.example.*` files.

| File | What it is |
| --- | --- |
| `proxies.txt` | Your proxy list. Copy `proxies.example.txt` and edit it. A scraper reads it unless its `application.yaml` names another file. |

The list format is the framework's. The reference is [config/README.md](https://github.com/andre-mei/multi-scraper-framework/blob/main/config/README.md) in [multi-scraper-framework](https://github.com/andre-mei/multi-scraper-framework), the engine an AI agent used to generate this real-estate scraper project. The AI prompt that adds a property portal is [docs/ai-scraper-recon-prompt.md](../docs/ai-scraper-recon-prompt.md).

The framework finds this folder by walking up from the directory a scraper is started in. To use a folder somewhere else, set `STW_CONFIG_DIR`. That is the name the framework reads (`UserConfigFiles.DIR_ENV`).

A Webshare token is `$webshare_api_token` in `proxies.txt` (this file is not committed), or the environment variable `WEBSHARE_API_TOKEN`. It is not written into `application.yaml`.
