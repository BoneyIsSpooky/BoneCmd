# BoneCmd
Command framework for Javacord 3

## Example

```java
import de.boney.bonecmd.Command;
import de.boney.bonecmd.Commands;
```
```java
Commands.registerListener(api);
Commands.registerCommand(new Command("echo")
                             .arg(Command.ArgType.STRING, "str")
                             .runs(args -> args.reply("Hello, you said " + args.getString("str").get()))
                        );
```

## Docs

soon

## How to get it
You can use a dependency manager or build it yourself.

#### Gradle
```groovy
repositories { maven { url 'https://jitpack.io' } }
dependencies { implementation 'com.github.BoneyIsSpooky:BoneCmd:-SNAPSHOT' }
```

#### Maven
```xml
<repositories>
    <repository>
        <id>Jitpack</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.BoneyIsSpooky</groupId>
        <artifactId>BoneCmd</artifactId>
        <version>-SNAPSHOT</version>
        <type>pom</type>
    </dependency>
</dependencies>
```

#### Build it yourself
Clone the repository and type `make jar`, the jar will be in `./build/libs/`
