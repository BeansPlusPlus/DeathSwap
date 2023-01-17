# DeathSwap

Before starting, create a [personal github access token](https://github.com/settings/tokens) and make sure it as `read:packages` permissions. 

With this information, set the following environment variables:

```
GITHUB_ACTOR = <Your Github username>
GITHUB_TOKEN = <Your generation personal access token>
```

Define what settings can be set in the `config.yml` by the `/config` in-game command.

Load the `config.yml` to the config plugin (Should be done on the plugin `onEnable`):
`ConfigLoader.loadFromInput(getResource("config.yml"));`

Get a value in the current game configuration:
`GameConfiguration.getConfig().getValue("some_key");`

The `main` attribute in `plugin.yml` must point to a `JavaPlugin` class.

A jar can be created by running `./gradlew jar`

The github secret `StorageSAS` must be set in order to push the jar plugin on push to main branch.