# No-Namespace-Providers JetBrains Rider Plugin

<img src="src/main/resources/META-INF/pluginIcon.svg" alt="drawing" width="40"/>

Automatically mark all directories in your projects as not a namespace providers.

![preview](https://i.imgur.com/ojNEJ0f.png)

## Build

```bash
gradle build
```

Plugin artifact will be written to

`build/libs/no-namespace-providers-rider-plugin-<version>.jar`

## Publish

```bash
export ORG_GRADLE_PROJECT_intellijPublishToken="..."
gradle publishPlugin
```

## Debug

Open in IntelliJ to simply run and debug in a real Rider instance.


## Manual Installation

Download latest release jar and load it to your Rider: https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk

## Design

The code is a bit hacky. I could not find a proper plugin integration point to implement this behavior.

However, it won't affect performance and should supply good results as long as FS changes are done from within Rider.
